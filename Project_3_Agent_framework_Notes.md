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

## Day 4 — Agent Fundamentals

### Goal

Understand the difference between a normal chatbot, a tool-enabled chatbot, and an agent.

### Agent Concept

A normal chatbot:

```text
User
 ↓
LLM
 ↓
Answer
```

A tool-enabled interaction:

```text
User
 ↓
LLM
 ↓
Tool
 ↓
Tool Result
 ↓
LLM
 ↓
Answer
```

An agent:

```text
User
 ↓
LLM
 ↓
Action
 ↓
Observation
 ↓
LLM
 ↓
Action / Final Answer
```

The key difference is that the LLM can decide whether an action/tool is required.

### Existing P2 Capability Reused by P3

The `searchKnowledge` tool reuses the existing P2 RAG infrastructure:

```text
Agent
 ↓
searchKnowledge
 ↓
RagRetrievalService
 ↓
QueryTransformer
 ↓
MultiQueryGenerator
 ↓
MultiQueryRetriever
 ↓
Re-ranking
 ↓
PGVector
 ↓
Citations
```

P2 provides the knowledge capability; P3 adds agentic orchestration.

### Tool Registration

Spring AI exposes a tool to the model through the `@Tool` annotation and registers it with the `ChatClient`:

```java
.tools(knowledgeSearchTool)
```

The model decides when the tool is needed.

### Day 4 Testing

Verified:

- General Java question → direct answer without tool execution.
- Internal refund-policy question → `searchKnowledge` executed.
- Mixed/internal question → tool used and unavailable information was not invented.

The application logs confirmed actual tool execution.

---

# Day 5 — Agent Loop & Multi-Step Execution

## Goal

Move from single-tool execution to multi-step agent execution.

Single-tool flow:

```text
User
 ↓
LLM
 ↓
Tool
 ↓
Tool Result
 ↓
LLM
 ↓
Final Answer
```

Multi-step flow:

```text
User
 ↓
LLM
 ↓
Tool 1
 ↓
Tool Result
 ↓
LLM decides next action
 ↓
Tool 2
 ↓
Tool Result
 ↓
LLM
 ↓
Final Answer
```

## Agent Loop

The fundamental pattern is:

```text
Action → Observation → Decision
```

- **Action:** LLM requests a tool call.
- **Observation:** Application executes the tool and returns the result.
- **Decision:** LLM evaluates the result and either calls another tool or produces the final answer.

### Important

Tool 1 does **not** directly call Tool 2.

The LLM is the bridge:

```text
Tool 1
 ↓
Result
 ↓
LLM
 ↓
Tool 2
```

## Second Tool

A deterministic business tool was added:

```text
checkRefundEligibility
```

Parameters:

```text
policyName
customerType
purchaseDate
```

The tool determines the current date in the application and calculates eligibility.

For the test policy:

```text
STANDARD   → 45 days
ENTERPRISE → 60 days
```

Example:

```text
policyName    = Refund Policy
customerType  = STANDARD
purchaseDate  = 2026-08-20
currentDate   = 2026-09-21
```

Result:

```text
eligible = true
daysSincePurchase = 32
refundWindowDays = 45
```

## Multi-Step Agent Flow

For a refund eligibility request:

```text
User
 ↓
LLM
 ↓
searchKnowledge("refund policy")
 ↓
P2 RAG retrieves policy
 ↓
Tool result returned to LLM
 ↓
LLM decides eligibility check is required
 ↓
checkRefundEligibility(...)
 ↓
Eligibility result returned to LLM
 ↓
LLM
 ↓
Final AgentResponse
```

## Spring AI Tool Loop

No manual `while` loop was implemented.

Multiple tools are exposed:

```java
.tools(
    knowledgeSearchTool,
    refundEligibilityTool
)
```

Spring AI handles the tool-calling lifecycle:

```text
LLM
 ↓
Tool call
 ↓
Tool execution
 ↓
Tool result
 ↓
LLM again
 ↓
Next tool OR final answer
```

## Structured Agent Response

The final agent response uses `AgentResponse` instead of a simple `ChatResponse`.

The response contains:

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

The response therefore provides:

- `message` — final agent answer
- `toolsUsed` — tools involved in the interaction
- `sources` — supporting knowledge sources

The response is mapped using:

```java
.call()
.entity(AgentResponse.class);
```

## Testing

### Test 1 — General question

```text
"What is Java?"
```

Result:

```text
Direct LLM response
No tool required
```

### Test 2 — Knowledge question

```text
"What is our refund policy?"
```

Result:

```text
searchKnowledge
 ↓
P2 RAG
 ↓
Final answer
```

### Test 3 — Multi-step question

```text
"I am a standard customer and purchased something on
2026-08-20. Check whether I am eligible for a refund
according to our refund policy."
```

Result:

```text
searchKnowledge
 ↓
checkRefundEligibility
 ↓
Final AgentResponse
```

Application logs confirmed that `searchKnowledge` completed first and `checkRefundEligibility` executed afterwards in the same agent request.

## Key Learning

### P2

```text
RAG → Retrieve enterprise knowledge
```

### P3

```text
LLM → Decide → Tool → Observe → Decide again → Tool → Answer
```

The major capability added across Days 4–5 is the transition from simple tool availability to **agent-driven multi-step tool execution**.

## Completion

- [x] Agent fundamentals understood
- [x] Agent vs normal chatbot understood
- [x] Existing P2 RAG reused as an agent tool
- [x] Tool registration and execution verified
- [x] Agent loop understood
- [x] Action → Observation → Decision understood
- [x] Second tool implemented
- [x] Multiple tools exposed to the agent
- [x] Multi-tool chaining verified
- [x] Structured `AgentResponse` implemented
- [x] Sources included in the response
- [x] End-to-end testing completed

**P3 Day 4 + Day 5 — COMPLETE**
