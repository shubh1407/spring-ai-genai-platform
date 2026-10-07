# P3 — Day 1: Tool Calling Fundamentals

## Status

**Day 1 — COMPLETE**

## Goal

Move the existing P2 Chat/RAG application toward tool-using AI by exposing the existing knowledge-search capability as an LLM tool.

---

## 1. Tool Calling

Normal chat:

```text
User → LLM → Answer
```

Tool-enabled chat:

```text
User
 ↓
LLM
 ↓
Tool decision
 ↓
Tool call
 ↓
Application executes tool
 ↓
Tool result
 ↓
LLM
 ↓
Final answer
```

**Key rule:** The LLM does not execute the tool. The application executes it.

---

## 2. Function Calling vs Tool Calling

### Function Calling

The model produces a structured request to invoke a function with arguments.

```json
{
  "function": "getOrderStatus",
  "arguments": {
    "orderId": "ORD-123"
  }
}
```

### Tool Calling

The broader capability model where an LLM can request application capabilities such as:

- Java functions
- Database operations
- REST APIs
- Search
- Internal services
- External systems

---

## 3. Tool Definition and Schema

A tool definition should communicate:

- Name
- Description
- Parameters
- Parameter types
- Required parameters

Conceptually:

```json
{
  "name": "getOrderStatus",
  "description": "Returns the current status of an order",
  "parameters": {
    "type": "object",
    "properties": {
      "orderId": {
        "type": "string"
      }
    },
    "required": ["orderId"]
  }
}
```

Tool descriptions help the LLM decide which capability is relevant.

---

## 4. Tool Execution Lifecycle

```text
User
 ↓
LLM
 ↓
Need a tool?
 ├── No → Answer
 └── Yes
       ↓
    Tool Call
       ↓
   Application
       ↓
Input validation
       ↓
Authorization
       ↓
Tool execution
       ↓
DB / API / RAG
       ↓
Tool result
       ↓
LLM
       ↓
Final answer
```

The application remains responsible for execution, validation and security.

---

## 5. Tool Results and Errors

Possible results include:

- Success
- Not found
- Validation failure
- Authorization failure
- External-service failure

Internal exceptions should not be blindly exposed as tool output.

---

## 6. LLM Responsibility vs Application Responsibility

### LLM

- Understand request
- Decide whether a tool is useful
- Select tool
- Generate tool arguments

### Application

- Validate arguments
- Enforce authorization
- Execute tool
- Handle failures
- Control data/system access
- Return result to LLM

Mental model:

```text
LLM = decision maker
Application = execution + security boundary
```

---

## 7. Tool Calling vs Agent

Tool calling:

```text
LLM → Tool → Result → LLM
```

An agent introduces a decision loop and can perform multiple steps:

```text
LLM
 ↓
Tool A
 ↓
Result
 ↓
LLM
 ↓
Tool B
 ↓
Result
 ↓
LLM
 ↓
Final answer
```

Therefore, tool calling is a foundational building block for agentic systems.

Agent loops and planning are intentionally covered in later P3 days.

---

## 8. Tenant Isolation

The LLM should not control tenant authorization context.

The tool exposes:

```text
searchKnowledge(query)
```

not:

```text
searchKnowledge(query, tenantId)
```

Current prototype flow:

```text
HTTP Request
 ↓
Controller
 ↓
Request-scoped TenantContext
 ↓
ChatClient
 ↓
LLM
 ↓
searchKnowledge(query)
 ↓
TenantContext → tenantId
 ↓
PGVector tenant filter
```

The LLM controls the semantic query; the application controls the tenant context.

---

## 9. First Real Project Tool

Instead of a calculator demo, the existing P2 knowledge capability was exposed as the first P3 tool.

```text
searchKnowledge(query)
```

Architecture:

```text
KnowledgeSearchTool
 ↓
DocumentVectorStoreService
 ↓
VectorStore
 ↓
PGVector
```

The tool does not access PGVector directly. It reuses the existing P2 service abstraction.

---

## 10. Implemented Tool

The implemented tool follows this structure:

```java
@Component
public class KnowledgeSearchTool {

    private final DocumentVectorStoreService documentVectorStoreService;
    private final TenantContext tenantContext;

    public KnowledgeSearchTool(
            DocumentVectorStoreService documentVectorStoreService,
            TenantContext tenantContext) {
        this.documentVectorStoreService = documentVectorStoreService;
        this.tenantContext = tenantContext;
    }

    @Tool(
        name = "searchKnowledge",
        description = "Search the company's internal knowledge base for relevant policies, documentation, and business information."
    )
    public List<Document> searchKnowledge(
            @ToolParam(
                required = true,
                description = "The user's question or information to search for in the internal knowledge base."
            )
            String query) {

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(20)
                .similarityThreshold(0.7)
                .filterExpression(
                    new FilterExpressionBuilder()
                        .eq("tenantId", tenantContext.getTenantId())
                        .build()
                )
                .build();

        return documentVectorStoreService.searchWithRequest(searchRequest);
    }
}
```

### Design decisions

The LLM controls only:

```text
query
```

The application controls:

```text
tenantId
topK
similarityThreshold
metadata filter
vector-store access
```

This is important because infrastructure and authorization controls should not become LLM-controlled arguments.

---

## 11. Spring AI Tool Registration

The tool is registered with the existing `ChatClient`:

```java
client.prompt()
    .system(systemPrompt)
    .user(prompt)
    .tools(knowledgeSearchTool)
    .advisors(messageChatMemoryAdvisor)
    .call()
    .content();
```

The existing AI provider abstraction is reused; a second independent LLM client is not required.

---

## 12. Actual Tool Calling Verification

The running application produced Spring AI tool-execution logs:

```text
Executing tool call: searchKnowledge
Starting execution of tool: searchKnowledge
```

The tool performed real vector retrieval:

```text
query=refund policy
resultCount=8
```

The same interaction then triggered another tool call:

```text
query=refund
resultCount=9
```

Both tool executions completed successfully before the final AI response.

Therefore the verified flow was:

```text
User
 ↓
LLM
 ↓
searchKnowledge("refund policy")
 ↓
KnowledgeSearchTool
 ↓
DocumentVectorStoreService
 ↓
PGVector
 ↓
8 documents
 ↓
LLM
 ↓
searchKnowledge("refund")
 ↓
KnowledgeSearchTool
 ↓
DocumentVectorStoreService
 ↓
PGVector
 ↓
9 documents
 ↓
LLM
 ↓
Final answer
```

This also demonstrated that the model can request multiple tool calls during one interaction.

---

## 13. Day 1 Architecture

```text
                         USER
                           │
                           ▼
                     Agent Controller
                           │
                           ▼
                    AiProviderClient
                           │
                           ▼
                       ChatClient
                           │
                           ▼
                          LLM
                           │
                     Tool decision
                           │
                           ▼
                 KnowledgeSearchTool
                           │
                    TenantContext
                           │
                           ▼
              DocumentVectorStoreService
                           │
                           ▼
                      VectorStore
                           │
                           ▼
                        PGVector
                           │
                           ▼
                     Tool result
                           │
                           ▼
                          LLM
                           │
                           ▼
                    Final response
```

The existing `RagController` remains separate. It continues to represent the deterministic P2 RAG flow. The agent/tool endpoint is used to observe LLM-driven tool execution independently.

---

## 14. Key Engineering Decisions

### Reuse P2 services

```text
Tool → Service → VectorStore → PGVector
```

### Do not expose tenantId to the LLM

```text
searchKnowledge(query)
```

instead of:

```text
searchKnowledge(query, tenantId)
```

### Do not expose infrastructure controls to the LLM

The application owns:

- Top-K
- Similarity threshold
- Tenant filter
- Vector-store implementation

### Keep a dedicated tool layer

Tool classes represent capabilities exposed to the LLM.

Existing services remain responsible for application/business operations.

### Tool calling is not the complete agent

Day 1 proves tool calling. Agent loops, planning, multi-step execution and memory are intentionally covered later.

---

## 15. Day 1 Testing Checklist

- [x] Application starts successfully
- [x] `KnowledgeSearchTool` is registered
- [x] LLM can select `searchKnowledge`
- [x] Tool executes through Spring AI
- [x] Tool reaches `DocumentVectorStoreService`
- [x] Real PGVector retrieval occurs
- [x] Tenant filter is applied
- [x] Tool result returns to the LLM
- [x] Multiple tool calls work in one interaction
- [x] Final response is generated after tool execution

---

## 16. Interview Questions

### 1. What is tool calling?

A mechanism where an LLM can request an application-defined capability using structured arguments while the application performs the actual execution.

### 2. Does the LLM execute the tool?

No. The application executes it.

### 3. What is function calling?

A mechanism where the model produces a structured function invocation and its arguments.

### 4. What is a tool schema?

A structured description of a tool's name, purpose, parameters, types and required inputs.

### 5. Why are tool descriptions important?

The LLM uses them when deciding which available tool is relevant.

### 6. Who generates tool arguments?

The LLM generates the requested arguments according to the schema, but the application must validate them.

### 7. Who is responsible for authorization?

The application, not the LLM.

### 8. Why should tenantId not be an LLM-controlled argument?

Because tenant identity is an authorization concern. Allowing the model to select it could enable cross-tenant access.

### 9. Tool calling vs Agent?

Tool calling provides the capability to invoke tools. An agent adds decision loops and potentially multiple tool invocations to accomplish a task.

### 10. Why separate tool and service layers?

To separate LLM-facing capabilities from application/business services and infrastructure.

### 11. Why does `KnowledgeSearchTool` use `DocumentVectorStoreService`?

To preserve the P2 service abstraction and avoid coupling the tool directly to PGVector.

### 12. What controls remain outside the LLM?

Tenant filtering, top-K, similarity threshold and vector-store access.

### 13. What happens after a tool returns?

The result is provided back to the LLM, which can use it for the final answer or request another tool.

### 14. Can an LLM call multiple tools?

Yes. Day 1 demonstrated multiple `searchKnowledge` calls within one interaction.

### 15. Explain the complete lifecycle.

```text
User
 ↓
LLM
 ↓
Tool Call
 ↓
Application
 ↓
Validation / Authorization
 ↓
Tool Execution
 ↓
Tool Result
 ↓
LLM
 ↓
Final Answer
```

---

## 17. Final Mental Model

```text
             User
               ↓
              LLM
               ↓
         Tool decision
               ↓
              Tool
               ↓
     Application + Security
               ↓
        DB / API / RAG
               ↓
          Tool result
               ↓
              LLM
               ↓
         Final answer
```

## Day 1 Outcome

The P2 knowledge system now has its first agentic capability:

