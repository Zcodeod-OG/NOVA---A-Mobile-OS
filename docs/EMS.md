# NOVA Cognitive Runtime (NCR)

# Document 08 — Event & Messaging Specification (EMS) v1.0

**Status:** Engineering Ready

**Owner:** Runtime Infrastructure Team

**Dependencies:**

* TDD v1.0
* Interface & API Specification
* Module Specification Pack

---

# 1. Purpose

The Event & Messaging System is the communication backbone of NOVA.

Every runtime module communicates through events rather than direct method calls whenever asynchronous coordination is required.

Goals:

* Loose coupling
* High observability
* Replayability
* Deterministic execution
* Easy debugging
* Platform independence

This document defines the runtime event protocol, event lifecycle, delivery guarantees, tracing model, and messaging architecture.

---

# 2. Design Principles

1. Events are immutable.
2. Events are append-only.
3. Every event has a globally unique identifier.
4. Every event belongs to exactly one trace.
5. Modules publish events—they never manipulate another module's internal state.
6. Business logic must not exist inside the Event Bus.
7. Commands request work; events announce facts.

---

# 3. Messaging Architecture

```text
                  Runtime Kernel
                        │
                Runtime Event Bus
                        │
──────────────────────────────────────────────
Conversation
SUP
Inference
Memory
Reasoning
Planner
Execution
Policy
Capability
Android Adapter
──────────────────────────────────────────────
```

The Event Bus is infrastructure only.

It performs no routing decisions based on business logic.

---

# 4. Communication Model

NOVA supports two communication patterns.

### Commands

Purpose:

Request another module to perform work.

Examples:

* ExecuteGraph
* RetrieveMemory
* StartConversation

Commands expect exactly one handler.

---

### Events

Purpose:

Announce that something has already happened.

Examples:

* GraphBuilt
* MemoryRetrieved
* ExecutionCompleted

Events may have zero, one, or many subscribers.

---

# 5. Event Lifecycle

```text
Create
   ↓
Validate
   ↓
Publish
   ↓
Dispatch
   ↓
Handle
   ↓
Archive
```

Events are never modified after publication.

---

# 6. Standard Event Envelope

Every runtime event follows the same schema.

```kotlin
RuntimeEvent

eventId: UUID

traceId: UUID

correlationId: UUID?

timestamp: Instant

sourceModule: Module

eventType: String

priority: Priority

version: Int

payload: Any
```

No module-specific metadata may exist outside the payload.

---

# 7. Event Categories

## Conversation

* ConversationStarted
* ObservationReceived
* ConversationInterrupted
* ConversationCompleted

---

## Understanding

* ObservationNormalized
* IntentDetected
* EntityResolved
* ConstraintExtracted
* NIRGenerated

---

## Memory

* MemoryQueryRequested
* MemoryRetrieved
* MemoryStored
* MemoryUpdated
* MemoryDeleted

---

## Reasoning

* ReasoningStarted
* EvidenceCollected
* AmbiguityResolved
* ReasoningCompleted

---

## Planning

* PlanningStarted
* TasksGenerated
* GraphBuilt
* GraphOptimized
* PlanningCompleted

---

## Execution

* ExecutionStarted
* NodeScheduled
* NodeRunning
* NodeCompleted
* NodeFailed
* GraphCompleted
* GraphFailed

---

## Capability

* CapabilityResolved
* CapabilitySelected
* CapabilityExecuted
* CapabilityUnavailable

---

## Android

* PermissionGranted
* PermissionDenied
* IntentCompleted
* AccessibilityFailed

---

## System

* RuntimeStarted
* RuntimeReady
* RuntimeStopping
* RuntimeShutdown

---

# 8. Event Priorities

| Priority | Usage                               |
| -------- | ----------------------------------- |
| Critical | Runtime failure, security, shutdown |
| High     | Active conversation, execution      |
| Normal   | Planning, memory, reasoning         |
| Low      | Background indexing                 |
| Idle     | Maintenance                         |

Priority affects scheduling only.

It never changes semantics.

---

# 9. Delivery Guarantees

| Event Type     | Guarantee     |
| -------------- | ------------- |
| Commands       | Exactly Once  |
| Runtime Events | At Least Once |
| Telemetry      | Best Effort   |

