# QuestionForge (Part 3)

QuestionForge is the automated interview question generator component of the code comprehension assessment platform. It takes a `ContextBundle` produced by Part 2 (`ContextBuilder`) for a target Java method and prompts an LLM via an OpenAI-compatible REST API to generate rigorous, grounded technical interview questions and evaluator answer keys.

---

## 1. Architecture & End-to-End Flow

```
                      +-------------------+
                      |   ContextBundle   |  (From Part 2 ContextBuilder)
                      +---------+---------+
                                |
                                v
                      +-------------------+
                      |   PromptBuilder   |  - Encloses untrusted code in data tags
                      |   (Template v1)   |  - Injects targetId, count, difficulty
                      +---------+---------+
                                |
                                v
                      +-------------------+
                      |     LlmClient     |  - OpenAI-compatible REST or Fake client
                      |   (/completions)  |  - Configurable timeout, temperature, model
                      +---------+---------+
                                |
                                v
                      +-------------------+
                      | QuestionValidator |  - Schema & strict JSON check
                      | (Grounding Guard) |  - Grounding violation detection
                      +---------+---------+  - Truncated / signature-only checks
                                |
                   [Invalid]    |    [Valid]
             +------------------+------------------+
             |                                     |
             v                                     v
   +-------------------+                +----------------------+
   | Retry Once via    |                | GeneratedQuestionSet |
   | PromptBuilder     |                +----------+-----------+
   +---------+---------+                           |
             |                                     |
      [Second Fail]                                v
             |                          +----------------------+
             v                          |  QuestionController  |
        HTTP 502                        |   POST /questions    |
      (Bad Gateway)                     +----------+-----------+
                                                   |
                                     (Hides answer keys unless
                                        question-forge.debug=true)
                                                   |
                                                   v
                                        HTTP 200 JSON Response
```

---

## 2. API Reference

### `POST /questions`

#### Request Payload
```json
{
  "targetId": "com.example.service.OrderService#processOrder(Long)",
  "depth": 1,
  "count": 3,
  "difficulty": "MEDIUM"
}
```

#### Response Payload (`debug=false` by default)
```json
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
```
*Note: Evaluator-facing `answerKeys` are strictly hidden from candidate responses unless configured via `question-forge.debug=true`.*

---

## 3. Error Handling & HTTP Status Codes

* **`400 Bad Request`**: Validation errors on request body (invalid difficulty, blank `targetId`, count < 1 or > 10).
* **`404 Not Found`**: Target method not found in codebase (propagated from Part 2 `ContextBuilder`).
* **`502 Bad Gateway`**: LLM returned unparseable JSON or grounding violations twice (after single corrective retry).
* **`503 Service Unavailable`**: LLM request timed out or network connection failed.

---

## 4. Running Tests

Tests run using `FakeLlmClient` and never invoke external LLM providers:
```bash
mvn test
```
