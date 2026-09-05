# Spring AI GenAI Platform

A production-oriented GenAI application built with Spring AI.

This repository is being developed progressively through multiple stages, where each stage extends the previous version into a more capable and production-ready AI system.

---

# Current Stage

## Project 1 — AI Copilot

Project 1 establishes the production foundation of the GenAI platform.

### Project 1 Includes

- Spring Boot + Spring AI
- Groq as the AI provider
- `openai/gpt-oss-120b` model
- Chat API
- Conversation memory
- Structured output
- Basic AI guardrails
- Retry handling
- Timeout handling
- Circuit Breaker
- Rate Limiter
- Bulkhead
- Global exception handling
- Request validation
- AI request/error/latency metrics
- Actuator health and metrics
- Structured application logs
- Unit tests
- Integration tests
- Black-box API testing

---

# Architecture

```text
Client
   |
   v
ChatController
   |
   v
ChatService
   |
   v
AiProviderClient
   |
   v
Spring AI ChatClient
   |
   v
Groq
   |
   v
GPT OSS 120B
```

Production concerns:

```text
Client
  |
  v
Validation
  |
  v
Controller
  |
  v
Service
  |
  v
AiProviderClient
  |
  v
Resilience
  |
  +--> Retry
  +--> Circuit Breaker
  +--> Rate Limiter
  +--> Bulkhead
  +--> Timeout
  |
  v
Spring AI
  |
  v
Groq
  |
  v
GPT OSS 120B
```

Cross-cutting concerns:

```text
Exception Handling
Logging
Metrics
Actuator
Testing
Security / Secret Handling
```

---

# Project 1 Learning Journey

> The repository feature list is the source of truth for Project 1 capabilities. The day-wise structure below is the revision/implementation organization used while building those capabilities.

---

# Day 1 — Spring AI Fundamentals & AI Chat Foundation

## Goal

Understand how Spring AI connects a Spring application to an LLM provider and build the basic AI chat flow.

## Concepts

- Generative AI application architecture
- Spring AI fundamentals
- `ChatClient`
- `ChatModel`
- AI provider abstraction
- OpenAI-compatible APIs
- Groq integration
- Chat request/response flow
- REST API integration
- Controller / Service separation

## Mental Model

```text
Client
  |
  v
Controller
  |
  v
Service
  |
  v
ChatClient
  |
  v
ChatModel
  |
  v
Groq
  |
  v
GPT OSS 120B
```

## ChatClient

`ChatClient` is the main application-facing abstraction used to interact with a chat model.

## ChatModel

`ChatModel` represents the model-level abstraction responsible for producing chat responses.

## Why abstraction?

Direct provider coupling makes provider/model changes harder.

```text
Application
    |
    v
Spring AI abstraction
    |
    +---- Groq
    +---- Other provider
```

## Implemented

- Spring Boot
- Spring AI
- Groq provider
- `openai/gpt-oss-120b`
- Chat API
- Controller → Service → AI provider flow

---

# Day 2 — Conversation Memory, Structured Output & Guardrails

## Goal

Move from a basic stateless AI call toward a useful AI assistant.

## Conversation Memory

Separate HTTP requests do not automatically contain previous conversation context.

```text
Current Message
      |
      v
Conversation Memory
      |
      v
Previous Context + Current Message
      |
      v
ChatClient
      |
      v
LLM
```

More history means more context, but also potentially:

- More tokens
- More latency
- More cost

## Structured Output

LLMs normally return generated text. Applications often need predictable structures.

```text
LLM
 |
 v
Structured Output
 |
 v
Validation
 |
 v
Application
```

Structured output creates a stronger application contract.

## Guardrails

LLM output is probabilistic and should not automatically be treated as trusted application data.

```text
LLM Output
    |
    v
Validation / Guardrails
    |
    v
Application
```

## Implemented

- Conversation memory
- Structured output
- Structured output validation
- Basic AI guardrails

---

# Day 3 — Resilience & Failure Handling

