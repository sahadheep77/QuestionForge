package com.questionforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.questionforge.context.ContextBundle;
import com.questionforge.context.DroppedItem;
import com.questionforge.context.MethodNode;
import com.questionforge.dto.enums.Difficulty;
import com.questionforge.dto.enums.QuestionType;
import com.questionforge.dto.response.AnswerKey;
import com.questionforge.dto.response.GeneratedQuestionSet;
import com.questionforge.dto.response.Question;
import org.springframework.stereotype.Component;

import java.util.*;


@Component
public class QuestionValidator {

    private final ObjectMapper objectMapper;

    public QuestionValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ValidationResult(
        boolean valid,
        String errorMessage,
        GeneratedQuestionSet questionSet
    ) {
        public static ValidationResult success(GeneratedQuestionSet set) {
            return new ValidationResult(true, null, set);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message, null);
        }
    }

    
    public ValidationResult validate(
        String rawLlmJson,
        ContextBundle bundle,
        int expectedCount,
        Difficulty expectedDifficulty,
        String promptVersion
    ) {
        if (rawLlmJson == null || rawLlmJson.isBlank()) {
            return ValidationResult.failure("LLM returned empty or null content");
        }

        String cleanJson = rawLlmJson.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        cleanJson = cleanJson.trim();

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(cleanJson);
        } catch (Exception e) {
            return ValidationResult.failure("Malformed JSON: " + e.getMessage());
        }

        if (!rootNode.hasNonNull("questions") || !rootNode.get("questions").isArray()) {
            return ValidationResult.failure("Missing or non-array 'questions' field");
        }
        if (!rootNode.hasNonNull("answerKeys") || !rootNode.get("answerKeys").isArray()) {
            return ValidationResult.failure("Missing or non-array 'answerKeys' field");
        }

        JsonNode questionsNode = rootNode.get("questions");
        JsonNode answerKeysNode = rootNode.get("answerKeys");

        if (questionsNode.size() != expectedCount) {
            return ValidationResult.failure("Expected " + expectedCount + " questions but received " + questionsNode.size());
        }
        if (answerKeysNode.size() != expectedCount) {
            return ValidationResult.failure("Expected " + expectedCount + " answerKeys matching questions but received " + answerKeysNode.size());
        }

        List<Question> questions = new ArrayList<>();
        Map<String, Question> questionMap = new HashMap<>();
        for (int i = 0; i < questionsNode.size(); i++) {
            JsonNode qNode = questionsNode.get(i);
            String id = qNode.path("id").asText(null);
            String typeStr = qNode.path("type").asText(null);
            String diffStr = qNode.path("difficulty").asText(null);
            String text = qNode.path("text").asText(null);
            String targetId = qNode.path("targetMethodId").asText(null);

            if (id == null || text == null || targetId == null) {
                return ValidationResult.failure("Question at index " + i + " missing required fields (id, text, targetMethodId)");
            }

            QuestionType type;
            try {
                type = QuestionType.valueOf(typeStr);
            } catch (Exception e) {
                return ValidationResult.failure("Invalid QuestionType '" + typeStr + "' at question " + id);
            }

            Difficulty diff;
            try {
                diff = Difficulty.valueOf(diffStr);
            } catch (Exception e) {
                return ValidationResult.failure("Invalid Difficulty '" + diffStr + "' at question " + id);
            }

            Question question = new Question(id, type, diff, text, targetId);
            questions.add(question);
            questionMap.put(id, question);
        }

        List<AnswerKey> answerKeys = new ArrayList<>();
        for (int i = 0; i < answerKeysNode.size(); i++) {
            JsonNode aNode = answerKeysNode.get(i);
            String qId = aNode.path("questionId").asText(null);
            if (qId == null || !questionMap.containsKey(qId)) {
                return ValidationResult.failure("AnswerKey at index " + i + " references unknown or null questionId: " + qId);
            }

            List<String> keyPoints = new ArrayList<>();
            aNode.path("keyPoints").forEach(kp -> keyPoints.add(kp.asText()));

            List<String> requiredSymbols = new ArrayList<>();
            aNode.path("requiredSymbols").forEach(rs -> requiredSymbols.add(rs.asText()));

            String rationale = aNode.path("rationale").asText("");

            answerKeys.add(new AnswerKey(qId, keyPoints, requiredSymbols, rationale));
        }

        String groundingError = checkGrounding(bundle, questions, answerKeys);
        if (groundingError != null) {
            return ValidationResult.failure(groundingError);
        }

        List<String> warnings = new ArrayList<>();
        if (rootNode.has("warnings") && rootNode.get("warnings").isArray()) {
            rootNode.get("warnings").forEach(w -> warnings.add(w.asText()));
        }

        return ValidationResult.success(new GeneratedQuestionSet(questions, answerKeys, promptVersion, warnings));
    }

    
    public String checkGrounding(ContextBundle bundle, List<Question> questions, List<AnswerKey> answerKeys) {
        Set<String> forbiddenSymbols = new HashSet<>();

        if (bundle.droppedItems() != null) {
            for (DroppedItem dropped : bundle.droppedItems()) {
                if (dropped.methodId() != null) {
                    forbiddenSymbols.add(extractSimpleName(dropped.methodId()));
                    forbiddenSymbols.add(dropped.methodId());
                }
            }
        }

        List<MethodNode> connected = new ArrayList<>();
        if (bundle.callers() != null) connected.addAll(bundle.callers());
        if (bundle.callees() != null) connected.addAll(bundle.callees());

        for (MethodNode node : connected) {
            if (node.signatureOnly() || node.code() == null || node.code().isBlank()) {
                forbiddenSymbols.add(extractSimpleName(node.methodId()));
                forbiddenSymbols.add(node.methodId());
            }
        }

        for (Question q : questions) {
            for (String forbidden : forbiddenSymbols) {
                if (forbidden.length() > 3 && q.text().contains(forbidden)) {
                    return "Grounding violation in question " + q.id() +
                           ": targets symbol without code or dropped from bundle: " + forbidden;
                }
            }
        }

        for (AnswerKey ak : answerKeys) {
            for (String sym : ak.requiredSymbols()) {
                for (String forbidden : forbiddenSymbols) {
                    if (forbidden.equalsIgnoreCase(sym) || sym.contains(forbidden)) {
                        return "Grounding violation in answerKey for " + ak.questionId() +
                               ": requires symbol without code or dropped from bundle: " + sym;
                    }
                }
            }
        }

        return null;
    }

    private String extractSimpleName(String methodId) {
        if (methodId == null) return "";
        int hash = methodId.indexOf('#');
        if (hash != -1) {
            String sub = methodId.substring(hash + 1);
            int paren = sub.indexOf('(');
            return paren != -1 ? sub.substring(0, paren) : sub;
        }
        return methodId;
    }
}
