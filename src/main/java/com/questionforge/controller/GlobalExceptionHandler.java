package com.questionforge.controller;

import com.questionforge.exception.InvalidLlmOutputException;
import com.questionforge.exception.LlmTimeoutException;
import com.questionforge.exception.TargetNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TargetNotFoundException.class)
    public ProblemDetail handleTargetNotFound(TargetNotFoundException ex) {
        log.warn("Target not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Target Method Not Found");
        pd.setType(URI.create("urn:problem-type:target-not-found"));
        return pd;
    }

    @ExceptionHandler(LlmTimeoutException.class)
    public ProblemDetail handleLlmTimeout(LlmTimeoutException ex) {
        log.error("LLM timeout occurred: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setTitle("LLM Service Unavailable / Timeout");
        pd.setType(URI.create("urn:problem-type:llm-timeout"));
        return pd;
    }

    @ExceptionHandler(InvalidLlmOutputException.class)
    public ProblemDetail handleInvalidLlmOutput(InvalidLlmOutputException ex) {
        log.error("Invalid LLM output: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("Invalid LLM Response");
        pd.setType(URI.create("urn:problem-type:bad-gateway-llm"));
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        pd.setTitle("Bad Request Parameters");
        pd.setType(URI.create("urn:problem-type:bad-request"));
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        pd.setType(URI.create("urn:problem-type:bad-request"));
        return pd;
    }
}