## Goal

Treat the AI provider as an unreliable external dependency.

AI providers can experience:

- Network failure
- Timeout
- Temporary errors
- Rate limiting
- Provider outage
- Slow responses
- Overload

## Retry

Retry attempts a failed operation again when the failure may be transient.

```text
Request
   |
   v
Provider
   |
   +---- Failure
   |
   v
Retry
   |
   v
Provider
```

Do not retry blindly for every error.

## Exponential Backoff

```text
Attempt 1 → failure
     |
    wait
     |
Attempt 2 → failure
     |
longer wait
     |
Attempt 3
```

Backoff reduces pressure on an unhealthy provider.

## Timeout

Prevents requests from waiting indefinitely.

```text
Request
   |
   v
Provider
   |
   +---- Response
   |
   +---- Timeout
```

## Circuit Breaker

Protects the application from repeatedly calling an unhealthy dependency.

```text
             failures exceed threshold
CLOSED ------------------------------> OPEN
  ^                                     |
  |                              wait duration
  |                                     |
  |                                     v
  +------------------------------- HALF_OPEN
                                      |
                              +-------+-------+
                              |               |
                           Success          Failure
                              |               |
                              v               v
                           CLOSED            OPEN
```

### CLOSED

Requests normally pass through.

### OPEN

Calls are short-circuited because the dependency is considered unhealthy.

### HALF_OPEN

A limited number of calls test whether the dependency has recovered.

## Rate Limiter

Controls request rate.

Purpose:

- Protect provider
- Control usage
- Control cost
- Prevent bursts
- Protect application

```text
Incoming Requests
       |
       v
Rate Limiter
   |       |
Allowed  Rejected
   |
   v
Provider
```

## Bulkhead

Controls concurrent execution.

```text
Incoming Requests
       |
       v
+----------------------+
|      Bulkhead        |
| Max Concurrent Calls |
+----------------------+
       |
       v
AI Provider
```

## Resilience4j

Used for:

- Retry
- Circuit Breaker
- Rate Limiter
- Bulkhead

## Mental Model

```text
Retry
→ Can we recover from a transient failure?

Timeout
→ How long are we willing to wait?

Circuit Breaker
→ Should we stop calling an unhealthy provider?

Rate Limiter
→ How many requests are allowed?

Bulkhead
→ How many concurrent operations are allowed?
```

## Implemented

- Retry
- Exponential backoff
- Timeout handling
- Circuit Breaker
- Circuit Breaker states
- Rate Limiter
- Bulkhead
- Resilience4j
- Failure-path testing

---

# Day 4 — Production Hardening, Exception Handling, Observability & Testing

## Goal

Turn the AI application from a working demo into a production-oriented service.

---

## Request Validation

Invalid input should be rejected before expensive AI processing.

```text
Client
  |
  v
Validation
  |
  +---- Invalid → 400
  |
  v
ChatService
  |
  v
AI Provider
```

Benefits:

- Predictable API behavior
- No unnecessary AI calls
- Better client feedback
- Lower cost

---

## Global Exception Handling

Application-specific exceptions should be handled centrally.

```text
Application Exception
        |
        v
Global Exception Handler
        |
        v
Safe API Error Response
```

## Spring MVC Exception Handling

Spring MVC already handles many framework-level exceptions.

Examples:

- Invalid request parameters
- Missing request data
- Unsupported HTTP methods
- Unknown resources

A broad:

```java
@ExceptionHandler(Exception.class)
```

can accidentally intercept framework exceptions and convert expected errors into `500 Internal Server Error`.

When custom handling of MVC exceptions is required, `ResponseEntityExceptionHandler` provides a better foundation.

### Key lesson

Do not blindly catch every exception.

---

# Streaming

Streaming allows generated output to reach the client progressively.

```text
Request
  |
  v
LLM
  |
  +--> chunk
  +--> chunk
  +--> chunk
  +--> chunk
  |
  v
Client
```

