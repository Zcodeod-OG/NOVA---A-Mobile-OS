# NOVA Cognitive Runtime (NCR)

# Document 03 — Interface & API Specification (IAS) v1.0

**Status:** Engineering Ready

**Purpose:** Define every public interface, event contract, request/response model, and module boundary used by the NOVA Cognitive Runtime. This document is the only source of truth for inter-module communication.

---

# 1. Design Principles

Every interface in NOVA must satisfy these rules:

* Stable and versioned.
* Strongly typed.
* Independent of Android APIs.
* Independent of any LLM implementation.
* Serializable where appropriate.
* Backward compatible across minor versions.
* Testable without Android hardware.

---

# 2. Runtime Module Contracts

| Module                    | Accepts           | Produces            |
| ------------------------- | ----------------- | ------------------- |
| Conversation Service      | Observation       | ConversationRequest |
| SUP                       | Observation       | NIR                 |
| Adaptive Inference Engine | InferenceRequest  | InferenceResult     |
| Memory Platform           | MemoryQuery       | MemoryResult        |
| Reasoning Engine          | ReasoningRequest  | ReasoningContext    |
| Planning Service          | PlanningRequest   | PlanningResult      |
| Execution Runtime         | ExecutionRequest  | ExecutionResult     |
| Policy Engine             | PolicyRequest     | PolicyDecision      |
| Capability Framework      | CapabilityRequest | CapabilityResult    |

No module may bypass another module's public contract.

---

# 3. Core Runtime Objects

The runtime standardizes a small set of canonical data models.

## Observation

Represents any external input.

Fields:

* id
* timestamp
* sessionId
* traceId
* modality
* payload
* metadata

---

## NIR (NOVA Intermediate Representation)

Represents validated user intent.

Fields:

* version
* goal
* entities
* constraints
* context
* requiredCapabilities
* confidence

---

## ReasoningContext

Contains enriched context for planning.

Fields:

* resolvedEntities
* evidence
* assumptions
* recommendations
* confidence

---

## NAG (NOVA Action Graph)

Represents executable work.

Contains:

* graph metadata
* task hierarchy
* action nodes
* dependencies
* execution policies

---

# 4. Event Contract

Every runtime event follows the same schema.

Fields:

* eventId
* traceId
* correlationId
* timestamp
* source
* destination
* priority
* eventType
* payload

Events are immutable.

---

# 5. Event Categories

Conversation

* ConversationStarted
* TurnReceived
* ConversationCompleted

Planning

* PlanningStarted
* GraphBuilt
* PlanningCompleted

Execution

* ExecutionStarted
* NodeCompleted
* ExecutionCompleted

Capability

* CapabilityResolved
* CapabilityExecuted
* CapabilityFailed

Memory

* MemoryStored
* MemoryRetrieved
* MemoryUpdated

System

* RuntimeReady
* RuntimeError
* ShutdownRequested

---

# 6. Capability Interface

Every capability implements the same public contract.

Required operations:

* execute()
* health()
* supportedOperations()
* requiredPermissions()
* version()

Capabilities never expose platform-specific objects.

---

# 7. Execution Contracts

Every Action Node declares:

* unique identifier
* action type
* inputs
* outputs
* dependencies
* timeout
* retry policy
* rollback policy
* execution priority

Execution Runtime guarantees that node semantics are preserved regardless of scheduling.

---

# 8. Memory Query Contract

Every memory request includes:

* query type
* query parameters
* retrieval strategy
* maximum results
* timeout
* traceId

Memory Platform decides which internal memory systems participate.

---

# 9. Error Model

All modules return structured errors.

Every error contains:

* code
* category
* severity
* recoverability
* user-visible message
* diagnostics

No raw exceptions cross module boundaries.

---

# 10. Versioning Strategy

Every public contract is independently versioned.

Breaking changes require a major version increment.

Minor versions must remain backward compatible.

---

# 11. Observability

Every public operation automatically records:

* latency
* success/failure
* traceId
* resource usage
* execution duration

These metrics remain local unless the user explicitly exports diagnostics.

---

# 12. Testing Requirements

Every interface must support:

* unit testing
* integration testing
* mock implementations
* deterministic replay

Any interface that cannot be mocked should be redesigned.

---

# 13. Engineering Acceptance Criteria

Before implementation begins, every interface must satisfy:

* Single responsibility.
* No circular dependencies.
* Stable public contract.
* Complete documentation.
* Defined error behavior.
* Version identifier.
* Test coverage target.

---

# 14. Relationship to Other Documents

* **PRD v2.0** defines product requirements.
* **TDD v1.0** defines runtime architecture.
* **IAS v1.0** defines communication contracts.
* Future documents (Sprint Plan, Module Specs, Database Schema) must conform to this specification.
