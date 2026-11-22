package com.questionforge.dto.response;

import java.util.List;

public record GeneratedQuestionSet(
    List<Question> questions,
    List<AnswerKey> answerKeys,
    String promptVersion,
    List<String> warnings
) {}
