package com.questionforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questionforge.client.FakeLlmClient;
import com.questionforge.client.LlmClient;
import com.questionforge.context.ContextBundle;
import com.questionforge.context.MockContextBuilder;
import com.questionforge.dto.enums.Difficulty;
import com.questionforge.dto.enums.QuestionType;
import com.questionforge.dto.request.GenerateQuestionsRequest;
import com.questionforge.dto.response.GenerateQuestionsResponse;
import com.questionforge.prompt.PromptBuilder;
import com.questionforge.service.QuestionGeneratorService;
import com.questionforge.service.QuestionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = QuestionForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class QuestionForgeTests {

    @TestConfiguration
    static class TestLlmConfig {
        @Bean
        @Primary
        public LlmClient fakeLlmClient() {
            return new FakeLlmClient();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockContextBuilder contextBuilder;

    @Autowired
    private PromptBuilder promptBuilder;

    @Autowired
    private QuestionValidator validator;

    @Autowired
    private QuestionGeneratorService generatorService;

    @Autowired
    private TestRestTemplate restTemplate;

    private FakeLlmClient fakeLlmClient;
    private String validLlmJson;
    private String groundingViolationJson;
    private final String targetId = "com.example.service.OrderService#processOrder(Long)";

    @BeforeEach
    void setUp() throws Exception {
        fakeLlmClient = (FakeLlmClient) llmClient;
        fakeLlmClient.reset();

        try (InputStream is = new ClassPathResource("fixtures/valid-llm-response.json").getInputStream()) {
            validLlmJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream is = new ClassPathResource("fixtures/grounding-violation-response.json").getInputStream()) {
            groundingViolationJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("1. Valid response parsed correctly into Questions and AnswerKeys")
    void testValidResponseParsedCorrectly() {
        ContextBundle bundle = contextBuilder.buildContext(targetId, 1);
        QuestionValidator.ValidationResult result = validator.validate(
            validLlmJson, bundle, 3, Difficulty.MEDIUM, "v1.0.0"
        );

        assertTrue(result.valid());
        assertNotNull(result.questionSet());
        assertEquals(3, result.questionSet().questions().size());
        assertEquals(3, result.questionSet().answerKeys().size());
        assertEquals("q1", result.questionSet().questions().get(0).id());
        assertEquals(QuestionType.DESIGN_RATIONALE, result.questionSet().questions().get(0).type());
    }

    @Test
    @DisplayName("2. Malformed JSON on first attempt, then retry succeeds")
    void testMalformedJsonThenRetrySucceeds() {
        fakeLlmClient.enqueueResponse("{\"questions\": [ malformed json ... ");
        fakeLlmClient.enqueueResponse(validLlmJson);

        var result = generatorService.generate(targetId, 1, 3, Difficulty.MEDIUM);

        assertNotNull(result);
        assertEquals(3, result.questions().size());
        assertEquals(2, fakeLlmClient.getCallHistory().size(), "Should have called LLM twice");
    }

    @Test
    @DisplayName("3. Malformed JSON twice results in 502 Bad Gateway")
    void testMalformedJsonTwiceResultsIn502() {
        fakeLlmClient.enqueueResponse("{\"broken\": 1}");
        fakeLlmClient.enqueueResponse("{\"still_broken\": 2}");

        GenerateQuestionsRequest request = new GenerateQuestionsRequest(targetId, 1, 3, Difficulty.MEDIUM);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/questions", request, String.class
        );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }

    @Test
    @DisplayName("4. Question referencing a dropped or missing symbol is rejected (grounding check)")
    void testGroundingViolationRejected() {
        ContextBundle bundle = contextBuilder.buildContext(targetId, 1);
        QuestionValidator.ValidationResult result = validator.validate(
            groundingViolationJson, bundle, 3, Difficulty.MEDIUM, "v1.0.0"
        );

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("Grounding violation"), "Expected grounding violation error message");
    }

    @Test
    @DisplayName("5. Count mismatch (LLM returns too few or too many questions) fails validation")
    void testCountMismatchRejected() {
        ContextBundle bundle = contextBuilder.buildContext(targetId, 1);
        // Valid JSON has 3 questions; expect 2
        QuestionValidator.ValidationResult result = validator.validate(
            validLlmJson, bundle, 2, Difficulty.MEDIUM, "v1.0.0"
        );

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("Expected 2 questions but received 3"));
    }

    @Test
    @DisplayName("6. Code containing injection strings is safely kept inside untrusted data delimiters")
    void testPromptInjectionDefense() {
        ContextBundle bundle = contextBuilder.buildContext(targetId, 1);
        String userPrompt = promptBuilder.buildUserPrompt(bundle, 3, Difficulty.MEDIUM);

        assertTrue(userPrompt.contains("<UNTRUSTED_STUDENT_CODE>"));
        assertTrue(userPrompt.contains("</UNTRUSTED_STUDENT_CODE>"));

        int startIdx = userPrompt.indexOf("<UNTRUSTED_STUDENT_CODE>");
        int endIdx = userPrompt.indexOf("</UNTRUSTED_STUDENT_CODE>");
        int codeIdx = userPrompt.indexOf("orderRepository.findById(orderId)");

        assertTrue(codeIdx > startIdx && codeIdx < endIdx, "Student code must be strictly enclosed within data tags");
    }

    @Test
    @DisplayName("7. Answer keys never appear in endpoint response by default (debug=false)")
    void testAnswerKeysExcludedInEndpointResponse() {
        fakeLlmClient.enqueueResponse(validLlmJson);

        GenerateQuestionsRequest request = new GenerateQuestionsRequest(targetId, 1, 3, Difficulty.MEDIUM);
        ResponseEntity<GenerateQuestionsResponse> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/questions", request, GenerateQuestionsResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().questions().size());
        assertNull(response.getBody().answerKeys(), "Answer keys must be null/omitted in normal responses");
    }

    @Test
    @DisplayName("8. Unknown target method returns 404 Not Found")
    void testUnknownTargetReturns404() {
        GenerateQuestionsRequest request = new GenerateQuestionsRequest("com.unknown.Class#doesNotExist()", 1, 3, Difficulty.MEDIUM);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/questions", request, String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("9. LLM timeout returns 503 Service Unavailable")
    void testLlmTimeoutReturns503() {
        fakeLlmClient.enqueueTimeout("Connection to LLM timed out");

        GenerateQuestionsRequest request = new GenerateQuestionsRequest(targetId, 1, 3, Difficulty.MEDIUM);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/questions", request, String.class
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }
}
