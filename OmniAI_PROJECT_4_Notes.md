# OmniAI — P4 Progress README
## Days 1–3: Dynamic Multi-Agent Foundation

**Project:** OmniAI — Enterprise AI Intelligence & Automation Platform  
**Phase:** P4 — Multi-Agent + Enterprise AI Automation & Orchestration  
**Progress:** Day 3 complete  
**Daily learning time:** ~45 minutes/day  
**Status:** Working implementation

---

## 1. What P4 is building

P4 moves OmniAI from a single-agent/tool-oriented system toward a dynamic multi-agent orchestration model.

```text
User Input
   ↓
Central Supervisor / Orchestrator
   ↓
Understand + Decide
   ↓
Agent / Specialist
   ↓
Agent Result
   ↓
Central Supervisor
   ↓
Decide Again
   ├── Delegate to another specialist
   ├── Delegate again
   └── Finish
```

The key change is that the Supervisor is no longer making one static routing decision and stopping. It can receive specialist results and make another decision based on the updated context.

---

# 2. P4 Day 1 — Multi-Agent Concept & Architecture

## Goal

Understand the architecture required for a dynamic multi-agent system before changing the implementation.

## Core concept

The central component is the **Supervisor / Orchestrator**.

The Supervisor is responsible for:

- understanding the user request
- deciding whether work needs to be delegated
- selecting the appropriate specialist
- defining the task for that specialist
- receiving the specialist result
- analysing the updated state
- deciding what should happen next
- eventually finishing the workflow

Specialists perform their assigned domain task.

The Supervisor does not directly perform specialist business work.

## Responsibility separation

```text
Supervisor   → Decision-making
Specialist   → Domain execution
Orchestrator → Workflow control
```

## Day 1 outcome

The architecture and responsibility boundaries were established before implementation.

---

# 3. P4 Day 2 — Dynamic Supervisor Decision

## Goal

Make the Supervisor explicitly decide whether to:

1. delegate work to a specialist, or
2. finish the workflow.

## Action model

A new `Action` enum was introduced:

```java
public enum Action {
    DELEGATE,
    FINISH
}
```

`SupervisorDecision` was extended with the action.

The decision contains:

```text
Action
Specialist Type
Task
Reason
Original Message
```

## Supervisor behaviour

### DELEGATE

```text
action = DELEGATE
specialistsType = selected specialist
task = task for specialist
reason = why delegation is required
```

### FINISH

```text
action = FINISH
```

The Supervisor system prompt was updated so the LLM returns this structured decision.

## Day 2 outcome

The Supervisor changed from a simple routing decision into a workflow-control decision.

---

# 4. P4 Day 3 — Supervisor → Specialist → Supervisor Loop

## Goal

Implement the first real dynamic orchestration loop.

Required flow:

```text
Supervisor
    ↓
Delegate
    ↓
Specialist
    ↓
SpecialistResult
    ↓
Shared Context
    ↓
Supervisor again
```

## SupervisorContext

A `SupervisorContext` was introduced to carry workflow state:

```text
SupervisorContext
├── originalRequest
├── conversationId
└── previousResults
```

`previousResults` stores specialist results produced during the current orchestration.

## Orchestrator responsibility

The Orchestrator now:

1. creates the initial Supervisor context
2. calls the Supervisor
3. checks the Supervisor action
4. delegates to the selected specialist when action is `DELEGATE`
5. receives the `SpecialistResult`
6. adds the result to `previousResults`
7. calls the Supervisor again
8. repeats until the Supervisor returns `FINISH`

## Final Day 3 flow

```text
                User Request
                     ↓
                Orchestrator
                     ↓
               Supervisor #1
                     ↓
                 DELEGATE
                     ↓
              SpecialistService
                     ↓
               Specialist Agent
                     ↓
             SpecialistResult
                     ↓
          previousResults.add(...)
                     ↓
               Supervisor #2
                     ↓
          ┌──────────┴──────────┐
          ↓                     ↓
      DELEGATE                FINISH
          ↓                     ↓
   Another Specialist         End
          ↓
       Result
          ↓
   Supervisor Again
```

---

# 5. Previous Specialist Results are sent to Supervisor

A critical Day 3 change was ensuring that the second Supervisor call is not blind.

The Supervisor receives:

```text
Original User Request
+
Previous Specialist Results
```

Prompt structure:

```text
User message:
{message}

Previous Specialist Results:
{previousResults}
```

The `SpecialistResult` objects are converted into readable text before being inserted into the prompt.

Example:

```text
Specialist: KNOWLEDGE
Status: SUCCESS
Result: Standard customers can request a refund within 45 days of purchase.
Sources: company-policy.txt
```

This gives the Supervisor the context required to decide what should happen next.

---

# 6. Working Example

The current implementation successfully produced:

```json
{
  "specialistsType": "KNOWLEDGE",
  "status": "SUCCESS",
  "result": "Standard customers can request a refund within 45 days of purchase.",
  "sources": [
    {
      "source": "company-policy.txt",
      "fileName": "company-policy.txt",
      "fileType": "txt",
      "pageNumber": "",
      "chunkIndex": "0"
    }
  ]
}
```

This proves the first complete delegation path:

```text
User
 ↓
Supervisor
 ↓
KNOWLEDGE Specialist
 ↓
Knowledge Agent
 ↓
SpecialistResult
```