Streaming improves perceived responsiveness but introduces additional reactive/observability considerations.

---

# Observability

Observability is the ability to understand what is happening inside a running system from the telemetry it produces.

Three primary pillars:

```text
Logs      → What happened?
Metrics   → How often / how much?
Traces    → Where did the request go / where did it spend time?
```

AI applications additionally care about:

```text
Tokens
Cost
Model
Provider
Latency
Time to First Token
Retries
Failures
```

---

# Logging

Logging records individual application events.

Useful events:

```text
AI_REQUEST_STARTED
AI_PROVIDER_CALL_STARTED
AI_REQUEST_COMPLETED
AI_PROVIDER_ERROR
AI_PROVIDER_TIMEOUT
AI_RATE_LIMIT_EXCEEDED
AI_PROVIDER_UNAVAILABLE
AI_PROVIDER_BUSY
```

Exact names are application conventions. The events must be distinguishable.

---

# Safe Logging

Useful metadata:

```text
requestId
conversationId
operation
provider
model
status
latencyMs
errorCode
```

Do NOT blindly log:

```text
API keys
Authorization headers
Passwords
Access tokens
Full prompts
Full responses
Sensitive retrieved documents
Unnecessary PII
```

Why?

AI prompts/responses may contain confidential information, PII, credentials, or proprietary data.

Logging sensitive AI content creates another data-leakage surface.

---

# Logging Levels

## INFO

Normal important lifecycle events.

## WARN

Abnormal but expected/recoverable situations.

Examples:

```text
AI rate limit reached
AI bulkhead rejected request
```

## ERROR

Unexpected or failed operations.

## DEBUG

Detailed diagnostic information.

Important:

> DEBUG does not mean log everything.

Sensitive data must remain protected at every log level.

---

# Correlation ID / Request ID

A correlation ID connects logs and events belonging to the same request.

Without request IDs:

```text
Request started
Request started
Request failed
Request completed
```

With request IDs:

```text
requestId=abc → START
requestId=xyz → START
requestId=abc → FAILED
requestId=xyz → COMPLETED
```

Typical lifecycle:

```text
HTTP Request
     |
     v
Request ID
     |
     v
Controller
     |
     v
Service
     |
     v
AI Provider Client
     |
     v
Logs / Metrics / Trace
```

---

# MDC

MDC = Mapped Diagnostic Context.

It is commonly used to attach contextual values such as `requestId` to log entries.

Conceptually:

```java
MDC.put("requestId", requestId);
```

Cleanup is important because application threads can be reused:

```java
try {
    MDC.put("requestId", requestId);

    // request processing

} finally {
    MDC.remove("requestId");
}
```

## Reactive Caveat

Traditional MDC is thread-local.

Reactive execution can move across threads.

```text
Synchronous
→ MDC is relatively straightforward

Reactive / Flux
→ Context propagation needs additional consideration
```

Do not assume ThreadLocal/MDC behaves identically through a reactive pipeline.

---

# Exception Observability

Conceptual architecture:

```text
Provider Exception
       |
       v
AiExceptionTranslator
       |
       v
AiProviderException
       |
       v
Global Exception Handler
       |
       v
API Response
```

Internal logs can contain useful diagnostic metadata:

```text
requestId=abc
errorCode=AI_PROVIDER_TIMEOUT
provider=groq
model=openai/gpt-oss-120b
exceptionType=...
latencyMs=...
```

The client should receive a safe error such as:

```text
504
AI_PROVIDER_TIMEOUT
```

Do not expose internal stack traces unnecessarily.

---

# Metrics

Logs answer:

> What happened?

Metrics answer:

> How often and how much?

Important AI metrics:

```text
AI requests total
AI successful requests
AI failed requests
AI request latency
AI timeout count
AI retry count
AI rate-limit rejection count
AI circuit-breaker events
AI bulkhead rejection count
```

Later, where provider/version support exists:

```text
input tokens
output tokens
total tokens
estimated cost
time-to-first-token
```

