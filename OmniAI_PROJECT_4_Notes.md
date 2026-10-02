# OmniAI — Project 4 Learning README
## Days 1–2: Multi-Agent Foundation

> **Project:** OmniAI — Enterprise AI Intelligence & Automation Platform  
> **Phase:** P4 — Multi-source Intelligence + Verification  
> **Purpose:** Build the flagship orchestration layer while learning the P4 roadmap topics inside the same production system.

---

## 1. Important Scope Correction

The master roadmap defines **Project 4** as:

> **Multi-source Intelligence + Verification**

Its locked topics are:

1. Multi-source reasoning
2. Structured data + RAG
3. SQL/data analysis
4. Read-only SQL boundary
5. Schema awareness
6. Query validation
7. SQL injection protection
8. Result limits
9. Query timeout
10. Cross-source reasoning
11. Conflicting sources
12. Source authority
13. Evidence extraction
14. Claim/evidence separation
15. Evidence verification
16. Contradiction detection
17. Confidence
18. Revision loop
19. Guarded agentic reasoning
20. Human-in-the-loop
21. Approval/rejection
22. Audit trail
23. Report generation

The roadmap itself does **not** define a literal Day 1–15 breakdown. The 15-day structure used for OmniAI is an execution plan derived from these locked topics and the flagship goal.

### Critical architecture principle

The **multi-agent/supervisor architecture is the flagship architectural layer**.

The original P4 roadmap topics remain the learning scope and are implemented inside that architecture.

We must not replace the P4 roadmap with a generic multi-agent course.

---

# 2. P4 — 15-Day Execution Structure

## Phase 1 — Multi-Agent Foundation

### Day 1 — Multi-Agent Architecture & Flagship Design
- Why a multi-agent architecture is needed for OmniAI
- Supervisor vs specialist
- Specialist boundaries
- Shared enterprise task
- High-level orchestration architecture
- KNOWLEDGE / DATA / REPORT specialist roles
- Where P4 multi-source reasoning will eventually fit
- Architecture decision for OmniAI

**Status:** COMPLETE

---

### Day 2 — Supervisor / Orchestrator

**Goal:** Convert an enterprise request into a structured specialist decision.

Concepts:
- Supervisor responsibility
- Understand → Decide → Delegate boundary
- Supervisor vs Planner
- LLM decision vs application control
- Structured decision output
- One-specialist routing decision
- Why Supervisor should not execute tools
- Why existing P3 Agent must remain intact

Implementation completed:
- `SpecialistsType`
- `SupervisorDecision`
- Dedicated `SUPERVISOR_SYSTEM_PROMPT`
- Existing `AgentService` supervisor path
- Existing generic structured-output capability in `AiProviderClient`
- Supervisor tested independently
- Existing P3 Agent flow preserved

### Verified routing

| Request | Result |
|---|---|
| Refund policy | KNOWLEDGE |
| Transaction analysis | DATA |
| Executive summary | REPORT |
| Churn + data + support documentation | DATA |

### Day 2 boundary

The Supervisor currently:

```text
User Request
    ↓
Supervisor
    ↓
Understand
    ↓
Choose specialist
    ↓
SupervisorDecision
    ↓
STOP
```

It does **not** execute the specialist.

**Status:** COMPLETE

> Important: Specialist execution/routing is NOT part of Day 2 completion. It belongs to later orchestration work.

---

## Phase 2 — Orchestration

### Day 3 — Specialist Agent Design
- What a specialist agent is
- Specialist responsibility boundaries
- KNOWLEDGE specialist
- DATA specialist
- REPORT specialist
- Inputs/outputs
- Specialist contracts
- How specialists differ from the Supervisor
- Design implementation before coding

### Day 4 — Agent-to-Agent Delegation
- Supervisor delegates a task
- Application-controlled delegation
- Specialist invocation
- Specialist result returned to Supervisor
- Delegation failure
- Specialist timeout/error handling
- No uncontrolled agent-to-agent loops

