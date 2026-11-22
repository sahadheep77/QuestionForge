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

/**
 * Core service orchestrating context building, prompt assembly, LLM invocation,
 * schema/grounding validation, and single-retry recovery on malformed output or grounding violations.
 */
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

    /**
     * Generates an interview question set given a target method and configuration.
     * Retries once on invalid LLM output (malformed JSON or grounding violation), then fails with 502.
     */
    public GeneratedQuestionSet generate(String targetId, int depth, int count, Difficulty difficulty) {
        // 1. Fetch context from Part 2 ContextBuilder
        ContextBundle bundle = contextBuilder.buildContext(targetId, depth);
        if (bundle == null || bundle.targetMethod() == null) {
            throw new TargetNotFoundException("Target method not found in repository: " + targetId);
        }

        // 2. Build initial prompts
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(bundle, count, difficulty);

        // 3. First LLM invocation
        log.info("Calling LLM for targetId='{}' (count={}, difficulty={})", targetId, count, difficulty);
        String rawResponse = llmClient.complete(systemPrompt, userPrompt);

        // 4. Validate output (schema, count, difficulty, grounding)
        QuestionValidator.ValidationResult validationResult = questionValidator.validate(
            rawResponse, bundle, count, difficulty, promptBuilder.getPromptVersion()
        );

        if (validationResult.valid()) {
            return validationResult.questionSet();
        }

        // 5. Retry once if validation failed
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

        // Second failure results in HTTP 502 (Bad Gateway / Invalid upstream output)
        log.error("Retry attempt failed validation: {}", retryValidationResult.errorMessage());
        throw new InvalidLlmOutputException("LLM failed to produce valid output after retry: " + retryValidationResult.errorMessage());
    }
}