Do not assume every provider/version exposes usage in exactly the same way.

---

# Latency

AI latency can vary significantly.

Example:

```text
Total = 3.2s

Application      30ms
RAG             400ms
Database         50ms
Provider       2700ms
```

Without measurement:

```text
"AI is slow."
```

With measurement:

```text
"Most latency is coming from the provider."
```

Measure important stages instead of guessing.

---

# Provider & Model Metadata

Useful metadata:

```text
provider=groq
model=openai/gpt-oss-120b
operation=chat
```

This becomes important when multiple providers/models are introduced.

It allows comparison of:

- Latency
- Failure rate
- Usage
- Cost
- Model behavior

without logging user content.

---

# Token Usage

Request count alone is not enough.

```text
Request A → 500 tokens
Request B → 20,000 tokens
```

Both are one request but have very different resource usage and potentially different cost.

Where available:

```text
inputTokens
outputTokens
totalTokens
```

Provider/version support must be verified.

---

# Cost

Conceptually:

```text
Token usage
     |
     v
Provider pricing
     |
     v
Estimated cost
```

Useful for:

- Budget monitoring
- Model selection
- Tenant usage
- Optimization
- Anomaly detection

---

# Tracing

Tracing follows a request across multiple components.

```text
API
 ↓
Service
 ↓
RAG
 ↓
Vector DB
 ↓
Tool
 ↓
LLM Provider
```

Tracing answers:

> Where did this request spend its time?

For P1, understand the concept. A large distributed tracing stack is not required unless justified.

---

# Actuator

Spring Boot Actuator provides operational information.

Exposed areas include:

```text
Health
Info
Metrics
```

---

# Testing Strategy

## Unit Tests

Test individual components in isolation.

Examples:

- Service logic
- Validation logic
- Exception translation

## Integration Tests

Test multiple components together.

Examples:

- Controller
- Service
- Spring context
- AI integration boundaries

## Black-Box API Tests

Test the actual running HTTP API.

This verifies externally visible behavior.

---

# Black-Box Test Cases

```text
Valid request
Invalid request
Blank request
Missing request
Null request
Malformed JSON
Unsupported HTTP method
Unknown endpoint
Streaming
Provider failure
Circuit Breaker behavior
Rate Limiter behavior
Bulkhead behavior
```

---

# Production Observability Rules

## Log

```text
requestId
conversationId
operation
provider
model
status
latency
errorCode
```

## Avoid logging

```text
API keys
Authorization headers
Passwords
Access tokens
Full prompts
Full responses
Sensitive retrieved documents
Unnecessary PII
```

## Core principle

> Production observability should explain what happened, where it happened, how often it happens, and how much it costs — without creating a new security or privacy problem.

---

# Day 5 — P1 Integration & Production Readiness

## Goal

Bring all Project 1 capabilities together into one coherent AI Copilot.

The features are not isolated. They work together as one production-oriented system.

---

# Final Request Flow

```text
                         Client
                           |
                           v
                    ChatController
                           |
                           v
                  Request Validation
                           |
                           v
                      ChatService
                           |
                           v
                  AiProviderClient
                           |
                           v
                 Resilience Layer
                           |
          ┌────────────────┼────────────────┐
          |                |                |
        Retry       Circuit Breaker   Rate Limiter
          |                |                |
          └────────────────┼────────────────┘
                           |
                       Bulkhead
                           |
                       Timeout
                           |
                           v
                  Spring AI ChatClient
                           |
                           v
                         Groq
                           |
                           v
                    GPT OSS 120B
                           |
                           v
                       Response
```

Cross-cutting:

```text
Exception Handling
Logging
Metrics
Actuator
Testing
Security / Secret Handling
```

---

# P1 Engineering Mental Model

The system evolved from:

```text
Request → LLM → Response
```

into:

```text
Request
   ↓
Validation
   ↓
Controller
   ↓
Service
   ↓
Provider Client
   ↓
Resilience
   ↓
Spring AI
   ↓
LLM Provider
   ↓
Response
```

