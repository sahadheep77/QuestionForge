# QuestionForge

QuestionForge is the automated interview question generator and answer key creator for code comprehension assessment. It consumes a structured ContextBundle representing a Java method and its surrounding call graph, prompts an LLM through an OpenAI-compatible REST API, and deterministically validates that all generated questions and answer keys are strictly grounded in real, visible code.

---

## The Problem Solved

When evaluating whether an engineer or student genuinely understands code they wrote versus pasting snippets from AI or forums, generic questions like "Explain dependency injection" or "What is Spring Boot?" fail. Anyone can memorize those definitions.

QuestionForge inspects the actual code, callers, callees, and class context, generating deep technical interview questions that require understanding the execution path and design trade-offs of that exact codebase.

---

## How It Works

1. Ingestion: Part 2 (ContextLens) supplies a ContextBundle containing the target method, enclosing class, caller nodes, callee nodes, and size-pruning metadata.
2. Hardened Prompt Construction: PromptBuilder wraps the code inside XML boundaries (UNTRUSTED_STUDENT_CODE) and instructs the model to treat the content strictly as passive data, neutralizing prompt injection attempts.
3. OpenAI-Compatible LLM Invocation: Calls an OpenAI-compatible /v1/chat/completions endpoint using Spring 6 RestClient. Works with OpenAI, Groq, or local inference engines like Ollama and vLLM with zero vendor-specific SDKs.
4. Deterministic Grounding Enforcement: QuestionValidator parses the response and checks it against the actual bundle. If a question targets an omitted method, a signature-only interface, or dropped items, it is rejected as a hallucination.
5. Single Corrective Retry: If the LLM produces invalid JSON or a grounding violation, QuestionGeneratorService feeds the exact validator error back to the LLM for one corrective retry before failing with HTTP 502 Bad Gateway.
6. Separation of Concerns: The service generates candidate-facing Questions and private evaluator-facing AnswerKeys. The REST endpoint excludes answer keys by default to prevent data leakage.

---

## Question Types

Every generated question maps to one of five technical dimensions:

1. DESIGN_RATIONALE: Why a specific implementation structure or design was selected.
2. CHANGE_IMPACT: What breaks or alters behavior if a connected dependency is modified.
3. FLOW: How data and execution flow across callers, the target method, and downstream callees.
4. EDGE_CASE: How the code behaves under null references, runtime failures, and boundary conditions.
5. TRADE_OFF: What alternative architectural choices were possible and why this path was preferred.

---

## Technical Stack

- Java 21 LTS: Records, pattern matching, immutable collections.
- Spring Boot 3.3.4: WebMvc, Jackson Databind, Jakarta Validation.
- HTTP Client: Spring 6 RestClient with configurable connect and read timeouts.
- Testing: JUnit 5, Spring Boot Test, and FakeLlmClient (100 percent offline, zero external LLM calls).
- Build: Apache Maven.

---

## API Reference

### POST /questions

Generates grounded interview questions for a target method.

Request Body:
{
  "targetId": "com.example.service.OrderService#processOrder(Long)",
  "depth": 1,
  "count": 3,
  "difficulty": "MEDIUM"
}

Response Body (Default Mode - Candidate Safe):
{
  "questions": [
    {
      "id": "q1",
      "type": "DESIGN_RATIONALE",
      "difficulty": "MEDIUM",
      "text": "Why does processOrder execute paymentService.charge before updating the order status to PROCESSED?",
      "targetMethodId": "com.example.service.OrderService#processOrder(Long)"
    }
  ],
  "promptVersion": "v1.0.0",
  "warnings": []
}

Answer keys containing grading criteria, required symbols, and rationale are kept server-side and omitted from candidate responses.

---

## Error Handling

- 400 Bad Request: Invalid input parameters (depth, count, or difficulty).
- 404 Not Found: Target method does not exist in the repository.
- 502 Bad Gateway: Upstream LLM failed schema validation or grounding checks after retry.
- 503 Service Unavailable: Upstream LLM request timed out or connection failed.

---

## Verification

The entire test suite runs offline using FakeLlmClient to simulate timeouts, malformed JSON, retries, and grounding violations deterministically:

mvn test