> The LLM can decide to use the application's `searchKnowledge` capability, the application executes it against tenant-scoped PGVector data, and the result is returned to the LLM for final response generation.

# P3 Day 1 — COMPLETE

# Day 2 — Spring AI Tools

## 1. Tool Fundamentals

A tool exposes an application capability that an LLM can invoke when it needs information or an action.

```text
User
  ↓
LLM / Agent
  ↓
Tool decision
  ↓
Application Tool
  ↓
Tool result
  ↓
LLM
  ↓
Final answer
```

The LLM decides whether a tool is useful; the application owns execution.

## 2. Spring AI Tool API

### `@Tool`

```java
@Tool(
    name = "searchKnowledge",
    description = "Search the company's internal knowledge base for relevant policies, documentation, and business information."
)
public ToolResponse searchKnowledge(String query) {
    ...
}
```

- `name`: model-facing tool name.
- `description`: explains the capability and helps tool selection.

### `@ToolParam`

```java
@ToolParam(
    required = true,
    description = "The user's question or information to search for in the internal knowledge base."
)
String query
```

It defines the input parameter exposed to the model.

## 3. Registering the Tool

Current Spring AI usage:

```java
client.prompt()
    .system(systemPrompt)
    .user(prompt)
    .tools(knowledgeSearchTool)
    .advisors(...)
    .call()
    .content();
```

`.tools(...)` makes the tool available for that invocation. Availability does not guarantee execution; the model decides whether to call it.

## 4. Tool Execution Lifecycle

```text
1. Application sends prompt + tool definitions
2. LLM decides whether a tool is required
3. LLM generates a tool call
4. Spring AI receives the tool call
5. Spring AI executes the Java method
6. Tool result is converted to a tool-result message
7. LLM receives the result
8. LLM generates the final response
```

Verified in this project through logs:

```text
Executing tool call: searchKnowledge
Starting execution of tool: searchKnowledge
Successful execution of tool: searchKnowledge
Converting tool result to JSON
```

## 5. Normal Chat vs Tool-enabled Request

Normal path:

```text
Controller
  ↓
ChatService
  ↓
AiProviderClient
  ↓
ChatClient
  ↓
LLM
```

Agent path:

```text
Controller
  ↓
AgentService
  ↓
AiProviderClient
  ↓
ChatClient
  ↓
LLM
  ↓
KnowledgeSearchTool (when selected)
  ↓
Tool result
  ↓
LLM
  ↓
Final answer
```

The normal `/chat` and tool-enabled `/agent-ask` paths remain separate.

---

# Day 3 — Tool Design & Contracts

## 1. Tool as a Contract

A tool is not merely a Java method exposed to an LLM. It is a model-facing contract.

The contract defines:

- What the tool does
- When it should be used
- What inputs it accepts
- Which inputs are required
- What validation is enforced
- What result it returns
- What belongs to the LLM vs the application

## 2. Tool Description

Current tool:

```java
@Tool(
    name = "searchKnowledge",
    description = "Search the company's internal knowledge base for relevant policies, documentation, and business information."
)
```

Good tool descriptions should be specific, action-oriented and useful for tool selection.

## 3. Input Contract

Current input:

```java
@ToolParam(
    required = true,
    description = "The user's question or information to search for in the internal knowledge base."
)
String query
```

Application validation remains necessary because model-generated input cannot be treated as trusted input.

Current validation:

```java
if (query == null || query.isBlank()) {
    throw new ToolValidationException(
        "Search query must not be blank"
    );
}

if (query.length() > 500) {
    throw new ToolValidationException(
        "Search query is too long"
    );
}
```

## 4. Tool Validation Exception

Invalid tool input is different from an AI provider failure.

```text
Invalid tool input
    → ToolValidationException
    → TOOL_VALIDATION_ERROR
    → HTTP 400

LLM/provider failure
    → AI_PROVIDER_ERROR
```

This keeps error classification meaningful.

## 5. Tool Granularity

A tool should represent a meaningful application capability.

Too broad:

```text
doEverything()
```

Too low-level:

```text
executeSql()
```

Better:

```text
searchKnowledge()
```

The LLM selects a capability; the application owns implementation details.

## 6. LLM vs Application Responsibility

### LLM

- Understand the request
- Decide whether a tool is needed
- Select a tool
- Supply parameters
- Interpret the result
- Generate the final response

### Application

- Validate inputs
- Enforce tenant/security boundaries
- Execute deterministic logic
- Access databases/vector stores/APIs
- Apply retrieval algorithms
- Enforce business rules
- Return structured results

Security and authorization must not depend on the LLM.

## 7. Reusing P2 Retrieval

The first tool implementation directly called the vector store:

```text
KnowledgeSearchTool
  ↓
SearchRequest
  ↓
DocumentVectorStoreService
  ↓
PGVector
```

That bypassed P2's retrieval intelligence.

Final architecture:

```text
KnowledgeSearchTool
  ↓
RagRetrievalService
  ↓
QueryTransformer
  ↓
MultiQueryGenerator
  ↓
MultiQueryRetriever
  ↓
DocumentReranker
  ↓
Documents
```

Therefore P3 reuses the same P2 retrieval pipeline instead of creating a second simplified search implementation.

## 8. Why the Tool Does Not Call `RagService.ask()`

`ask()` performs the complete RAG operation, including the LLM call.

Calling it from a tool would create:

```text
Agent LLM
  ↓
Tool
  ↓
RagService.ask()
  ↓
Retrieval
  ↓
Another LLM call
  ↓
Tool result
  ↓
Agent LLM
```

That would create unnecessary nested LLM inference.

The reusable boundary is the retrieval operation, not the complete RAG answer operation.

## 9. `RagRetrievalService`

The retrieval pipeline was extracted into a reusable service so P2 RAG and the P3 tool can share exactly the same retrieval intelligence.

```text
P2 RAG:

RagService
   ↓
RagRetrievalService
   ↓
Query Transform
   ↓
Multi Query
   ↓
Retrieval
   ↓
Re-ranking
   ↓
Context
   ↓
LLM
```

```text
P3 Agent:

AgentService
   ↓
AiProviderClient
   ↓
KnowledgeSearchTool
   ↓
RagRetrievalService
   ↓
Query Transform
   ↓
Multi Query
   ↓
Retrieval
   ↓
Re-ranking
   ↓
Documents + Citations
   ↓
Agent LLM
```

The `AiProviderClient` no longer holds `KnowledgeSearchTool` as a constructor dependency. The tool is supplied to the tool-enabled request, preventing the Spring bean dependency cycle.

## 10. Tool Output

The tool returns a structured `ToolResponse` containing:

```text
Documents
+
Citations
```

Current flow:

```java
List<Document> finalDocuments =
        ragRetrievalService.retrieveDocument(
            query,
            tenantContext.getTenantId()
        );

List<Citation> citations =
        citationBuilder.extractSources(finalDocuments);

return new ToolResponse(finalDocuments, citations);
```

No separate LLM-generated answer is produced by the tool.

## 11. Tenant Boundary

Tenant ID comes from application-controlled `TenantContext`.

The LLM does not provide the tenant ID as a tool parameter.

```text
Agent
  ↓
searchKnowledge(query)
  ↓
Application TenantContext
  ↓
Tenant-specific retrieval
```

Full authentication-derived tenant identity is a later P4 security concern.

## 12. Read vs Write Tools

`searchKnowledge` is a read-only tool.

Examples of read tools:

```text
searchKnowledge()
getOrder()
getCustomer()
```

Write/action tools have greater risk:

```text
refundCustomer()
deleteUser()
sendEmail()
updateOrder()
```

Write tools require stronger validation, authorization, approval and idempotency controls.

## 13. Idempotency

An idempotent operation can safely be repeated without unintended additional side effects.

`searchKnowledge` is read-only and has no external side effect.

For future action tools, idempotency becomes important because an agent may retry or repeat a call.

## 14. Tool Error Contract

Conceptually, tool failures should be distinguishable from provider failures:

```text
Invalid tool input
→ TOOL_VALIDATION_ERROR

Unauthorized tool action
→ TOOL_AUTHORIZATION_ERROR

External tool failure
→ TOOL_EXECUTION_ERROR

LLM/provider failure
→ AI_PROVIDER_ERROR
```

The exact taxonomy can evolve when action tools are introduced.

---

# Day 3 Final Architecture

```text
                       Agent / LLM
                            │
                            │ decides
                            ▼
                  KnowledgeSearchTool
                            │
                     validate input
                            │
                            ▼
                   RagRetrievalService
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
       Query Transform   Multi Query    Re-ranking
                            │
                            ▼
                     Vector Retrieval
                            │
                            ▼
                         PGVector
                            │
                            ▼
                     Documents
                            │
                            ▼
                      Citations
                            │
                            ▼
                       ToolResponse
                            │
                            ▼
                          Agent
```

## Day 3 Status: COMPLETE

# P3 Day 4–5 — Agent Fundamentals, Agent Loop & Multi-Step Execution

## 1. Agent Fundamentals

### What is an Agent?

A normal chatbot mainly generates an answer:

```text
User → LLM → Answer
```

A tool-enabled AI system can invoke application capabilities:

```text
User → LLM → Tool → Result → LLM → Answer
```

An **agent** goes one step further: the LLM can decide what action is needed, observe the result, and decide what to do next.

```text
User
 ↓
LLM
 ↓
Action / Tool
 ↓
Observation
 ↓
LLM
 ↓
Next Action OR Final Answer
```

### Core Agent Idea

```text
Action → Observation → Decision
```

- **Action:** LLM requests a tool.
- **Observation:** Tool executes and returns a result.
- **Decision:** LLM evaluates the result and decides whether another action is required.

---

## 2. P2 RAG + P3 Agent

P2 already provides the knowledge capability:

```text
Query
 ↓
Query Transformation
 ↓
Multi Query
 ↓
Vector Retrieval
 ↓
Re-ranking
 ↓
Citations
```

P3 does not rebuild this.

Instead:

```text
Agent
 ↓
searchKnowledge Tool
 ↓
P2 RAG
 ↓
Knowledge Result
 ↓
LLM
```

### Important distinction

**P2:**

> How can the system retrieve relevant enterprise knowledge?

**P3:**

> How can the LLM decide when and how to use that capability?

So P2 provides the **capability**, while P3 adds **agentic orchestration**.

---

## 3. Tool as an Agent Capability

The existing tool:

```text
searchKnowledge
```

is exposed using Spring AI tool support.

Important APIs used:

```java
@Tool
@ToolParam
```

and:

```java
.tools(knowledgeSearchTool)
```

The LLM decides whether the tool is required.