surrounded by:

```text
Exception Handling
Logging
Metrics
Actuator
Testing
```

---

# P1 Completion Checklist

## AI Foundation

- [x] Spring Boot + Spring AI
- [x] Groq integration
- [x] `openai/gpt-oss-120b`
- [x] Chat API
- [x] `ChatClient`
- [x] `ChatModel`
- [x] AI provider abstraction

## AI Capabilities

- [x] Conversation memory
- [x] Structured output
- [x] Structured output validation
- [x] Basic AI guardrails

## Resilience

- [x] Retry
- [x] Exponential backoff
- [x] Timeout handling
- [x] Circuit Breaker
- [x] Circuit Breaker states
- [x] Rate Limiter
- [x] Bulkhead
- [x] Resilience4j

## API Reliability

- [x] Request validation
- [x] Global exception handling
- [x] Standard HTTP error handling
- [x] Malformed JSON handling
- [x] Unsupported HTTP method handling
- [x] Unknown endpoint handling
- [x] Streaming testing

## Observability

- [x] AI request metrics
- [x] AI error metrics
- [x] AI latency metrics
- [x] Actuator health
- [x] Actuator metrics
- [x] Structured application logs
- [x] Observability concepts
- [x] Correlation ID / request ID concepts
- [x] MDC concepts
- [x] Safe logging principles
- [x] AI-specific telemetry concepts

## Testing

- [x] Unit tests
- [x] Integration tests
- [x] Black-box API testing
- [x] Validation testing
- [x] Error-path testing
- [x] Resilience testing

---

# Key Engineering Lessons

## 1. LLM is an external dependency

An AI provider can:

- Fail
- Timeout
- Become unavailable
- Rate-limit requests
- Become slow
- Return unexpected output

Therefore resilience is part of AI architecture.

## 2. AI output is not automatically trustworthy

LLM output is probabilistic.

Use deterministic application boundaries:

```text
LLM Output
    |
    v
Validation / Guardrails
    |
    v
Application
```

## 3. Validate early

```text
Invalid Request
      |
      X
AI Provider
```

Reject invalid requests before expensive AI processing.

## 4. Resilience mechanisms solve different problems

```text
Retry
→ transient failure recovery

Timeout
→ maximum waiting time

Circuit Breaker
→ stop calls to unhealthy dependency

Rate Limiter
→ control request rate

Bulkhead
→ control concurrent resource usage
```

## 5. Observability is part of production architecture

Important signals:

```text
Logs
Metrics
Traces
Provider
Model
Latency
Tokens
Cost
Failures
Retries
```

## 6. Never log sensitive AI data blindly

Prompts and responses may contain:

- PII
- Credentials
- Confidential information
- Proprietary business data

Prefer safe metadata.

## 7. Correlation IDs make debugging possible

```text
requestId=abc
    |
    +--> Controller
    +--> Service
    +--> AI call
    +--> Error
    +--> Metrics
```

## 8. Do not override framework exception handling blindly

Spring MVC already handles many standard web exceptions.

A generic `Exception` handler can accidentally change expected HTTP behavior.

---

# P1 Interview Revision

## Spring AI

### What is Spring AI?

Spring AI provides abstractions and integrations for building AI-powered applications in the Spring ecosystem.

### What is ChatClient?

`ChatClient` is the application-facing abstraction used to interact with a chat model.

### Why use an abstraction?

It reduces coupling between application code and a specific AI provider.

---

## Memory

### Why is conversation memory required?

Separate requests do not automatically contain previous conversation context.

### Trade-off?

More history gives more context but increases tokens, latency, and cost.

---

## Structured Output

### Why structured output?

It provides a predictable format that application code can validate and consume.

### Can it be blindly trusted?

No. Model output is probabilistic and should be validated.

---

## Resilience

### Retry vs Circuit Breaker?

Retry attempts recovery from transient failures.