The result is then stored in `SupervisorContext.previousResults` and supplied to the next Supervisor decision.

---

# 7. Component Responsibilities

## AgentController

Receives the external request and starts orchestration.

Existing P3 endpoints remain unchanged.

## Orchestrator

Owns the P4 workflow loop.

Responsibilities:

- create context
- call Supervisor
- delegate
- collect results
- call Supervisor again
- stop when Supervisor chooses `FINISH`

## AgentService

Owns Supervisor interaction.

Responsibilities:

- build Supervisor prompt
- include original request
- include previous specialist results
- request structured `SupervisorDecision`
- use the existing exception translation framework

## SpecialistService

Owns specialist execution.

Responsibilities:

- resolve the specialist
- construct `SpecialistRequest`
- execute the selected specialist
- return `SpecialistResult`

## Supervisor

Owns dynamic decision-making.

Responsibilities:

- analyse current request and previous results
- choose `DELEGATE` or `FINISH`
- select specialist when delegating
- define the next task

---

# 8. What is NOT implemented yet

Days 1–3 intentionally establish the foundation.

Still remaining in P4:

- richer shared agent state
- explicit agent-to-agent communication
- dynamic multi-agent delegation across multiple specialists
- sequential multi-agent workflows
- parallel execution
- conditional routing
- re-planning
- failure/recovery
- multi-source reasoning
- contradiction detection
- verification
- human-in-the-loop approval
- audit workflows
- final end-to-end P4 hardening

These will be implemented incrementally in the remaining P4 days.

---

# 9. Current P4 Architecture

```text
                    ┌──────────────────────┐
                    │      User Input      │
                    └──────────┬───────────┘
                               ↓
                    ┌──────────────────────┐
                    │     Orchestrator     │
                    │  Workflow Controller │
                    └──────────┬───────────┘
                               ↓
                    ┌──────────────────────┐
                    │      Supervisor      │
                    │  Decide Next Action  │
                    └──────────┬───────────┘
                               ↓
                         DELEGATE / FINISH
                               ↓
                    ┌──────────────────────┐
                    │  SpecialistService   │
                    └──────────┬───────────┘
                               ↓
              ┌────────────────┼────────────────┐
              ↓                ↓                ↓
        KNOWLEDGE            DATA             REPORT
          Agent              Agent             Agent
              └────────────────┼────────────────┘
                               ↓
                    ┌──────────────────────┐
                    │  SpecialistResult    │
                    └──────────┬───────────┘
                               ↓
                    ┌──────────────────────┐
                    │ SupervisorContext    │
                    │ previousResults      │
                    └──────────┬───────────┘
                               ↓
                         Supervisor Again
```

---

# 10. Key Learning from Days 1–3

### Day 1

**Multi-agent architecture starts with responsibility separation.**

Supervisor decides.  
Specialist executes.  
Orchestrator controls the workflow.

### Day 2

**A Supervisor needs an explicit action model.**

`DELEGATE` and `FINISH` turn a static routing decision into workflow control.

### Day 3

**A dynamic agent system needs feedback.**

The Supervisor cannot make subsequent decisions intelligently unless it receives the results of previous work.

Therefore:

```text
Decision
   ↓
Execution
   ↓
Result
   ↓
Context
   ↓
New Decision
```

is the fundamental P4 loop.

---

# 11. P4 Progress

```text
Day 1  ✅ Multi-agent concept & architecture
Day 2  ✅ Dynamic Supervisor decision
Day 3  ✅ Supervisor → Specialist → Result → Supervisor
Day 4  ⏳ Shared agent state/context
Day 5  ⏳ Agent-to-agent communication
Day 6  ⏳ Dynamic task delegation
Day 7  ⏳ Sequential multi-agent workflow
Day 8  ⏳ Parallel agent execution
Day 9  ⏳ Conditional routing
Day 10 ⏳ Re-planning + failure/recovery
Day 11 ⏳ Multi-source reasoning
Day 12 ⏳ Verification + contradiction
Day 13 ⏳ HITL + approval + audit
Day 14 ⏳ Full dynamic multi-agent demo + hardening
```

---

# 12. Definition of Done — Days 1–3

- [x] Multi-agent architecture understood
- [x] Supervisor owns decision-making
- [x] Orchestrator owns workflow control
- [x] Specialist execution remains isolated
- [x] Supervisor can return `DELEGATE`
- [x] Supervisor can return `FINISH`
- [x] `SupervisorContext` carries previous results
- [x] Specialist results are stored in context
- [x] Previous results are sent back to Supervisor
- [x] Supervisor can make another decision after specialist execution
- [x] Real KNOWLEDGE specialist execution works
- [x] `SpecialistResult` is produced successfully
- [x] Sources are preserved in the result
- [x] Existing P1/P2 exception framework remains intact
- [x] Existing P3 request model remains intact

---

# 13. Current State

**P4 Days 1–3: COMPLETE**

The system has moved from:

```text
Request → One Supervisor Decision → One Specialist
```

to:

```text
Request
  ↓
Supervisor
  ↓
Specialist
  ↓
Result
  ↓
Supervisor
  ↓
Next Decision
```

This is the foundation required for the remaining dynamic multi-agent capabilities in P4.
