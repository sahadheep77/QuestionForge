package com.questionforge.client;

import com.questionforge.exception.InvalidLlmOutputException;
import com.questionforge.exception.LlmTimeoutException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Fake implementation of LlmClient for deterministic, fast unit/integration testing.
 * Never makes external network calls.
 */
public class FakeLlmClient implements LlmClient {

    private final Queue<Object> programmedResponses = new ArrayDeque<>();
    private final List<RecordedCall> callHistory = new ArrayList<>();

    public record RecordedCall(String systemPrompt, String userPrompt) {}

    public void enqueueResponse(String jsonResponse) {
        programmedResponses.add(jsonResponse);
    }

    public void enqueueTimeout(String message) {
        programmedResponses.add(new LlmTimeoutException(message));
    }

    public void enqueueError(String message) {
        programmedResponses.add(new InvalidLlmOutputException(message));
    }

    public List<RecordedCall> getCallHistory() {
        return callHistory;
    }

    public void reset() {
        programmedResponses.clear();
        callHistory.clear();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        callHistory.add(new RecordedCall(systemPrompt, userPrompt));

        if (programmedResponses.isEmpty()) {
            throw new InvalidLlmOutputException("FakeLlmClient has no more programmed responses");
        }

        Object next = programmedResponses.poll();
        if (next instanceof RuntimeException re) {
            throw re;
        } else if (next instanceof String s) {
            return s;
        }
        throw new IllegalStateException("Unexpected response type in FakeLlmClient: " + next);
    }
}