The tool itself remains deterministic application code; the LLM decides **when to use it**, not how the underlying retrieval implementation works.

---

# Day 5 — Agent Loop & Multi-Step Execution

## 4. Single Tool vs Multi-Step Execution

Single-tool execution:

```text
LLM
 ↓
searchKnowledge
 ↓
Result
 ↓
LLM
 ↓
Answer
```

Multi-step execution:

```text
LLM
 ↓
Tool 1
 ↓
Result
 ↓
LLM
 ↓
Tool 2
 ↓
Result
 ↓
LLM
 ↓
Answer
```

The important concept is:

> Tool 1 does not directly call Tool 2. The LLM receives Tool 1's result and decides whether Tool 2 is required.

The LLM acts as the bridge between tools.

---

## 5. Multi-Tool Chaining Example

Two tools are available:

```text
searchKnowledge
checkRefundEligibility
```

User request:

> I am a standard customer and purchased something on 2026-08-20. Check whether I am eligible for a refund according to our refund policy.

Agent flow:

```text
User
 ↓
LLM
 ↓
searchKnowledge("refund policy")
 ↓
P2 RAG retrieves refund policy
 ↓
Result returned to LLM
 ↓
LLM understands the applicable policy
 ↓
checkRefundEligibility(...)
 ↓
Eligibility result
 ↓
LLM
 ↓
Final answer
```

This is **multi-step agent execution**.

---

## 6. `checkRefundEligibility` Tool

Purpose:

> Perform deterministic refund eligibility calculation.

Parameters:

```text
policyName
customerType
purchaseDate
```

The tool obtains the current application date itself.

Example policy used for the project:

```text
STANDARD   → 45 days
ENTERPRISE → 60 days
```

Example:

```text
customerType  = STANDARD
purchaseDate  = 2026-08-20
currentDate   = 2026-09-21
```

Calculation:

```text
daysSincePurchase = 32
refundWindow = 45
32 <= 45
↓
Eligible
```

The tool returns a structured eligibility result.

### Important design principle

The eligibility calculation is deterministic application logic.

The LLM should decide **when to invoke the tool**, while the tool performs the actual business calculation.

---

## 7. Spring AI Tool-Calling Loop

No custom `while` loop was required.

Multiple tools are exposed to the model:

```java
.tools(
    knowledgeSearchTool,
    refundEligibilityTool
)
```

Spring AI manages the tool-calling lifecycle:

```text
LLM
 ↓
Tool Call
 ↓
Tool Execution
 ↓
Tool Result
 ↓
LLM
 ↓
Another Tool OR Final Answer
```

This is preferable to manually implementing a duplicate agent loop when the framework already provides the lifecycle.

---

## 8. Structured Agent Response

The agent response was changed from a simple message response to a structured `AgentResponse`.

```java
.call()
.entity(AgentResponse.class);
```

Final response example:

```json
{
  "message": "You are eligible for a refund. Your purchase on 2026-08-20 is within the 45-day refund window for Standard customers.",
  "toolsUsed": [
    "searchKnowledge",
    "checkRefundEligibility"
  ],
  "sources": [
    {
      "source": "Company Refund Policy: Customers can request a refund within 45 days of purchase.",
      "fileName": "company-policy.txt",
      "fileType": "txt",
      "pageNumber": "",
      "chunkIndex": "0"
    }
  ]
}
```

The response provides:

- `message` — final answer
- `toolsUsed` — tools involved in the agent interaction
- `sources` — supporting knowledge sources

---

## 9. Agent vs Deterministic Workflow

This distinction is important for AI system design interviews.

### Deterministic workflow

Application decides the sequence:

```text
searchKnowledge
 ↓
checkRefundEligibility
 ↓
return answer
```

### Agentic workflow

The LLM decides whether the next action is required:

```text
LLM
 ↓
searchKnowledge
 ↓
observe result
 ↓
LLM decides
 ↓
checkRefundEligibility
 ↓
observe result
 ↓
LLM
 ↓
answer
```

Use an agent when the next action depends on the information obtained during execution.

Use a deterministic workflow when the sequence is known and predictable.

---

## 10. Testing

### Test 1 — General question

```text
What is Java?
```

Result:

```text
Direct LLM answer
No tool required
```

### Test 2 — Knowledge question

```text
What is our refund policy?
```

Result:

```text
searchKnowledge
 ↓
P2 RAG
 ↓
Final answer
```

### Test 3 — Multi-step request

```text
I am a standard customer and purchased something on
2026-08-20. Check whether I am eligible for a refund.
```

Result:

```text
searchKnowledge
 ↓
checkRefundEligibility
 ↓
Final AgentResponse
```

Application logs confirmed both tools executed sequentially in the same agent request.

---

# AI / Interview Key Takeaways

### 1. What makes a system agentic?

The LLM can decide what action/tool to take based on the current task and observations.

### 2. What is an agent loop?

```text
Action → Observation → Decision
```

### 3. Does Tool A directly invoke Tool B?

No.

```text
Tool A → Result → LLM → Tool B
```

The LLM orchestrates the interaction.

### 4. Difference between RAG and Agent?

**RAG** retrieves relevant knowledge.

**Agent** decides which capabilities/tools to use and can perform multiple steps.

### 5. Can an agent use RAG?

Yes.

RAG can be exposed as one of the agent's tools.

### 6. Why keep business logic inside tools?

The LLM should decide **what action is needed**. Deterministic business rules should remain in application code for reliability and control.

### 7. Agent vs workflow?

Known fixed sequence → deterministic workflow.

Dynamic next-step decision → agentic workflow.

### 8. Why is multi-step execution important?

It allows the system to solve tasks that require multiple dependent actions instead of producing an answer after a single tool call.

---

## Day 4–5 Completion

- [x] Agent fundamentals
- [x] Chatbot vs tool-enabled system vs agent
- [x] P2 RAG reused as an agent capability
- [x] Spring AI tool registration
- [x] Agent loop
- [x] Action → Observation → Decision
- [x] Multi-tool chaining
- [x] Second deterministic tool
- [x] Spring AI tool-calling lifecycle
- [x] Structured AgentResponse
- [x] Sources in agent response
- [x] Agent vs deterministic workflow
- [x] End-to-end testing
- [x] Multi-step execution verified

**P3 Day 4 + Day 5 — COMPLETE**

# P3 Day 6 — Planning & Workflow

## Objective

Day 6 introduced **explicit planning inside an agentic workflow**.

Day 5 demonstrated dynamic multi-step execution:

`LLM → Tool → Result → LLM → Next Tool`

Day 6 adds a planning capability without converting the agent into a deterministic workflow.

> Planning guides execution, but the LLM remains responsible for deciding which tool to call, when to continue, and when re-planning is required.

---

## 1. Planning vs Tool Calling

### Tool Calling

Tool calling allows the LLM to invoke an application capability.

`LLM → searchKnowledge → Result`

### Planning

Planning determines how a complex goal can be approached.

`User Goal → Execution Plan → Agent selects tools → Results → Continue/Re-plan`

Planning and execution are different responsibilities.

---

## 2. Agentic Planning

The planning capability was implemented as an actual Spring AI tool:

`createExecutionPlan`

For a simple request:

`User → LLM → Final Answer`

For a complex request:

`User → Agent → createExecutionPlan → Plan → Business Tools → Result → Final Answer`

The plan is **not an execution script**. It does not directly execute `searchKnowledge` or `checkRefundEligibility`.

The main agent receives the plan and remains responsible for selecting and invoking the actual tools.

---

## 3. Planning DTO

The execution plan uses structured output:

```java
public record AgentPlan(
    String goal,
    List<PlanStep> steps
) {}
```

Each step:

```java
public record PlanStep(
    int stepNumber,
    String action,
    String purpose
) {}
```

This provides a structured representation instead of relying on unstructured text.

---

## 4. Planning Tool

The planning capability is exposed through Spring AI:

```java
@Component
public class PlanningTool {

    private final AiProviderClient aiProviderClient;

    public PlanningTool(AiProviderClient aiProviderClient) {
        this.aiProviderClient = aiProviderClient;
    }

    @Tool(
        name = "createExecutionPlan",
        description = """
            Create an execution plan for a complex user request before executing
            business actions. Use this tool when the request requires multiple
            dependent actions, information gathering, or decision-making across
            multiple steps.
            The result of this tool is a plan for the agent to follow.
            This tool does not execute business actions.
            Do not use this tool for simple requests that can be answered directly.
            """
    )
    public AgentPlan createExecutionPlan(
        @ToolParam(
            required = true,
            description = "The user's goal or request that needs to be planned."
        )
        String goal
    ) {
        if (goal == null || goal.isBlank()) {
            throw new ToolValidationException(
                "Planning goal must not be blank"
            );
        }

        if (goal.length() > 1000) {
            throw new ToolValidationException(
                "Planning goal is too long"
            );
        }

        return aiProviderClient.createExecutionPlan(goal);
    }
}
```

**Design rule:** `PlanningTool` creates the plan; it does not execute the plan.

---

## 5. LLM Creates the Plan

Plan generation uses structured output:

```java
public AgentPlan createExecutionPlan(String goal) {
    return client.prompt()
        .system("""
            You are an execution planning assistant.
            Create a concise execution plan for the user's goal.
            Break complex tasks into meaningful steps.
            Do not execute tools.
            Do not invent facts.
            Keep the plan concise.
            Return the result as the requested structured output.
            """)
        .user(goal)
        .call()
        .entity(AgentPlan.class);
}
```

The LLM creates the plan; the main agent remains responsible for execution.

---

## 6. Complete Agent Flow

```text
User Goal
    ↓
Agent LLM
    ↓
Does this require planning?
    ↓
createExecutionPlan
    ↓
AgentPlan
    ↓
Agent decides next action
    ↓
searchKnowledge
    ↓
Tool Result
    ↓
Agent evaluates result
    ↓
checkRefundEligibility
    ↓
Tool Result
    ↓
Validation
    ↓
Final Answer
```

There is no hardcoded sequence such as:

```text
1. call planner
2. call searchKnowledge
3. call refundEligibility
```

The LLM remains in the decision loop.

---

## 7. Multi-Step Agent Traversal

Tool A does not directly call Tool B.

The model receives Tool A's result and decides whether Tool B is required:

`Tool A → LLM → Tool B`

For the refund scenario:

```text
createExecutionPlan
        ↓
      LLM
        ↓
searchKnowledge
        ↓
      LLM
        ↓
checkRefundEligibility
        ↓
      LLM
        ↓
Final Answer
```

The LLM acts as the decision-making bridge between tool executions.

---

## 8. Re-Planning

A plan is not permanent.

