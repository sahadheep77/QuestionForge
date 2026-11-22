package com.questionforge.client;

/**
 * Interface abstraction for LLM interactions.
 * Supports OpenAI-compatible REST API endpoints and fake client for tests.
 */
public interface LlmClient {
    /**
     * Sends prompt messages to the LLM and returns raw response string (expected JSON).
     *
     * @param systemPrompt the system instruction prompt
     * @param userPrompt the user prompt containing the ContextBundle
     * @return raw response string from LLM assistant message
     */
    String complete(String systemPrompt, String userPrompt);
}
