package com.questionforge.controller;

import com.questionforge.dto.request.GenerateQuestionsRequest;
import com.questionforge.dto.response.GenerateQuestionsResponse;
import com.questionforge.dto.response.GeneratedQuestionSet;
import com.questionforge.service.QuestionGeneratorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/questions")
public class QuestionController {

    private final QuestionGeneratorService questionGeneratorService;
    private final boolean debug;

    public QuestionController(
        QuestionGeneratorService questionGeneratorService,
        @Value("${question-forge.debug:false}") boolean debug
    ) {
        this.questionGeneratorService = questionGeneratorService;
        this.debug = debug;
    }

    @PostMapping
    public ResponseEntity<GenerateQuestionsResponse> generateQuestions(
        @Valid @RequestBody GenerateQuestionsRequest request
    ) {
        GeneratedQuestionSet questionSet = questionGeneratorService.generate(
            request.targetId(),
            request.depth(),
            request.count(),
            request.difficulty()
        );

        GenerateQuestionsResponse response = new GenerateQuestionsResponse(
            questionSet.questions(),
            debug ? questionSet.answerKeys() : null,
            questionSet.promptVersion(),
            questionSet.warnings()
        );

        return ResponseEntity.ok(response);
    }
}