If the agent determines that the current result is insufficient, unexpected, or does not satisfy the user's goal, it can request a new plan.

```text
Initial Plan
    ↓
Tool Execution
    ↓
Result
    ↓
Result sufficient?
   ├── Yes → Continue / Complete
   └── No  → createExecutionPlan again
                    ↓
                Revised Plan
                    ↓
                Tool Execution
```

Re-planning is **agent-driven**, not a hardcoded application workflow.

---

## 9. Deterministic Workflow vs Agentic Planning

### Deterministic Workflow

The application controls the sequence:

`A → B → C → D`

The execution path is known beforehand.

### Agentic Planning

The model dynamically decides the traversal:

`Goal → Plan → Tool → Result → LLM Decision → Next Tool / Re-plan / Finish`

Agentic planning is useful when the exact execution path cannot always be known in advance.

---

## 10. Spring AI Tool Calling

Tools are provided to the model:

```java
.tools(
    planningTool,
    knowledgeSearchTool,
    refundEligibilityTool
)
```

Conceptually:

```text
LLM requests tool call
        ↓
Spring AI Tool Calling Manager
        ↓
Tool execution
        ↓
Tool result returned to model
        ↓
LLM decides next action
```

The basic agent loop does not require a manually implemented `while` loop.

---

## 11. Day 6 Test

A complex refund request was used:

```text
I want to determine whether I am eligible for a refund.
My purchase date was 2026-08-20 and I am a STANDARD customer.
First determine the applicable company refund policy from the
internal knowledge base, then check my refund eligibility based
on that policy and my purchase date.
If the available information is insufficient at any point,
determine what additional information or action is required.
```

The demonstrated execution path:

```text
createExecutionPlan
        ↓
searchKnowledge
        ↓
checkRefundEligibility
        ↓
Final Answer
```

Re-planning was also tested as part of the Day 6 objective.

---

## 12. Important Design Decisions

- **Plan is guidance, not executable instructions.**
- **LLM remains responsible for traversal.**
- `PlanningTool` does not directly call `KnowledgeSearchTool` or `RefundEligibilityTool`.
- Planning is optional for simple requests.
- Re-planning is possible when new information changes the execution context.
- Do not replace the agent with a hardcoded `for` loop over plan steps.

---

## 13. AI / Interview Key Takeaways

### What is agentic planning?

Agentic planning is where an AI agent determines an approach for achieving a goal and dynamically selects actions/tools based on the current state and observed results.

### Is planning the same as workflow orchestration?

No.

A deterministic workflow has an application-controlled sequence. An agentic workflow allows the model to dynamically select the next action.

### Why should a plan not directly execute tools?

Separating planning from execution provides better control, validation, observability, and flexibility.

### How does re-planning work?

The agent observes an action result. If the result is insufficient or changes the execution context, it can generate a revised plan and continue.

### Does Tool A directly invoke Tool B?

Normally, no. The model receives Tool A's result and decides whether Tool B should be called.

### Where does Spring AI fit?

Spring AI provides the tool-calling infrastructure connecting model-generated tool calls with application-side tool implementations and returning tool results to the model.

---

## 14. Key Takeaways

- Planning is a capability inside an agent, not necessarily a separate deterministic workflow.
- `createExecutionPlan` was implemented as a Spring AI tool.
- The LLM generates a structured `AgentPlan`.
- The plan guides the agent but does not execute business operations.
- Tool execution remains agentic.
- Tool results are returned to the LLM before the next decision.
- The agent can perform multi-step execution.
- The agent can re-plan when the current execution state is insufficient.
- Simple requests can bypass planning.
- Planning, execution, validation, and re-planning are separate concepts.

## Day 6 Status

**Completed**

P3 progression:

`Tool Calling → Tool Engineering → Agent Fundamentals → Multi-Step Execution → Planning → Re-Planning`

# P3 Day 7 — Agent Memory

## Goal

Understand and validate conversation memory in the AI Agent.

Day 7 did not introduce a new memory implementation because the Agent already had Spring AI chat memory configured. The focus was understanding how it works, validating conversation continuity, and understanding production considerations.

---

## 1. Why Does an Agent Need Memory?

Without memory, every request is treated independently.

```text
Request 1
User: My purchase date is 2026-08-20.

Request 2
User: What was my purchase date?

Without memory:
Agent does not know.
```

With conversation memory:

```text
Request 1
User: My purchase date is 2026-08-20.
        ↓
Memory

Request 2
User: What was my purchase date?
        ↓
Memory
        ↓
Agent can answer
```

Memory gives the Agent continuity across turns.

---

## 2. Memory vs RAG

These two concepts are different.

### Memory

Stores/retrieves conversation context.

```text
User ↔ Agent conversation
```

### RAG

Retrieves external or enterprise knowledge.

```text
Question
   ↓
Retriever
   ↓
Vector Store
   ↓
Company documents
   ↓
LLM
```

Therefore:

```text
Memory = what happened in the conversation

RAG = information retrieved from knowledge sources
```

An enterprise Agent can use both.

```text
User
 ↓
Agent
 ├── Memory
 └── RAG
```

---

## 3. Existing Spring AI Memory Configuration

The Agent already uses:

```java
.advisors(messageChatMemoryAdvisor)
.advisors(advisorSpec ->
        advisorSpec.param(
                ChatMemory.CONVERSATION_ID,
                conversationId
        )
)
```

The important piece is:

```java
ChatMemory.CONVERSATION_ID
```

The conversation ID identifies which conversation history should be used.

---

## 4. Conversation ID

Example:

```text
conversationId = agent-memory-1
```

Request 1:

```text
My purchase date is 2026-08-20.
```

Request 2:

```text
What was my purchase date?
```

Because both requests use:

```text
agent-memory-1
```

they belong to the same logical conversation.

Different ID:

```text
agent-memory-2
```

means a different conversation.

---

## 5. Conversation Isolation

Conversation isolation is important in enterprise applications.

Example:

```text
Conversation A
conversationId = A

User:
My name is Rahul.
```

Another conversation:

```text
Conversation B
conversationId = B

User:
What is my name?
```

Conversation B should not receive information from conversation A.

This was tested during Day 7.

The isolation test showed that the second conversation did not have access to the first conversation's information.

---

## 6. Memory + Agent

Memory is especially useful when the Agent performs multiple steps over multiple turns.

Example:

```text
Turn 1
User:
I am a STANDARD customer.

        ↓

Memory

        ↓

Turn 2
User:
Check whether my purchase is eligible.

        ↓

Agent reads conversation context
        ↓
checkRefundEligibility
```

The Agent can combine:

```text
Memory
+
Planning
+
Tools
+
RAG
```

---

## 7. Short-Term Memory

Short-term memory means current conversation context.

Example:

```text
User:
My order ID is ORD-123.

Later:
What is my order ID?
```

The answer can come from conversation memory.

This is the type of memory used in the current P3 implementation.

---

## 8. Long-Term Memory

Long-term memory stores persistent user information beyond a single conversation.

Examples:

```text
User preferences
User profile facts
Frequently used settings
Persistent business preferences
```

Example:

```text
User prefers concise responses.
```

Long-term memory was discussed conceptually but was **not implemented in Day 7**.

---

## 9. Memory Is Not Agent State

These concepts should not be mixed.

### Memory

Answers:

> What happened in previous conversation turns?

### Agent state

Answers:

> What is the current status of this execution?

Example:

```text
Goal:
Check refund eligibility

State:
Policy retrieved
Eligibility check pending
```

Memory and state can work together but serve different purposes.

---

## 10. Production Memory Considerations

A production Agent must consider:

### Context growth

Conversation history can become very large.

```text
More history
    ↓
More tokens
    ↓
Higher cost + latency
```

### Context window

The model cannot process unlimited history.

### Persistence

For multiple application instances, memory may need shared persistence.

Possible architecture:

```text
Agent instances
      ↓
Redis / Database
      ↓
Conversation memory
```

### Security

Memory may contain sensitive information.

Consider:

- Access control
- Encryption
- Tenant isolation
- Retention
- Deletion
- PII handling

### Tenant isolation

Conversation IDs alone should not be treated as a complete security boundary.

Production systems should associate conversations with an authenticated tenant/user identity.

---

## 11. Tests Performed

### Test 1 — Basic conversation memory

Same conversation ID was used across multiple turns.

Result:

```text
Previous conversation information was available.
```

### Test 2 — Agent memory

The Agent retained context while performing tool-based execution.

### Test 3 — Conversation isolation

Different conversation ID was used.

Result:

```text
Previous conversation context was not available.
```

---

## 12. Code Change Status

### New code added?

**No.**

This is important.

Day 7 was not skipped.

The required memory infrastructure already existed from the earlier Agent implementation.

Day 7 validated and documented:

```text
ChatMemory
MessageChatMemoryAdvisor
conversationId
conversation isolation
Agent + memory
```

Adding another memory class only for the sake of a code commit would have been artificial.

---

## 13. Day 7 Architecture

```text
                 User
                  |
                  ↓
              AgentService
                  |
                  ↓
              AiProvider
                  |
          ┌───────┴────────┐
          ↓                ↓
       Memory             Tools
          ↓                ↓
 Conversation         RAG / Refund /
   History              Planner
```

---

## 14. Interview Questions

### What is Agent memory?

A mechanism that preserves relevant conversation context across interactions.

### How do you identify a conversation?

Using a conversation ID.

### Memory vs RAG?

Memory stores/retrieves conversation context; RAG retrieves external knowledge.

### Short-term vs long-term memory?

Short-term is conversation-level context; long-term persists user/business facts beyond a conversation.

### Why can memory become expensive?

More conversation history means more input tokens, latency and cost.

### Is conversationId a security boundary?

No. Production systems should bind conversation ownership to authenticated identity and tenant information.

---

## Day 7 Summary

```text
Memory
   ↓
Conversation ID
   ↓
ChatMemory
   ↓
Agent continuity
```

**Status: COMPLETE**

# P3 Day 8 — Advanced Agent Execution

## Goal

Understand how a production Agent executes complex tasks, maintains bounded autonomy, handles failures, retries/re-plans, and decides when to stop.

Day 8 was deliberately a **conceptual/design day**.

No artificial `AgentExecutionState` implementation was added because Spring AI already manages the core tool-calling loop.

---

## 1. Why Advanced Agent Execution Matters

A simple tool call looks like:

```text
User
 ↓
LLM
 ↓
Tool
 ↓
Result
 ↓
Answer
```

A real Agent can require several decisions:

```text
User
 ↓
Understand goal
 ↓
Decide whether tools are required
 ↓
Select tool
 ↓
Observe result
 ↓
Decide next action
 ↓
Another tool?
 ↓
Validate result
 ↓
Complete
```

