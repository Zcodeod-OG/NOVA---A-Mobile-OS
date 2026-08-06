# NOVA Cognitive Runtime (NCR)

## Technical Design Document (TDD) v1.0

### Canonical Engineering Blueprint

**Status:** Engineering Ready (Architecture Draft)

**Purpose:** This document is the single source of truth for the NOVA engineering team. It consolidates all architectural decisions into one coherent design and replaces earlier draft sections.

---

# 1. System Vision

NOVA is a **portable Cognitive Runtime** that allows users to interact with devices through intentions rather than applications.

The runtime is:

* Offline-first
* Local-first
* Event-driven
* Model-agnostic
* Capability-based
* Deterministic
* Extensible across platforms

Android is the first execution target, not the only one.

---

# 2. High-Level Runtime Architecture

```text
User
│
├── Voice
├── Text
├── Images (future)
└── Shared Content
        │
        ▼
Conversation Service
        │
        ▼
Semantic Understanding Pipeline (SUP)
        │
        ▼
Adaptive Inference Engine (AIE)
        │
        ▼
NOVA Intermediate Representation (NIR)
        │
        ▼
Memory Platform
        │
        ▼
Reasoning Engine
        │
        ▼
Planning Service
        │
        ▼
NOVA Action Graph (NAG)
        │
        ▼
Execution Runtime (ERT)
        │
        ▼
Policy Engine
        │
        ▼
Capability Framework
        │
        ▼
Android Adapter Layer
        │
        ▼
Android OS
```

---

# 3. Core Design Principles

* Everything is event-driven.
* Every subsystem has one responsibility.
* Natural language never reaches execution directly.
* All execution is deterministic.
* AI models are replaceable.
* Android APIs are isolated.
* Capabilities abstract applications.
* Runtime owns orchestration.

---

# 4. Runtime Kernel

Responsibilities:

* Service lifecycle
* Event bus
* Dependency injection
* Configuration
* Plugin management
* Scheduling infrastructure
* Service discovery

The Kernel is infrastructure only. It contains no AI logic.

---

# 5. Event System

Everything inside NOVA communicates using events.

Examples:

* IntentReceived
* MemoryRetrieved
* GraphBuilt
* ExecutionStarted
* CapabilityFailed
* ExecutionCompleted

Every event carries:

* Trace ID
* Correlation ID
* Timestamp
* Source
* Payload

---

# 6. Semantic Understanding Pipeline (SUP)

Converts raw observations into structured meaning.

Stages:

1. Normalization
2. Parsing
3. Entity Recognition
4. Intent Classification
5. Constraint Extraction
6. Context Injection
7. Validation
8. NIR Generation

Output:

Validated NIR.

---

# 7. Adaptive Inference Engine (AIE)

Purpose:

Select the cheapest inference strategy capable of solving the current request.

Inference Tiers:

* Tier 0 — Deterministic Rules
* Tier 1 — Rule Engine
* Tier 2 — Lightweight Models
* Tier 3 — Compact LLM
* Tier 4 — Heavy Background Models

The runtime decides dynamically based on:

* Complexity
* Battery
* RAM
* Thermal state
* Latency target

---

# 8. NOVA Intermediate Representation (NIR)

Canonical representation of user intent.

Contains:

* Goal
* Entities
* Constraints
* Context
* Required Capabilities
* Confidence

Natural language is discarded after NIR generation.

---

# 9. Memory Platform

Memory is not one database.

It consists of:

* Working Memory
* Episodic Memory
* Semantic Memory
* Preference Memory
* Knowledge Graph
* Vector Index
* Context Cache

All access goes through the Memory Coordinator.

Persistent storage is handled separately.

---

# 10. Reasoning Engine

Purpose:

Determine what information is relevant.

Responsibilities:

* Resolve ambiguity
* Rank evidence
* Validate constraints
* Generate assumptions
* Produce a Reasoning Context (RC)