### Day 5 — Shared Task State
- Why agents need shared state
- Task ID / execution context
- Supervisor decision
- Specialist result
- Intermediate findings
- Evidence/results
- State lifecycle

### Day 6 — Sequential vs Parallel Execution
- Sequential specialist execution
- Parallel execution
- When parallelism is safe
- Dependencies between specialists
- Aggregating results
- Failure handling in parallel work

### Day 7 — Conditional Routing
- Routing based on task state/result
- Multi-step orchestration
- Re-routing
- Guarded loops
- Stop conditions
- Bounded autonomy

---

## Phase 3 — Multi-Source Intelligence

### Day 8 — Structured Data + RAG
- Combining structured and unstructured sources
- Data + document retrieval
- Source roles
- Cross-source context
- Why one source is insufficient for enterprise questions

### Day 9 — Read-Only SQL/Data Agent
- SQL/data analysis
- Read-only boundary
- Schema awareness
- Safe query generation
- Result handling

### Day 10 — SQL Safety
- Query validation
- SQL injection protection
- Result limits
- Query timeout
- Dangerous query rejection
- Failure handling

---

## Phase 4 — Verification

### Day 11 — Cross-Source Reasoning
- Combining SQL results, support tickets and documentation
- Source relationships
- Conflicting sources
- Source authority
- Evidence extraction

### Day 12 — Claims + Evidence
- Claim/evidence separation
- Evidence verification
- Supporting evidence
- Missing evidence
- Citation/evidence mapping

### Day 13 — Contradiction + Confidence + Revision
- Contradiction detection
- Confidence
- Revision loop
- Accept / revise / flag
- Guarded reasoning

---

# 3. Day 14 — Human-in-the-Loop + Audit + Report

This is the **first of the two final flagship days**.

### Goal

Turn the verified reasoning pipeline into an enterprise-controlled output process.

### Roadmap topics covered

- Human-in-the-loop
- Approval/rejection
- Audit trail
- Report generation

### Concept

The system should not blindly turn an AI conclusion into an enterprise action.

Instead:

```text
Reasoning
   ↓
Verification
   ↓
Confidence / Issues
   ↓
Human Review
   ├── APPROVE
   ├── REJECT
   └── REQUEST REVISION
   ↓
Final Report / Controlled Action
```

### What we will design

#### 1. Human approval boundary
Define exactly when a human must review.

Examples:
- Low-confidence conclusion
- Contradictory evidence
- Sensitive enterprise action
- Material business recommendation

#### 2. Approval states

Conceptually:

```text
PENDING_REVIEW
APPROVED
REJECTED
REVISION_REQUIRED
```

The exact implementation will be decided during Day 14 rather than assumed beforehand.

#### 3. Audit trail

We need to be able to answer:

- What request was received?
- Which plan was created?
- Which specialist ran?
- Which tools/sources were used?
- What evidence was found?
- What conclusion was produced?
- What confidence/verification result existed?
- Who approved/rejected it?
- What final output was generated?

#### 4. Report generation

The final report should separate:

```text
Executive conclusion
↓
Key findings
↓
Evidence
↓
Conflicting evidence / limitations
↓
Confidence
↓
Recommended next steps
```

The report must not hide uncertainty.

### Day 14 architecture target

```text
Supervisor
    ↓
Specialists
    ↓
Cross-source reasoning
    ↓
Verification
    ↓
Confidence
    ↓
Human Review
    ↓
Audit Trail
    ↓
Executive Report
```

### Day 14 implementation philosophy

**No code dump.**

We will first:
1. Understand the approval boundary
2. Decide the state model
3. Decide what must be audited
4. Decide report inputs/outputs
5. Map exact classes/interfaces/files
6. You propose the implementation
7. Review it
8. Only then write code

---

# 4. Day 15 — End-to-End OmniAI Flagship

### Goal

Integrate everything into the final P4 flagship flow.

The roadmap's final P4 demo is:

> Analyze why customer churn increased this month. Use customer data, support tickets and product documentation. Verify the findings and produce an executive report.

### Target architecture