The important concept is that the Agent can make decisions between tool executions.

---

## 2. Agent Loop

Conceptually:

```text
        ┌───────────────┐
        │     Goal      │
        └───────┬───────┘
                ↓
             Decide
                ↓
          Execute Tool
                ↓
          Observe Result
                ↓
             Validate
                ↓
        ┌───────┴────────┐
        ↓                ↓
    Continue           Complete
        |
        ↓
      Decide
```

The Agent loop is not necessarily a fixed:

```text
A → B → C
```

sequence.

The next action can depend on the result of the previous action.

---

## 3. Agent State vs Memory

This was one of the main concepts of Day 8.

### Memory

Memory represents conversation context.

```text
What did the user and Agent discuss?
```

### Agent State

State represents the current execution status.

```text
What has happened during this execution?
What remains?
What is the current objective?
```

Example:

```text
Goal:
Determine refund eligibility

Current state:
Policy retrieved
Customer type known
Purchase date known
Eligibility check pending
```

Therefore:

```text
Memory ≠ Agent State
```

They can work together.

---

## 4. Why We Did Not Add AgentExecutionState

An explicit state object was considered during Day 8.

However, creating a state class only to demonstrate the concept would have duplicated functionality already handled by the Agent/tool-calling infrastructure.

The important learning objective was to understand:

```text
State
+
Memory
+
Tool results
+
Planning
+
Stop conditions
```

rather than adding unnecessary framework code.

Therefore:

**No new code was added on Day 8.**

---

## 5. Bounded Autonomy

An enterprise Agent should not have unlimited autonomy.

The Agent should operate inside defined boundaries.

Important controls include:

```text
Allowed tools
Maximum execution time
Maximum tool calls
Retry limits
Input validation
Output validation
Security policies
Stop conditions
```

Conceptually:

```text
Agent
 ↓
Decision
 ↓
Allowed capability?
 ├── No → Stop / reject
 └── Yes
       ↓
    Execute
       ↓
    Validate
```

---

## 6. Stop Conditions

An Agent needs a clear definition of when the task is complete.

Examples:

```text
Required information collected
Required business operation completed
Final answer generated
Maximum execution limit reached
Security policy violated
Tool repeatedly failing
```

Without stop conditions, autonomous systems can become unpredictable or expensive.

---

## 7. Retry vs Re-plan

These are different recovery mechanisms.

### Retry

Use when the same operation may succeed again.

```text
Tool call
   ↓
Temporary failure
   ↓
Retry same operation
```

Example:

```text
Temporary network timeout
```

### Re-plan

Use when the current approach is no longer sufficient.

```text
Tool result
   ↓
Result insufficient
   ↓
Reconsider approach
   ↓
New plan
   ↓
Continue
```

Example:

```text
Search result does not contain required information.
```

The Agent may need another strategy.

---

## 8. Deterministic Workflow

A deterministic workflow has a predefined sequence.

```text
A
 ↓
B
 ↓
C
```

The application controls the sequence.

Example:

```text
Validate input
 ↓
Call service A
 ↓
Call service B
 ↓
Persist result
```

---

## 9. Agentic Workflow

An Agentic workflow allows the model to decide the next action.

```text
Goal
 ↓
LLM
 ↓
Tool A
 ↓
Result
 ↓
LLM
 ↓
Tool B
 ↓
Result
 ↓
LLM
 ↓
Final answer
```

The sequence can change based on results.

---

## 10. Hybrid Workflow

Production systems often combine both.

Example:

```text
Deterministic business rules
          +
Agentic decision making
```

Example:

```text
Application:
Only STANDARD and ENTERPRISE customers are allowed.

Agent:
Decides which information must be retrieved.

Application:
Validates the final business operation.
```

This gives better control than giving the LLM complete freedom.

---

## 11. Tool Calling Loop in Spring AI

Spring AI already manages the fundamental loop.

Conceptually:

```text
LLM
 ↓
Tool Call
 ↓
ToolCallingManager
 ↓
Tool Execution
 ↓
Tool Result
 ↓
LLM
 ↓
Another Tool?
 ├── Yes → execute again
 └── No → final response
```

Therefore the application should not unnecessarily implement:

```java
while (toolCall) {
    ...
}
```

The framework already handles the core mechanism.

---

## 12. Multi-Step Agent Execution

P3 already demonstrated multi-step execution before Day 8.

Example:

```text
User
 ↓
Agent
 ↓
createExecutionPlan
 ↓
searchKnowledge
 ↓
checkRefundEligibility
 ↓
Final response
```

The important learning from Day 8 is understanding what happens conceptually around those calls:

```text
Decide
 ↓
Execute
 ↓
Observe
 ↓
Validate
 ↓
Continue / Re-plan / Stop
```

---

## 13. Planning vs Execution

Planning creates guidance.

Execution performs actions.

```text
Planner
   ↓
Plan
   ↓
Agent
   ↓
Actual tools
```

The plan should not be treated as a rigid program.

The Agent must consider current results before deciding the next action.

---

## 14. Agent Failure Scenarios

A production Agent can encounter:

### Tool failure

```text
Tool
 ↓
Exception
```

Possible response:

```text
Retry
Fallback
Re-plan
Fail safely
```

### Invalid tool input

```text
LLM
 ↓
Invalid parameters
 ↓
Validation failure
```

The tool contract should reject invalid input.

### Insufficient result

```text
Tool result
 ↓
Not enough information
 ↓
Re-plan / another tool
```

### Maximum execution boundary

```text
Too many steps
 ↓
Stop safely
```

---

## 15. Security Boundary

Agent autonomy must not bypass application security.

The LLM should not be trusted to enforce authorization.

Correct architecture:

```text
User
 ↓
Authenticated application
 ↓
Authorization
 ↓
Agent
 ↓
Allowed tools
 ↓
Business operation
```

The tool/application must enforce sensitive business rules.

---

## 16. Observability

Agent execution should be observable.

Useful information:

```text
conversationId
request ID
tool name
tool duration
tool success/failure
number of tool calls
LLM latency
token usage
final outcome
```

Important distinction:

A field such as LLM-generated `toolsUsed` is useful for response formatting but should not be treated as authoritative execution telemetry.

Actual tool execution should be measured from the application's tool execution layer/logs/metrics.

---

## 17. Why Day 8 Was Important Even Without Code

Day 8 prevents a common mistake:

> Adding code just because the topic is called "advanced agent execution."

The objective was to understand the execution model before adding production controls.

The concepts established here are later useful for:

```text
P4 Multi-Agent
P4 Enterprise Automation
P4 Human-in-the-loop
P4 Guardrails
P5 Evaluation
P5 Observability
P5 Cost / latency control
```

---

## 18. Day 8 Architecture

```text
                  User Goal
                     |
                     ↓
                  Agent
                     |
                  Decide
                     |
              ┌──────┴──────┐
              ↓             ↓
           Tool          Direct Answer
              |
              ↓
           Result
              |
           Validate
              |
       ┌──────┼───────┐
       ↓      ↓       ↓
   Continue Re-plan Complete
```

---

## 19. Interview Questions

### What is agent state?

The current execution context/status of an Agent task.

### How is it different from memory?

Memory represents conversation history/context; state represents current execution progress.

### Why should Agent autonomy be bounded?

To control cost, latency, security risk and unpredictable behavior.

### Retry vs re-plan?

Retry repeats a failed operation; re-plan changes the execution strategy.

### Deterministic vs agentic workflow?

Deterministic workflows have predefined steps; agentic workflows allow the LLM to choose the next action.

### What is a hybrid workflow?

A combination of deterministic application-controlled logic and agentic decision making.

### Should we manually implement the tool loop?

Not for the core Spring AI tool-calling mechanism. Spring AI already manages it.

---

## Day 8 Summary

The key model is:

```text
Goal
 ↓
Decide
 ↓
Execute
 ↓
Observe
 ↓
Validate
 ↓
Continue / Re-plan / Stop
```

Production Agent design is not just about giving an LLM tools.

It is about controlling:

```text
Autonomy
State
Memory
Tools
Failures
Retries
Re-planning
Security
Observability
Stop conditions
```

**Status: COMPLETE — conceptual/design day**

# P3 Day 9 & Day 10 — MCP (Model Context Protocol)

## Overview

P3 Day 9 and Day 10 introduced MCP and built the first standalone MCP Server.

The goal was to understand how an AI application can connect to external capabilities through a standardized protocol, and then implement a basic MCP Server with a tool.

---

# Day 9 — MCP Fundamentals

## Objective

Understand:

- What MCP is
- Why MCP exists
- MCP Host / Client / Server
- MCP Tools
- MCP Resources
- MCP Prompts
- MCP vs normal tool calling
- MCP vs REST APIs
- Capability discovery
- Where MCP fits into OmniAI

## 1. What is MCP?

MCP stands for **Model Context Protocol**.

MCP provides a standardized way for AI applications to discover and interact with external capabilities such as:

- Tools
- Resources
- Prompts

Basic architecture:

```text
AI Application
      |
  MCP Client
      |
      | MCP
      v
  MCP Server
      |
      +---- Tools
      +---- Resources
      +---- Prompts
```

## 2. MCP Host

The MCP Host is the AI application that wants to use MCP capabilities.

In OmniAI:

```text
OmniAI / AI Copilot
        |
        +---- Agent
        +---- RAG
        +---- Native Tools
        +---- MCP Client
```

The host contains or controls the MCP Client.

## 3. MCP Client

The MCP Client communicates with an MCP Server.

```text
MCP Host
   |
MCP Client
   |
MCP Server
```

The client can discover available capabilities and request their execution/use.

## 4. MCP Server

An MCP Server exposes capabilities to MCP Clients.

```text
MCP Server
|
+-- Tools
+-- Resources
+-- Prompts
```

The MCP Server is not an agent.

It does not:

- reason about the user's request
- create an execution plan
- decide which capability should be used
- run an autonomous agent loop

Therefore:

> MCP Server = capability provider

> Agent = decision maker

## 5. MCP Tools

A Tool represents an executable capability.

Examples:

```text
getCustomer()
createTicket()
searchPolicy()
```

Conceptually:

```text
Agent
  |
MCP Client
  |
MCP Server
  |
Tool
  |
Result
```

Tools are used when an action or computation needs to be performed.

## 6. MCP Resources

A Resource represents information or context exposed through MCP.

Examples:

```text
company://policies/refund
company://docs/security
company://products
```

Simple distinction:

```text
Tool
→ perform an action / execute something

Resource
→ provide information or context
```

Resources were discussed conceptually in Day 9 and are implemented later.

## 7. MCP Prompts

