package com.questionforge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record GenerateQuestionsResponse(
    List<Question> questions,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<AnswerKey> answerKeys,
    String promptVersion,
    List<String> warnings
) {}