Circuit Breaker stops repeated calls to an unhealthy dependency.

### Rate Limiter vs Bulkhead?

Rate Limiter controls request rate.

Bulkhead controls concurrent execution/resource usage.

### Why timeout?

To prevent requests from waiting indefinitely.

---

## Exception Handling

### Why can a generic Exception handler be dangerous?

It can intercept framework exceptions that Spring MVC would otherwise map to appropriate HTTP responses.

### What is ResponseEntityExceptionHandler useful for?

It provides a foundation for centralized/custom handling of Spring MVC exceptions.

---

## Observability

### What is observability?

The ability to understand internal system behavior from telemetry.

### Logs vs Metrics vs Traces?

```text
Logs
→ What happened?

Metrics
→ How often / how much?

Traces
→ Where did it happen / where did time go?
```

### Why is AI observability important?

AI applications have:

- Variable latency
- Provider failures
- Token usage
- Cost
- Model/provider differences
- Retries
- Timeouts
- Rate limits
- Streaming behavior

### What is a correlation ID?

An identifier used to associate events belonging to the same request.

### What is MDC?

Mapped Diagnostic Context, commonly used to attach values such as request IDs to logs.

### Why clean MDC?

Threads can be reused, so old context must not leak into another request.

### Why can MDC be tricky with reactive code?

Traditional MDC is thread-local while reactive execution can move across threads.

---

# Practical Debugging Checklist

When an AI request fails:

```text
1. Check HTTP status
2. Check request validation
3. Check requestId
4. Check application logs
5. Check errorCode
6. Check provider/model
7. Check latency
8. Check Retry behavior
9. Check Circuit Breaker state
10. Check Rate Limiter
11. Check Bulkhead
12. Check provider availability
```

For slow requests:

```text
Request
   |
   +--> Application latency
   +--> Retrieval/tool latency (future projects)
   +--> Provider latency
   +--> Total latency
```

Measure instead of guessing.

---

# One-Minute P1 Revision

Project 1 builds the production foundation of the GenAI platform.

Core flow:

```text
Client
 → Controller
 → Validation
 → Service
 → Provider Client
 → Resilience
 → Spring AI
 → Groq
 → GPT OSS 120B
```

Capabilities:

```text
Memory
Structured Output
Guardrails
Retry
Timeout
Circuit Breaker
Rate Limiter
Bulkhead
Exception Handling
Validation
Logging
Metrics
Actuator
Testing
```

Core principle:

> The LLM is a powerful but probabilistic external dependency, so the application must provide deterministic boundaries for validation, resilience, observability, and safe error handling.

---

# Project 1 Final Status

```text
┌────────────────────────────────────────────┐
│          PROJECT 1 — AI COPILOT            │
│                                            │
│  AI Chat                         ✓         │
│  Conversation Memory             ✓         │
│  Structured Output               ✓         │
│  Guardrails                      ✓         │
│  Retry                           ✓         │
│  Timeout                         ✓         │
│  Circuit Breaker                 ✓         │
│  Rate Limiter                    ✓         │
│  Bulkhead                        ✓         │
│  Validation                      ✓         │
│  Exception Handling              ✓         │
│  Metrics                         ✓         │
│  Actuator                        ✓         │
│  Logging                         ✓         │
│  Unit Tests                      ✓         │
│  Integration Tests               ✓         │
│  Black-box Testing               ✓         │
│                                            │
│              P1 COMPLETE                  │
└────────────────────────────────────────────┘
```

---

# Next Stage

## Project 2 — RAG

Project 2 extends the same AI Copilot with enterprise knowledge retrieval.

High-level flow:

```text
Documents
    |
    v
Document Ingestion
    |
    v
Transformation / Chunking
    |
    v
Embedding Model
    |
    v
Vectors
    |
    v
Vector Store
    |
    v
Retrieval
    |
    v
RAG Answer
    |
    v
Grounded AI Response
```

Project 2 is an extension of this same platform, not an unrelated application.

---