Prompts are reusable prompt templates exposed by an MCP Server.

Examples:

```text
summarizeIncident
analyzeCustomerIssue
createReleaseSummary
```

Simple distinction:

```text
Tool
→ Action

Resource
→ Information / Context

Prompt
→ Reusable prompt template
```

Prompts were discussed conceptually in Day 9 and are implemented later.

## 8. MCP vs Normal Spring AI Tool Calling

Existing OmniAI tools use application-local Spring AI tools:

```text
Spring AI Agent
      |
      v
Java @Tool
      |
Business logic
```

MCP introduces a protocol boundary:

```text
AI Application
      |
MCP Client
      |
MCP Protocol
      |
MCP Server
      |
Tool
      |
Business logic
```

A local Spring AI Tool is directly part of the application.

An MCP capability is exposed through an MCP Server and can potentially be consumed by different MCP-compatible AI applications.

## 9. MCP vs REST

MCP is not a replacement for REST.

REST:

```text
Application
    |
   HTTP
    |
REST API
```

MCP:

```text
AI Application
    |
MCP Client
    |
MCP Server
    |
AI-accessible capabilities
```

An MCP Server can itself use REST APIs internally:

```text
AI Agent
   |
MCP Client
   |
MCP Server
   |
REST API
   |
Enterprise System
```

## 10. MCP vs Function Calling

Function calling is primarily a mechanism through which an LLM/application selects and invokes a tool.

MCP is a standardized protocol for connecting AI applications with external capabilities and context.

```text
Function Calling:

LLM
 |
Tool schema
 |
Application function
```

versus:

```text
MCP:

AI Application
 |
MCP Client
 |
MCP Server
 |
Capability
```

They are related but not the same thing.

## 11. Discovery

An MCP Client can discover what an MCP Server exposes.

```text
Connect to MCP Server
        |
        v
Discover capabilities
        |
        +-- Tools
        +-- Resources
        +-- Prompts
```

The client does not need to know the server's internal implementation.

## 12. USB-C Analogy

MCP can be thought of as a standardized connector/interface.

The important idea is:

> The client does not need to know the server's internal implementation as long as the MCP-facing contract remains compatible.

For example:

```text
Before:

MCP Server
  |
Java
  |
REST API A


After:

MCP Server
  |
Python
  |
REST API B
```

If the MCP-facing capability contract remains compatible, the client does not need to know about the internal implementation change.

This does not mean the client can never change. If the server changes its exposed tool name, schema, or behavior incompatibly, the client/agent may need adaptation.

## Day 9 Key Takeaways

1. MCP = Model Context Protocol.
2. MCP provides a standardized protocol for AI applications to access external capabilities.
3. MCP Host = AI application.
4. MCP Client communicates with MCP Servers.
5. MCP Server exposes capabilities.
6. MCP Tools represent actions.
7. MCP Resources provide information/context.
8. MCP Prompts provide reusable prompt templates.
9. MCP is not the same as REST.
10. MCP is not the same as function calling.
11. MCP decouples the client from the server's internal implementation when the MCP contract remains compatible.
12. The Agent remains the decision maker; the MCP Server provides capabilities.

---

# Day 10 — Build MCP Server

## Objective

Move from MCP theory to implementation.

Day 10 goal:

> Build a standalone MCP Server and expose a real MCP Tool.

The first tool created was:

```text
getCompanyPolicy
```

The tool accepts a policy name and returns structured policy information.

## 1. Project Architecture

```text
ai-copilot/
|
+-- ai-copilot-app/
|      |
|      +-- Agent
|      +-- RAG
|      +-- Native Spring AI Tools
|      +-- Memory
|
+-- ai-copilot-mcp-server/
       |
       +-- MCP Server
       +-- MCP Tools
```

The MCP Server is an independent Spring Boot application.

Current ports:

```text
ai-copilot-app
    -> 8080

ai-copilot-mcp-server
    -> 8090
```

## 2. Why Separate MCP Server?

The purpose is to demonstrate a real protocol boundary:

```text
AI Application
      |
      | MCP
      v
Separate MCP Server
```

We did not force the existing RAG implementation into the MCP Server.

The MCP Server is a separate application, so directly importing `RagRetrievalService` would create poor coupling.

The first MCP Server capability is intentionally standalone.

## 3. MCP Server Dependency

The MCP Server uses the Spring AI MCP WebMVC server starter:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

The MCP server does not need OpenAI, PGVector, JPA, or the application's RAG dependencies for this first capability.

## 4. MCP Server Configuration

Example:

```yaml
server:
  port: 8090

spring:
  application:
    name: ai-copilot-mcp-server

  ai:
    mcp:
      server:
        name: omni-ai-mcp-server
        version: 1.0.0
        protocol: STREAMABLE
        type: SYNC
```

Important configuration concepts:

- Server name
- Server version
- MCP protocol/transport configuration
- Synchronous server type

## 5. MCP Server Application

The MCP Server has its own Spring Boot entry point:

```java
@SpringBootApplication
public class AiCopilotMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(
            AiCopilotMcpServerApplication.class,
            args
        );
    }
}
```

This application runs independently on port 8090.

## 6. First MCP Tool

The first tool is:

```text
getCompanyPolicy
```

Its purpose is to retrieve a company policy by policy name.

Conceptual contract:

```text
Tool:
    getCompanyPolicy

Input:
    policyName

Output:
    CompanyPolicy
```

Example:

```text
Input:
    refund

Output:
    Company Refund Policy
    refundWindowDays = 45
    applicableCustomerTypes =
        STANDARD
        ENTERPRISE
```

## 7. MCP Tool Annotation

The tool is exposed using Spring AI's MCP annotation:

```java
@McpTool(
    name = "getCompanyPolicy",
    description = "Retrieve a company policy by policy name."
)
```

The input parameter is described using:

```java
@McpToolParam(
    description = "The policy name, for example refund",
    required = true
)
```

Conceptually:

```text
Java Method
     |
     v
MCP Tool Definition
     |
     v
MCP Server
     |
     v
MCP Client can discover/use it
```

## 8. Tool Validation

The tool validates its input.

Example:

```java
if (policyName == null || policyName.isBlank()) {
    throw new IllegalArgumentException(
        "Policy name must not be blank"
    );
}
```

Unknown policies are also rejected.

The tool follows the earlier tool-engineering principles:

- clear contract
- meaningful description
- input validation
- predictable output

## 9. Structured Output

The tool returns a structured object:

```java
public record CompanyPolicy(
    String policyName,
    int refundWindowDays,
    String[] applicableCustomerTypes
) {
}
```

Structured output is preferable to arbitrary text because the consuming application can work with explicit fields.

## 10. What the MCP Server Does

The server:

```text
Receive MCP request
       |
Validate input
       |
Execute capability
       |
Return result
```

The server does NOT:

```text
X Decide which tool should be called
X Reason about the user's overall request
X Create an agent plan
X Run an autonomous agent loop
```

Those responsibilities belong to the AI application/agent.

## 11. Day 10 Architecture

```text
                  MCP Client / Test
                         |
                    MCP Protocol
                         |
                         v
                +------------------+
                |   MCP Server     |
                |      :8090       |
                +--------+---------+
                         |
                         v
                getCompanyPolicy
                         |
                         v
                  CompanyPolicy
```

The AI Copilot Agent is not connected to this MCP Server yet.

## 12. Why Agent Testing Is Not Possible Yet

Current state:

```text
ai-copilot-app :8080
        |
        X
        |
ai-copilot-mcp-server :8090
```

There is currently no MCP Client in the AI Copilot application.

Therefore the Agent cannot use the MCP tool yet.

Next architecture:

```text
User
 |
Agent
 |
MCP Client
 |
MCP Server
 |
getCompanyPolicy
 |
Result
 |
Agent
 |
Final Answer
```

That integration comes next.

## Day 10 Key Takeaways

1. MCP Server is a capability provider.
2. MCP Server is not an Agent.
3. An MCP Server can expose Tools, Resources and Prompts.
4. Day 10 implemented the first Tool.
5. `@McpTool` exposes a server capability.
6. `@McpToolParam` describes tool input.
7. MCP tools should have clear contracts and validation.
8. Structured output is preferable to arbitrary text.
9. The MCP Server is a separate application in our project.
10. The MCP Server does not need an LLM.
11. The Agent has not been connected to MCP yet.
12. The MCP Client integration comes next.

---

# Day 9 + Day 10 Final Architecture

```text
                         OmniAI
                           |
                  +--------+--------+
                  |                 |
                  v                 v
             AI Copilot       MCP Server
               :8080             :8090
                  |                 |
             +----+----+            |
             |         |            |
           Agent      RAG      getCompanyPolicy
             |
        Native Tools
             |
          Memory


Future:

Agent
  |
MCP Client
  |
MCP Server
  |
External / Enterprise Capabilities
```

## Current Status

```text
P3 Day 9  — MCP Fundamentals       COMPLETE
P3 Day 10 — MCP Server + Tool      COMPLETE
```

## Next

**P3 Day 11 — MCP Client**

Goal:

```text
ai-copilot-app
      |
   MCP Client
      |
      v
ai-copilot-mcp-server
      |
getCompanyPolicy
```

Then the MCP capability can be made available to the Agent.

# P3 Day 11 — MCP Client Integration

## 1. Goal

Integrate the existing AI Copilot application with the standalone MCP Server.

Architecture:

```text
ai-copilot-app :8080
        |
        | MCP Client
        v
ai-copilot-mcp-server :8090
        |
        v
getCompanyPolicy
```

The goal is to allow the existing Agent to use MCP tools alongside native Spring AI tools.

---

## 2. MCP Client Responsibilities

The MCP Client is responsible for:

- Connecting to an MCP Server
- Initializing the MCP session
- Discovering server capabilities
- Discovering available tools
- Exposing discovered MCP tools to the AI application
- Invoking MCP tools when the Agent decides to use them

The MCP Client is **not** the Agent.

```text
Agent
  = decision maker

MCP Client
  = communication/protocol layer

MCP Server
  = capability provider
```

---

## 3. Existing Architecture

The project now contains two Spring Boot applications:

```text
ai-copilot/
│
├── ai-copilot-app/              :8080
│   ├── Agent
│   ├── Native Spring AI Tools
│   ├── RAG
│   ├── Memory
│   └── MCP Client
│
└── ai-copilot-mcp-server/       :8090
    └── MCP Tools
```

The MCP Server is a separate application and does not directly share Java classes with the main application.

---

## 4. MCP Client Dependency

