package com.questionforge.dto.response;

import java.util.List;

/**
 * Evaluator-facing answer key.
 */
public record AnswerKey(
    String questionId,
    List<String> keyPoints,
    List<String> requiredSymbols,
    String rationale
) {}
