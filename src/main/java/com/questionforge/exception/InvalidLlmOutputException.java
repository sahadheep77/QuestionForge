package com.questionforge.exception;

public class InvalidLlmOutputException extends RuntimeException {
    public InvalidLlmOutputException(String message) {
        super(message);
    }

    public InvalidLlmOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