In `ai-copilot-app/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

Spring AI provides the MCP client implementation and manages the protocol communication.

---

## 5. MCP Client Configuration

The main application uses the MCP server through Streamable HTTP:

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        name: omni-ai-client
        version: 1.0.0
        type: SYNC
        request-timeout: 20s

        streamable-http:
          connections:
            company-mcp:
              url: http://localhost:8090
```

Important:

- MCP Server runs on `8090`
- AI Copilot runs on `8080`
- `company-mcp` identifies the configured MCP connection
- Spring AI handles MCP protocol communication

---

## 6. MCP Discovery

On application startup, the MCP Client connects to the MCP Server.

The startup logs confirmed:

```text
Server response with Protocol: 2025-11-25
Capabilities:
  prompts
  resources
  tools
```

and:

```text
Implementation:
name=omni-ai-mcp-server
```

This proves:

```text
AI Copilot
    ↓
MCP Client
    ↓
MCP Server
```

connection and capability discovery are working.

---

## 7. MCP Tool Discovery

The MCP Server exposes:

```text
getCompanyPolicy
```

The MCP Client discovers this capability and makes it available to the AI application as a tool callback.

Conceptually:

```text
MCP Server
    ↓
getCompanyPolicy
    ↓
MCP Client
    ↓
ToolCallback
    ↓
ChatClient / Agent
```

Spring AI allows MCP tool callbacks/providers to be supplied to the ChatClient.

---

## 8. Native Tools vs MCP Tools

The Agent can now work with both types of tools.

### Native Spring AI Tools

```text
searchKnowledge
checkRefundEligibility
createExecutionPlan
```

### MCP Tools

```text
getCompanyPolicy
```

Architecture:

```text
                     Agent
                       |
             ┌─────────┴─────────┐
             ↓                   ↓
      Native Tools           MCP Tools
             |                   |
      Spring AI Tool        MCP Client
             |                   |
             |              MCP Server
             |                   |
             |           getCompanyPolicy
             |
      searchKnowledge
      checkRefundEligibility
      createExecutionPlan
```

The Agent does not need to know whether a capability is implemented locally or remotely. It sees callable tools.

---

## 9. Discovery != Execution

This is an important distinction.

### Discovery

Means:

> "What capabilities does this MCP Server expose?"

Example:

```text
getCompanyPolicy
```

being discovered does not mean the tool has been executed.

### Execution

Execution happens only when the Agent decides to call the tool.

```text
User request
     ↓
LLM
     ↓
decides MCP tool is required
     ↓
getCompanyPolicy
     ↓
MCP Server executes
     ↓
tool result
     ↓
LLM continues
```

Tool availability does not guarantee tool execution.

---

## 10. Agent + MCP Execution

The Agent now supports a mixed tool environment.

Example:

```text
User:
"Find the refund policy and check whether I am eligible."
```

Possible execution:

```text
Agent
  ↓
getCompanyPolicy          ← MCP
  ↓
Company Refund Policy
  ↓
checkRefundEligibility    ← Native
  ↓
Eligibility result
  ↓
Final answer
```

The LLM decides the sequence.

The tools themselves do not call each other.

---

## 11. Day 11 Implementation

The existing `AgentService` receives the MCP tool provider:

```java
private final SyncMcpToolCallbackProvider mcpTools;
```

and passes it along with the existing native tools:

```java
AgentResponse response = client.getChatResponseWithToolCall(
        SYSTEM_PROMPT,
        prompt,
        chatRequest.conversationId(),
        knowledgeSearchTool,
        plannerTool,
        mcpTools
);
```

The AI provider then supplies these capabilities to the ChatClient.

The exact tool-calling loop is managed by Spring AI.

---

## 12. Spring AI Tool Calling Loop

The application does not manually implement:

```java
while (toolCall) {
    ...
}
```

Spring AI manages the loop.

Conceptually:

```text
LLM
 ↓
Tool call
 ↓
ToolCallingManager
 ↓
Tool execution
 ↓
Tool result
 ↓
LLM
 ↓
Another tool call?
 ├── Yes → execute again
 └── No  → final answer
```

This is the same mechanism used for native tools and MCP tools.

---

## 13. Testing

### Test 1 — MCP Tool

Request:

```json
{
  "conversationId": "mcp-test-1",
  "message": "Use the company policy tool to find the refund policy and tell me the refund window for STANDARD customers.",
  "tenantId": "1"
}
```

Observed:

```text
Executing tool call: getCompanyPolicy
```

Request completed successfully.

---

### Test 2 — MCP + Native Tool

Request:

```json
{
  "conversationId": "hybrid-test-1",
  "message": "First use the company policy system to find the refund policy. Then use the refund eligibility tool to check whether a STANDARD customer who purchased on 2026-09-01 is eligible.",
  "tenantId": "1"
}
```

Observed:

```text
Executing tool call: getCompanyPolicy
Executing tool call: checkRefundEligibility
Successful execution of tool: checkRefundEligibility
```

Final result:

```text
According to the Company Refund Policy
(45-day refund window for STANDARD customers),
a purchase on 2026-09-01 is only 25 days old,
so the STANDARD customer is eligible for a refund.
```

This proves the complete hybrid flow:

```text
MCP Tool
   ↓
Native Tool
   ↓
Final Answer
```

works successfully.

---

## 14. Important Debugging Lesson

During testing, an earlier request produced:

```text
attempted to call tool 'JSON' which was not in request.tools
```

This should not be interpreted as:

> MCP Client integration is broken.

Later tests proved that MCP execution itself works.

The successful hybrid test demonstrated:

```text
MCP + Native Tools + Agent Loop + Structured Final Response
```

can work in the current application.

Therefore, the `JSON` error was conditional to a particular model/tool-call execution path rather than a basic MCP connectivity failure.

The important debugging distinction is:

```text
MCP connection failure
        ≠
MCP tool execution failure
        ≠
LLM tool-selection/request failure
```

Always inspect the exact failing layer from logs before changing the architecture.

---

## 15. Key Architecture Decision

MCP Server remains a separate application.

We do **not** directly import classes such as:

```text
RagRetrievalService
KnowledgeSearchTool
```

into the MCP Server.

Instead:

```text
AI Copilot
     |
     | MCP
     ↓
MCP Server
     |
     ↓
MCP capability
```

This preserves the protocol boundary and allows the MCP Server to evolve independently.

---

## 16. What Day 11 Added

Before Day 11:

```text
Agent
 └── Native Spring AI Tools
```

After Day 11:

```text
Agent
 ├── Native Spring AI Tools
 │    ├── searchKnowledge
 │    ├── checkRefundEligibility
 │    └── createExecutionPlan
 │
 └── MCP Tools
      └── getCompanyPolicy
```

The Agent can now operate across both local and remote capabilities.

---

## 17. Interview Takeaways

### What is an MCP Client?

A component that connects an AI application to an MCP Server and discovers/invokes MCP capabilities.

### Does MCP Client make decisions?

No.

The Agent/LLM decides which tool to use.

### Does MCP Server contain the Agent?

Not necessarily.

In this project, the MCP Server is a capability provider and has no LLM-based Agent.

### Can native and MCP tools coexist?

Yes.

Spring AI exposes both as callable tools to the Agent.

### Is tool discovery the same as tool execution?

No.

Discovery tells the application what is available. The Agent must still decide to execute a tool.

### Does the application manually implement the tool loop?

No.

Spring AI's tool-calling infrastructure manages the model → tool → result → model cycle.

---

## 18. Day 11 Summary

Day 11 established the MCP Client side of the architecture.

We now have:

```text
                AI Copilot :8080
                      |
                 MCP Client
                      |
                      ↓
              MCP Server :8090
                      |
                      ↓
               getCompanyPolicy
```

and the Agent can combine:

```text
Native Tools + MCP Tools
```

inside the same agentic execution flow.

**Day 11 Status: COMPLETE**

# Day 12 — MCP Resources & Prompts

## 2. MCP Resource

An MCP Resource represents externally addressable context/data exposed by an MCP server.

Example:

```text
company://policies/refund
```

The project exposes company refund policy through this resource.

### Resource vs RAG

```text
RAG
→ semantic retrieval from a knowledge/vector store

MCP Resource
→ known externally addressable context/data exposed through MCP
```

A Resource is useful when a client needs a specific known piece of external context.

### Resource flow

```text
MCP Client
    ↓
readResource()
    ↓
MCP Server
    ↓
CompanyPolicyResource
    ↓
Policy Content
```

For:

```text
company://policies/refund
```

the server returns:

```text
Company Refund Policy

STANDARD customers:
Refunds are allowed within 45 days of purchase.

ENTERPRISE customers:
Refunds are allowed within 60 days of purchase.
```

---

## 3. MCP Prompt

An MCP Prompt is a reusable prompt template exposed by the MCP server.

Example:

```text
refund-analysis
```

Arguments:

```text
customerType
purchaseDate
```

Example generated prompt:

```text
Analyze refund eligibility for a customer.

Customer type: STANDARD
Purchase date: 2026-09-01

Use the company refund policy.
Explain the applicable refund window
and whether the customer is eligible.
```

### Mental model

```text
Tool     = Action
Resource = Context
Prompt   = Instruction
```

Example in this project:

```text
Tool
→ getCompanyPolicy

Resource
→ company://policies/refund

Prompt
→ refund-analysis
```

---

## 4. MCP Client Consumption

The main application acts as the MCP Client.

Spring AI MCP Client handles:

- MCP protocol communication
- connection/session
- capability discovery
- tool discovery
- resource access
- prompt access

The application does not implement MCP protocol handling manually.

### Resource test

Conceptually:

```java
McpSyncClient client = mcpClients.getFirst();

McpSchema.ReadResourceRequest request =
        McpSchema.ReadResourceRequest.builder(
                "company://policies/refund"
        ).build();

McpSchema.ReadResourceResult result =
        client.readResource(request);
```

The returned content is extracted from:

```java
McpSchema.TextResourceContents
```

### Prompt test

Conceptually:

```java
McpSchema.GetPromptRequest request =
        McpSchema.GetPromptRequest.builder("refund-analysis")
                .arguments(
                        Map.of(
                                "customerType", "STANDARD",
                                "purchaseDate", "2026-09-01"
                        )
                )
                .meta(null)
                .build();

McpSchema.GetPromptResult result =
        client.getPrompt(request);
```

The prompt message is extracted from:

```java
McpSchema.TextContent
```

Both Resource and Prompt were tested successfully.

---

## 5. Day 12 Key Learning

MCP exposes different capabilities for different purposes:

```text
MCP Server
 ├── Tools
 ├── Resources
 └── Prompts
```

Do not force every MCP capability into the Agent as a Tool.

Use each according to its responsibility.

---

# Day 13 — MCP Capability Discovery

