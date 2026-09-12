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
