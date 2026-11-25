package com.questionforge.service;

import com.questionforge.client.LlmClient;
import com.questionforge.context.ContextBuilder;
import com.questionforge.context.ContextBundle;
import com.questionforge.dto.enums.Difficulty;
import com.questionforge.dto.response.GeneratedQuestionSet;
import com.questionforge.exception.InvalidLlmOutputException;
import com.questionforge.exception.TargetNotFoundException;
import com.questionforge.prompt.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class QuestionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(QuestionGeneratorService.class);

    private final ContextBuilder contextBuilder;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final QuestionValidator questionValidator;

    public QuestionGeneratorService(
        ContextBuilder contextBuilder,
        PromptBuilder promptBuilder,
        LlmClient llmClient,
        QuestionValidator questionValidator
    ) {
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.questionValidator = questionValidator;
    }

    
    public GeneratedQuestionSet generate(String targetId, int depth, int count, Difficulty difficulty) {
        ContextBundle bundle = contextBuilder.buildContext(targetId, depth);
        if (bundle == null || bundle.targetMethod() == null) {
            throw new TargetNotFoundException("Target method not found in repository: " + targetId);
        }

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(bundle, count, difficulty);

        log.info("Calling LLM for targetId='{}' (count={}, difficulty={})", targetId, count, difficulty);
        String rawResponse = llmClient.complete(systemPrompt, userPrompt);

        QuestionValidator.ValidationResult validationResult = questionValidator.validate(
            rawResponse, bundle, count, difficulty, promptBuilder.getPromptVersion()
        );

        if (validationResult.valid()) {
            return validationResult.questionSet();
        }

        log.warn("First LLM attempt failed validation: {}. Retrying once...", validationResult.errorMessage());
        String retryPrompt = promptBuilder.buildRetryPrompt(rawResponse, validationResult.errorMessage());

        String retryRawResponse = llmClient.complete(systemPrompt, retryPrompt);
        QuestionValidator.ValidationResult retryValidationResult = questionValidator.validate(
            retryRawResponse, bundle, count, difficulty, promptBuilder.getPromptVersion()
        );

        if (retryValidationResult.valid()) {
            log.info("Retry succeeded for targetId='{}'", targetId);
            return retryValidationResult.questionSet();
        }

        log.error("Retry attempt failed validation: {}", retryValidationResult.errorMessage());
        throw new InvalidLlmOutputException("LLM failed to produce valid output after retry: " + retryValidationResult.errorMessage());
    }
}
