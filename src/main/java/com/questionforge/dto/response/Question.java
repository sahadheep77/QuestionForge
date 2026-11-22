package com.questionforge.dto.response;

import com.questionforge.dto.enums.Difficulty;
import com.questionforge.dto.enums.QuestionType;

/**
 * Candidate-facing question.
 */
public record Question(
    String id,
    QuestionType type,
    Difficulty difficulty,
    String text,
    String targetMethodId
) {}