The Reasoning Engine never plans or executes.

---

# 11. Planning Service

Purpose:

Transform the Reasoning Context into a deterministic NOVA Action Graph (NAG).

Pipeline:

* Goal decomposition
* Task generation
* Capability mapping
* Dependency analysis
* Graph optimization
* Validation

Output:

Optimized Action Graph.

---

# 12. NOVA Action Graph (NAG)

Executable representation of work.

Properties:

* Directed acyclic graph
* Parallel execution
* Immutable
* Serializable
* Deterministic

Hierarchy:

Goal → Task → Action

---

# 13. Execution Runtime (ERT)

Purpose:

Execute Action Graphs.

Subcomponents:

* Scheduler
* Queue Manager
* Worker Pools
* Dependency Manager
* Retry Manager
* Rollback Manager
* Progress Tracker
* Resource Manager
* Logger

The runtime executes graphs but never changes their meaning.

---

# 14. Policy Engine

Purpose:

Validate actions before execution.

Responsible for:

* Permissions
* User confirmation
* Privacy policies
* Battery policies
* Enterprise rules
* Safety constraints

The planner never evaluates policy.

---

# 15. Capability Framework

Capabilities are contracts, not applications.

Examples:

* Communication
* Knowledge
* Media
* Device
* Time
* Notifications

Each capability consists of:

* Interface
* Resolver
* Provider
* Adapter

Providers (e.g., WhatsApp, SMS) are selected at runtime.

---

# 16. Android Adapter Layer

Platform-specific implementations.

Responsibilities:

* Android Intents
* Accessibility
* Content Providers
* MediaStore
* Contacts
* Calendar
* AlarmManager
* Notifications

No business logic exists here.

---

# 17. Runtime Executive (REX) *(Future Architecture)*

**MVP Status:** Not Implemented

Future orchestration layer responsible for:

* Dynamic replanning
* Admission control
* Resource arbitration
* Runtime adaptation
* Goal arbitration
* Capability failover
* Thermal optimization

Reserved to prevent planning and execution responsibilities from becoming tightly coupled.

---

# 18. Data Flow

```text
Observation
    ↓
SUP
    ↓
NIR
    ↓
Memory + Reasoning
    ↓
Planning
    ↓
NAG
    ↓
Execution Runtime
    ↓
Policy Engine
    ↓
Capability Framework
    ↓
Android Adapter
    ↓
Android OS
```

---

# 19. MVP Scope (2 Weeks)

### Included

* Conversation Service
* SUP
* AIE (basic tier selection)
* NIR
* Working + Semantic Memory
* Vector search
* Planner
* NAG
* Execution Runtime
* Policy Engine (basic)
* Capability Framework
* Android Adapter Layer
* WhatsApp
* Calendar
* Alarms
* File search
* Photos
* Contacts

### Deferred

* Runtime Executive
* Plugin SDK
* Multi-device runtime
* Long-term Goal Manager
* Reflection Engine
* Advanced Learning Engine
* Distributed execution
* Multi-agent support

---

# 20. Recommended Technology Stack

### Mobile

* Kotlin
* Jetpack Compose
* Android SDK
* WorkManager
* Accessibility Service
* Room

### AI

* ONNX Runtime
* llama.cpp
* MediaPipe (where useful)
* Whisper.cpp (or equivalent offline ASR)
* Small embedding model

### Search

* SQLite + Room
* HNSW vector index
* Knowledge Graph layer

### Runtime

* Kotlin Coroutines
* Flow
* StateFlow
* Dependency Injection (Koin or Hilt)
* Event Bus

### Build

* Gradle
* JUnit
* MockK
* GitHub Actions

---

# 21. Guiding Principle

> **The Runtime is the product.**

The AI model, Android integrations, and applications are replaceable components. The runtime architecture, deterministic execution pipeline, and capability abstraction are the enduring core of NOVA.
