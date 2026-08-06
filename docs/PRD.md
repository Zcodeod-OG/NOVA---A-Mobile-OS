# NOVA

## Product Requirements Document (PRD) v2.0

**Document ID:** NOVA-PRD-001

**Status:** Draft v2.0

**Last Updated:** August 2026

**Owner:** Product

**Dependencies:**

* Document 00 – Product Vision

---

# 1. Executive Summary

NOVA is a **Local AI Runtime for Android** that transforms smartphones from application-centric systems into intent-centric systems.

Rather than asking users to navigate applications manually, NOVA interprets natural language, plans the required steps, retrieves relevant personal knowledge from the device, and executes approved actions through a secure tool framework.

Unlike cloud-based assistants, NOVA performs all reasoning, retrieval, indexing, and planning locally whenever technically possible.

The MVP focuses on becoming the fastest and most private way to interact with a user's own device.

---

# 2. Product Definition

## Product Category

Local AI Runtime

## Elevator Pitch

> "Android manages apps. NOVA manages intentions."

---

# 3. Product Goals

## Primary Goals

### G1 — Natural Interaction

Allow users to operate their phone through natural language using either voice or text.

---

### G2 — Universal Local Search

Provide semantic search across:

* Photos
* Videos
* PDFs
* Audio recordings
* Downloads
* Documents
* Notes (where accessible)
* Contacts
* Calendar events

Users should never need to remember filenames.

---

### G3 — Intelligent Planning

Support multi-step requests.

Example:

> "Send Rahul the latest railway presentation."

Execution Plan:

1. Search local storage.
2. Rank candidate files.
3. Confirm selection.
4. Launch WhatsApp.
5. Open Rahul's conversation.
6. Attach document.
7. Ask for final confirmation.
8. Send.

---

### G4 — Local Memory

Maintain semantic understanding of the user's digital content.

The system should organize information into concepts rather than folders.

---

### G5 — Safe Execution

All actions requiring modification of user data must be:

* explainable
* reversible where possible
* permission-aware
* user-approved

---

# 4. Success Metrics

## Product KPIs

### Search

* Top-3 semantic retrieval accuracy ≥ 90% on indexed content.

### Planning

* ≥ 95% successful execution of supported single-step tasks.
* ≥ 85% successful execution of supported multi-step tasks.

### Latency

* Voice response begins in under 2 seconds.
* Search completes in under 500 ms after indexing.
* Common tool actions execute in under 3 seconds.

### Privacy

* 100% of personal data processing remains local for offline features.

---

# 5. Target Users

## Primary

* Developers
* Students
* Researchers
* Founders
* Privacy-conscious users
* Productivity enthusiasts

## Secondary

* Professionals
* Small businesses
* Creators

---

# 6. MVP Scope

## Included

### Conversation

* Voice input
* Text input
* Multi-turn conversations
* Context retention during a session

---

### Planning

* Intent recognition
* Multi-step planning
* Tool selection
* Confirmation generation

---

### Retrieval

* Files
* Photos
* PDFs
* Audio transcripts
* Calendar
* Contacts

---

### Device Actions

* Calendar events
* Alarms
* Timers
* File opening
* Document sharing
* WhatsApp automation
* Contact lookup

---

### Intelligence

* OCR indexing
* Image embeddings
* Semantic file search
* Local vector database
* Memory graph (basic)

---

# 7. Explicitly Out of Scope (MVP)

The following are intentionally excluded from Version 1:

* Email automation
* Browser automation
* Cross-device synchronization
* Smart home control
* Third-party plugin marketplace
* Continuous wake-word detection
* Multi-agent orchestration
* Autonomous execution without confirmation

---

# 8. Functional Requirements

## FR-001 — Voice Interface

Priority: Critical

### Description

The system shall accept spoken commands entirely offline.

### Acceptance Criteria

* Speech recognition works without internet.
* Supports English initially.
* Automatically detects end-of-speech.
* Converts speech to planner input.

---

## FR-002 — Text Interface

Priority: Critical

Users may issue commands using text.

Behavior must be identical to voice.

---

## FR-003 — Planner

Priority: Critical

The planner shall:

* understand intent
* decompose complex requests
* identify required tools
* resolve dependencies
* request clarification when confidence is low

The planner **must never execute tools directly**.

---

## FR-004 — Tool Execution

Priority: Critical

The execution engine shall:

* validate permissions
* execute approved plans
* capture execution results
* report failures
* expose structured outputs to the planner

---

## FR-005 — Semantic Search

Priority: Critical

Users may retrieve content without filenames.

Example queries:

* "Passport photo."
* "Whiteboard from yesterday."
* "Latest railway presentation."
* "Invoice from Amazon."

---

## FR-006 — Messaging

Priority: High

Support:

* contact resolution
* attachment selection
* WhatsApp automation
* confirmation before send

---

## FR-007 — Calendar

Priority: High

Support:

* create event
* modify event
* delete event
* search events

---

## FR-008 — Alarm

Priority: High

Support:

* create
* delete
* recurring alarms
* labels

---

## FR-009 — Memory Graph (MVP)

The runtime shall begin constructing relationships between:

* files
* contacts
* projects
* conversations
* calendar events

This graph is local and continuously refined.

---

# 9. User Stories

### Student

> I want to find the lecture slide I used last Tuesday without remembering its filename.

---

### Founder

> I want to send the latest investor deck without opening multiple applications.

---

### Researcher

> I want to search every paper discussing diffusion transformers regardless of where I stored them.

---

### Professional

> I want to schedule meetings naturally without navigating the Calendar UI.

---

# 10. Core User Journey

```text
User speaks
      │
Speech Recognition
      │
Intent Parsing
      │
Planner
      │
Memory + Retrieval
      │
Execution Plan
      │
User Confirmation
      │
Tool Execution
      │
Completion Summary
```

---

# 11. Product Principles

Every shipped feature must satisfy all of the following:

* Reduces interaction friction.
* Preserves user privacy.
* Works offline whenever feasible.
* Can be extended through the Runtime.
* Has measurable acceptance criteria.
* Does not tightly couple to a specific LLM.

---

# 12. Risks

| Risk                                 | Mitigation                                                   |
| ------------------------------------ | ------------------------------------------------------------ |
| Android background restrictions      | Use WorkManager, foreground services, and deferred indexing. |
| Accessibility changes                | Build abstraction layer around UI automation.                |
| Model performance on low-end devices | Support multiple quantized model profiles.                   |
| Large media libraries                | Incremental indexing with background scheduling.             |

---

# 13. Open Questions (To Be Resolved in TDD)

* Planner architecture
* Memory Engine design
* Knowledge Graph schema
* Tool protocol
* Embedding pipeline
* Indexing scheduler
* Runtime lifecycle
* Permission model
* Conversation state machine
* Plugin API
* Battery optimization strategy

---

# Exit Criteria for PRD Approval

The PRD is approved only if:

* Product scope is frozen.
* All MVP features have measurable acceptance criteria.
* Engineering estimates are feasible within the roadmap.
* Every feature has a documented owner.
* No unresolved architectural dependency blocks implementation.

Once approved, implementation details move exclusively into the Technical Design Document.