## 6. Discovery vs Execution

These are different operations.

```text
Discovery
→ What capabilities does the server expose?

Execution / Consumption
→ Use one of those capabilities.
```

Examples:

```text
listTools()
listResources()
listPrompts()
```

only discover capabilities.

They do not execute them.

---

## 7. Current MCP Capabilities

The server exposes:

```text
TOOLS
    ↓
getCompanyPolicy

RESOURCES
    ↓
company://policies/{policyName}

PROMPTS
    ↓
refund-analysis
```

This allows the client to understand what the MCP server provides.

---

## 8. Why Discovery Matters

An MCP server can expose capabilities such as:

```text
Tools:
- getCompanyPolicy
- getCustomerProfile
- createRefundRequest

Resources:
- company://policies/refund
- company://policies/returns

Prompts:
- refund-analysis
- customer-summary
```

The client can discover these capabilities from the server.

This reduces coupling to the server's internal implementation.

The client depends on the MCP-facing contract.

### Important distinction

```text
listTools()
```

showing:

```text
getCompanyPolicy
```

proves discovery works.

It does NOT prove that:

```text
getCompanyPolicy
```

was executed by the Agent.

---

## 9. Day 13 Key Learning

Remember:

```text
MCP Discovery ≠ MCP Execution
```

Discovery tells us what exists.

Execution actually uses a capability.

This distinction is also useful when debugging MCP integrations.

---

# Day 14 — MCP + Agent Integration

## 10. Objective

Day 14 connects MCP capabilities with the existing Agent workflow.

The goal is to prove the complete flow:

```text
User
  ↓
AgentService
  ↓
MCP Prompt
  ↓
MCP Resource
  ↓
Enriched Prompt
  ↓
Agent / LLM
  ↓
Tool Selection
  ↓
Business Tool
  ↓
LLM
  ↓
Final Response
```

---

## 11. MCP Context Enrichment

The MCP Prompt and Resource are retrieved before the Agent call.

Conceptually:

```text
MCP WORKFLOW INSTRUCTIONS:
<mcp prompt>

MCP POLICY CONTEXT:
<mcp resource>

USER REQUEST:
<user request>
```

This enriched prompt is passed to the Agent.

Important:

The Resource and Prompt are explicitly retrieved by the application.

They are not automatically executed by the LLM.

---

## 12. Existing Agent Tools

The Agent continues to receive normal Spring AI tools and MCP tool callbacks.

Example:

```java
client.getChatResponseWithToolCall(
        SYSTEM_PROMPT,
        enrichedPrompt,
        conversationId,
        knowledgeSearchTool,
        plannerTool,
        mcpTools
);
```

Therefore:

```text
Spring AI Tool
      +
Planner Tool
      +
Knowledge Search Tool
      +
MCP Tool Callback
      ↓
Agent
```

The existing Spring AI tool-calling loop remains responsible for tool execution.

No custom Agent execution loop was introduced.

---

## 13. Important Boundary

MCP Resource and MCP Prompt are not Tools.

```text
Resource
→ explicitly fetched by application

Prompt
→ explicitly fetched by application

Tool
→ exposed to Agent for tool calling
```

This distinction keeps the architecture simple.

---

## 14. End-to-End Example

User request:

```text
Check whether a STANDARD customer who purchased
on 2026-09-01 is eligible for a refund.
```

The MCP Prompt provides instructions for refund analysis.

The MCP Resource provides:

```text
STANDARD → 45 days
ENTERPRISE → 60 days
```

The Agent then has access to the business capability:

```text
checkRefundEligibility
```

The final answer should be based on the actual policy/context and actual business-tool result.

---

## 15. MCP + Agent Architecture

```text
                         User
                           │
                           ▼
                    ┌──────────────┐
                    │ AgentService │
                    └──────┬───────┘
                           │
                ┌──────────┴──────────┐
                │                     │
                ▼                     ▼
          MCP Prompt             MCP Resource
                │                     │
                └──────────┬──────────┘
                           ▼
                    Enriched Prompt
                           │
                           ▼
                    ┌──────────────┐
                    │  Agent / LLM │
                    └──────┬───────┘
                           │
                 ┌─────────┼─────────┐
                 ▼         ▼         ▼
              Planner   Knowledge   MCP Tools
                         Search
                           │
                           ▼
                    Business Result
                           │
                           ▼
                    Final Response
```

---

## 16. Why MCP + Agent is Useful

Without MCP:

```text
Agent
  ↓
Direct application tools
```

With MCP:

```text
Agent Application
       ↓
    MCP Client
       ↓
   MCP Server
       ↓
External capabilities
```

This allows capabilities to be separated from the Agent application.

For example:

```text
Refund MCP Server
Customer MCP Server
Finance MCP Server
HR MCP Server
```

The Agent can consume capabilities through a standardized interface.

---

## 17. MCP Server Independence

The MCP server is a separate Spring Boot application.

The main application does not directly depend on MCP server Java classes.

Preferred architecture:

```text
Main App
   ↓
MCP Client
   ↓
MCP Protocol
   ↓
MCP Server
```

The server's internal implementation can change as long as the MCP-facing contract remains compatible.

If the MCP-facing contract changes incompatibly, the client/Agent may require adaptation.

---

# Testing

## 18. Test 1 — Resource

Verify:

```text
readResource()
```

returns the refund policy.

Expected:

```text
Company Refund Policy
STANDARD → 45 days
ENTERPRISE → 60 days
```

---

## 19. Test 2 — Prompt

Verify:

```text
getPrompt("refund-analysis")
```

with:

```text
customerType = STANDARD
purchaseDate = 2026-09-01
```

returns the expected generated prompt.

---

## 20. Test 3 — Capability Discovery

Verify that the MCP server exposes:

```text
Tools:
getCompanyPolicy

Resources:
company://policies/{policyName}

Prompts:
refund-analysis
```

Remember:

```text
Discovery only proves availability.
```

It does not prove execution.

---

## 21. Test 4 — End-to-End Agent

Start:

```text
MCP Server → 8090
Main App   → 8080
```

Send an Agent request.

Expected execution flow:

```text
MCP Prompt fetch
        ↓
MCP Resource fetch
        ↓
AI Provider request
        ↓
Agent tool execution
        ↓
Final response
```

For the refund example, the important business tool is:

```text
checkRefundEligibility
```

The response should be based on actual tool output and policy context, not invented data.

---

# Production Observability

## 22. MCP Logs

Useful logs:

```text
mcp.resource.request.started
mcp.resource.request.completed
mcp.resource.request.failed

mcp.prompt.request.started
mcp.prompt.request.completed
mcp.prompt.request.failed

ai.provider.request.started
ai.provider.request.completed
ai.provider.request.failed
```

Useful fields:

```text
operation
resource/prompt name
conversationId
latencyMs
status
exception type
```

Avoid logging full prompts, responses, or sensitive business data in production by default.

---

## 23. AI Provider Metrics

The existing provider metrics cover:

```text
request count
error count
request duration
provider
model
operation
status
error code
```

This is sufficient for the current P3 scope.

No separate complicated metrics framework is required.

---

# Security

## 24. MCP Security Principles

MCP introduces an external capability boundary.

Important considerations:

### Trusted servers

Only connect to trusted MCP servers.

### Tool authorization

Not every Agent should necessarily have access to every MCP capability.

### Input validation

MCP tools must validate their inputs.

### Tenant isolation

Do not trust arbitrary LLM-generated tenant/user identifiers.

### Sensitive resources

Do not expose sensitive enterprise data through resources without authorization.

### Auditability

MCP capability usage should be observable.

---

# Common Mistakes

## 25. MCP is not the Agent

MCP does not provide reasoning.

```text
MCP Server
→ capability provider
```

The Agent/LLM performs reasoning and tool selection.

---

## 26. MCP Server should not own the Agent

Avoid unnecessarily coupling:

```text
MCP Server
   ↓
LLM
   ↓
Agent
```

The MCP server in this architecture exposes capabilities.

---

## 27. Resource is not automatically RAG

```text
Resource → addressable external context
RAG      → retrieval architecture
```

They can work together, but they are different concepts.

---

## 28. Discovery is not execution

```text
listTools()
```

does not execute a tool.

---

## 29. Do not turn every MCP capability into a Tool

Use the appropriate capability:

```text
Tool     → action
Resource → context/data
Prompt   → reusable instruction
```

---

# Design Decisions

## 30. Decisions Made

### Decision 1
MCP server is a separate application.

### Decision 2
Main application communicates through MCP Client.

### Decision 3
MCP Tools can be supplied to the Agent through MCP tool callbacks.

### Decision 4
MCP Resources are explicitly retrieved by the application.

### Decision 5
MCP Prompts are explicitly retrieved by the application.

### Decision 6
Resource and Prompt content can enrich the Agent request.

### Decision 7
Existing Spring AI Agent tool execution remains unchanged.

### Decision 8
No custom Agent execution loop was introduced.

### Decision 9
MCP capability discovery is treated separately from capability execution.

---

# Interview Takeaways

## 31. What is MCP?

MCP is a standardized protocol for connecting AI applications/Agents with external capabilities such as Tools, Resources, and Prompts.

## 32. MCP Client vs MCP Server

```text
MCP Client
→ consumes capabilities

MCP Server
→ exposes capabilities
```

## 33. What can an MCP Server expose?

```text
Tools
Resources
Prompts
```

## 34. Does MCP replace RAG?

No.

They solve different problems.

## 35. Does the Agent need the MCP server's Java implementation?

No.

The Agent/client interacts through the MCP-facing contract.

## 36. What happens if the MCP contract changes?

If a tool/resource/prompt contract changes incompatibly, the consuming client/Agent may need adaptation.

---

# Final P3 Mental Model

```text
                         ┌─────────────┐
                         │    User     │
                         └──────┬──────┘
                                ↓
                       ┌────────────────┐
                       │ Agent Service  │
                       └───────┬────────┘
                               ↓
                       ┌────────────────┐
                       │   Agent / LLM  │
                       └───────┬────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ↓                    ↓                    ↓
      Planning              Tools                Memory
                               │
                    ┌──────────┴──────────┐
                    ↓                     ↓
               App Tools             MCP Tools
                                            │
                                            ↓
                                    ┌──────────────┐
                                    │  MCP Server  │
                                    ├──────────────┤
                                    │ Tools        │
                                    │ Resources    │
                                    │ Prompts      │
                                    └──────────────┘
```

The core P3 mental model:

```text
Agent
+
Tools
+
Planning
+
Memory
+
MCP
        ↓
Enterprise AI Agent
```

And the MCP mental model:

```text
Tool     = Action
Resource = Context
Prompt   = Instruction
```