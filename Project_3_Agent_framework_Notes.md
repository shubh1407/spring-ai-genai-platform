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
