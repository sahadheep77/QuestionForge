package com.questionforge.client;

import com.questionforge.config.LlmProperties;
import com.questionforge.dto.llm.ChatCompletionRequest;
import com.questionforge.dto.llm.ChatCompletionResponse;
import com.questionforge.exception.InvalidLlmOutputException;
import com.questionforge.exception.LlmTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

/**
 * Standard OpenAI-compatible REST API client using Spring 6 RestClient.
 * Compatible with OpenAI, Groq, Ollama, vLLM, and any endpoint exposing /v1/chat/completions.
 */
public class OpenAiCompatibleRestClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleRestClient.class);

    private final RestClient restClient;
    private final LlmProperties properties;

    public OpenAiCompatibleRestClient(LlmProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.timeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));

        RestClient.Builder builder = restClientBuilder
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey().trim());
        }

        this.restClient = builder.build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        List<ChatCompletionRequest.ChatMessage> messages = List.of(
            new ChatCompletionRequest.ChatMessage("system", systemPrompt),
            new ChatCompletionRequest.ChatMessage("user", userPrompt)
        );

        ChatCompletionRequest request = new ChatCompletionRequest(
            properties.model(),
            messages,
            properties.temperature(),
            properties.maxTokens(),
            new ChatCompletionRequest.ResponseFormat("json_object")
        );

        try {
            ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new InvalidLlmOutputException("LLM returned empty choices in completion response");
            }

            ChatCompletionResponse.Choice choice = response.choices().get(0);
            if (choice.message() == null || choice.message().content() == null) {
                throw new InvalidLlmOutputException("LLM returned null message content");
            }

            return choice.message().content();

        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException || e.getMessage().contains("timed out")) {
                throw new LlmTimeoutException("LLM request timed out after " + properties.timeoutMs() + "ms", e);
            }
            throw new LlmTimeoutException("Failed to reach LLM service: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            log.error("LLM upstream error HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new InvalidLlmOutputException("LLM upstream error (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            if (e instanceof LlmTimeoutException || e instanceof InvalidLlmOutputException) {
                throw e;
            }
            throw new InvalidLlmOutputException("Unexpected error during LLM invocation: " + e.getMessage(), e);
        }
    }
}