```text
Complex Enterprise Request
          ↓
        Task
          ↓
       Planner
          ↓
     Supervisor
          ↓
   ┌──────┼───────┐
   ↓      ↓       ↓
Knowledge Data   Report
   ↓      ↓       ↓
 RAG     SQL   Synthesis
   └──────┼───────┘
          ↓
 Cross-Source Reasoning
          ↓
 Evidence Extraction
          ↓
 Evidence Verification
          ↓
 Contradiction Detection
          ↓
 Confidence
          ↓
 Revision / Flag
          ↓
 Human Approval
          ↓
 Executive Report
```

### Day 15 verification

We will prove the complete flow using the flagship churn scenario.

Expected path:

```text
Task
 ↓
Plan
 ↓
Knowledge retrieval
 ↓
SQL/data analysis
 ↓
Tool execution
 ↓
Cross-source reasoning
 ↓
Evidence verification
 ↓
Report
```

### Day 15 production checks

We will also verify:

- Failure in one specialist
- Missing source
- Conflicting source
- Invalid/unsafe SQL
- Empty retrieval
- Low-confidence result
- Verification failure
- Human rejection
- Revision path
- Audit trail completeness
- Final report correctness

### Day 15 final outcome

P4 should end with a demonstrable **OmniAI enterprise intelligence workflow**, not isolated toy agents.

---

# 5. What Day 2 Did NOT Include

This is important because we previously crossed the boundary.

Day 2 did **NOT** include:

- Specialist invocation
- Agent-to-agent delegation
- Specialist router implementation
- Shared task state
- Sequential/parallel execution
- Conditional routing

Those belong to later orchestration days.

Day 2 only established:

```text
REQUEST
   ↓
SUPERVISOR
   ↓
STRUCTURED DECISION
   ↓
KNOWLEDGE / DATA / REPORT
```

---

# 6. Current P4 Status

| Day | Topic | Status |
|---|---|---|
| 1 | Multi-Agent Architecture & Flagship Design | COMPLETE |
| 2 | Supervisor / Orchestrator | COMPLETE |
| 3 | Specialist Agent Design | NEXT |
| 4 | Agent-to-Agent Delegation | PLANNED |
| 5 | Shared Task State | PLANNED |
| 6 | Sequential vs Parallel | PLANNED |
| 7 | Conditional Routing | PLANNED |
| 8 | Structured Data + RAG | PLANNED |
| 9 | Read-Only SQL/Data Agent | PLANNED |
| 10 | SQL Safety | PLANNED |
| 11 | Cross-Source Reasoning | PLANNED |
| 12 | Claims + Evidence | PLANNED |
| 13 | Contradiction + Confidence + Revision | PLANNED |
| 14 | HITL + Approval + Audit + Report | PLANNED |
| 15 | End-to-End OmniAI Flagship | PLANNED |

---

# 7. Learning Rule Going Forward

For every remaining P4 day:

```text
Concept
   ↓
Why
   ↓
How it works
   ↓
Architecture
   ↓
Options / Trade-offs
   ↓
Decision
   ↓
Implementation responsibilities
   ↓
YOU propose implementation
   ↓
Review
   ↓
Actual code only when requested
   ↓
Testing
   ↓
Debugging
   ↓
Production thinking
   ↓
Interview questions
   ↓
Completion
```

**No premature code.**

---

# 8. P4 Final Goal

By the end of P4, OmniAI should be able to take:

> “Analyze why customer churn increased this month using customer data, support tickets and product documentation. Verify the findings and produce an executive report.”

and execute a controlled enterprise workflow involving:

```text
Task Understanding
      ↓
Planning
      ↓
Supervision
      ↓
Specialists
      ↓
RAG
      ↓
Structured Data / SQL
      ↓
Tool Execution
      ↓
Cross-Source Reasoning
      ↓
Evidence
      ↓
Verification
      ↓
Confidence
      ↓
Revision / Flag
      ↓
Human Approval
      ↓
Audit
      ↓
Executive Report
```

This is the P4 → OmniAI flagship bridge.