Duplicate event handling must be idempotent.

---

# 10. Event Ordering

Ordering is guaranteed only within a Trace ID.

Different traces may execute concurrently.

Example

```
Trace A

Observation

↓

Planning

↓

Execution

↓

Completed
```

Independent traces may interleave.

---

# 11. Correlation Model

Every user interaction creates:

```
Trace ID
```

Every child event contains:

```
Correlation ID
```

Example

```
Conversation

↓

Planning

↓

Execution

↓

Capability

↓

Android
```

All belong to one trace.

---

# 12. Event Bus Responsibilities

The Event Bus SHALL:

* Register subscribers
* Dispatch events
* Queue asynchronous events
* Retry failed delivery
* Maintain event ordering
* Collect metrics

The Event Bus SHALL NOT:

* Execute business logic
* Filter payloads
* Transform events

---

# 13. Synchronous vs Asynchronous Events

### Synchronous

Used when the caller cannot continue.

Examples:

* Permission check
* Capability resolution

---

### Asynchronous

Used for long-running operations.

Examples:

* File indexing
* Embedding generation
* Execution progress

---

# 14. Failure Handling

If a subscriber fails:

1. Retry according to policy.
2. Publish DeliveryFailed.
3. Continue dispatch to remaining subscribers.

One failing subscriber must not stop event propagation.

---

# 15. Event Replay

The runtime supports replay for debugging.

Replay sources:

* Execution History
* Conversation Trace
* Runtime Logs

Replay never re-executes Android actions.

It reconstructs runtime behavior only.

---

# 16. Event Versioning

Every event carries:

* schemaVersion
* payloadVersion

Breaking payload changes require a new major version.

Consumers must reject incompatible versions.

---

# 17. Observability

Every event records:

* Publish time
* Dispatch time
* Processing duration
* Subscriber count
* Success/failure

Metrics feed the Runtime Monitor.

---

# 18. Event Naming Convention

Pattern:

```
<Noun><PastTenseVerb>
```

Examples:

* MemoryRetrieved
* GraphBuilt
* CapabilityResolved

Commands use imperative verbs.

Examples:

* ExecuteGraph
* RetrieveMemory
* StorePreference

---

# 19. Security

Sensitive payloads are marked confidential.

Rules:

* Never log raw microphone audio.
* Never log document contents.
* Never log private messages.
* Personally identifiable data must be redacted from debug logs.

---

# 20. Event Package Structure

```
runtime/events/

├── bus/
├── commands/
├── conversation/
├── understanding/
├── memory/
├── reasoning/
├── planner/
├── execution/
├── capability/
├── android/
├── system/
├── models/
└── serializers/
```

---

# 21. Sequence Example

User:

> "Send Rahul the latest railway presentation."

Runtime Flow:

```
ConversationStarted

↓

ObservationReceived

↓

NIRGenerated

↓

MemoryRetrieved

↓

ReasoningCompleted

↓

GraphBuilt

↓

ExecutionStarted

↓

CapabilityResolved

↓

IntentCompleted

↓

GraphCompleted

↓

ConversationCompleted
```

Every transition is observable through the Event Bus.

---

# 22. MVP Scope

Included:

* In-memory Event Bus
* Typed runtime events
* Trace IDs
* Correlation IDs
* Priority queues
* Event replay (development)
* Structured logging

Deferred:

* Distributed messaging
* Cross-device event synchronization
* Persistent event sourcing
* External message brokers

---

# 23. Engineering Risks

* Event explosion in complex workflows.
* Circular event chains.
* Large payloads.
* Subscriber starvation.
* Debugging asynchronous races.

Mitigation:

* Strict ownership.
* Payload size limits.
* Trace visualization tools.
* Event linting during development.
* Idempotent subscribers.

---

# 24. Acceptance Criteria

The Event & Messaging System is complete when:

* Every runtime module communicates through typed events or commands.
* Every event includes trace and correlation identifiers.
* Event ordering is deterministic within a trace.
* Event replay reconstructs runtime execution.
* Business logic is absent from the Event Bus.
* All events are versioned and documented.
* Delivery guarantees are implemented according to this specification.
