# NOVA Cognitive Runtime (NCR)

# Document 05 — Android Integration Specification (AIS) v1.0

**Status:** Engineering Ready

**Owner:** Android Platform Team

**Dependencies:**

* TDD v1.0
* Interface & API Specification
* Data Platform Specification

---

# 1. Purpose

This document defines how the portable NOVA Cognitive Runtime integrates with Android.

The Android layer is responsible only for:

* Accessing Android APIs
* Managing permissions
* Monitoring system events
* Executing capability requests
* Providing platform services

No reasoning, planning, or AI logic exists in this layer.

---

# 2. Design Principles

* Runtime code must remain platform-independent.
* Android-specific code is isolated.
* Every Android API is wrapped behind an adapter.
* Permissions are requested only when required.
* Background work follows Android lifecycle rules.
* Foreground responsiveness always takes priority.

---

# 3. Android Architecture

```text id="z6x8gq"
NOVA Runtime
      │
Capability Framework
      │
Android Adapter Layer
      │
───────────────────────────
Android Services
Android Intents
MediaStore
Contacts
Calendar
AlarmManager
NotificationManager
Accessibility
WorkManager
Storage Access Framework
───────────────────────────
      │
Android OS
```

---

# 4. Android Modules

## 4.1 Accessibility Service

Purpose:

Automate applications that do not expose suitable public APIs.

Uses:

* UI traversal
* Click actions
* Text input
* Scroll
* Window detection

Used For:

* WhatsApp automation
* Legacy applications
* UI fallback

Accessibility is always the last-choice provider after official APIs.

---

## 4.2 Intent Adapter

Purpose:

Wrap Android Intent APIs.

Supported Operations:

* Open application
* Share content
* View document
* Dial number
* Launch settings
* Open URL

Returns structured execution results to the runtime.

---

## 4.3 MediaStore Adapter

Purpose:

Read local media.

Supports:

* Images
* Videos
* Audio

Provides:

* URI
* Metadata
* Thumbnails
* Timestamps

---

## 4.4 Contacts Adapter

Purpose:

Provide read-only and write access to contacts.

Operations:

* Search
* Retrieve
* Create
* Update

---

## 4.5 Calendar Adapter

Purpose:

Calendar capability implementation.

Operations:

* Read events
* Create events
* Modify events
* Delete events

---

## 4.6 Alarm Adapter

Purpose:

Wrap AlarmManager.

Operations:

* Create alarm
* Cancel alarm
* Update alarm

---

## 4.7 Notification Adapter

Purpose:

Observe and create notifications.

Operations:

* Read notifications (permission required)
* Publish notifications
* Dismiss notifications (where permitted)

---

## 4.8 Storage Access Adapter

Purpose:

Provide secure file access.

Supports:

* Documents
* Downloads
* External storage
* SAF URIs

No raw filesystem assumptions.

---

# 5. Android Services

The application contains the following services.

## Foreground Runtime Service

Maintains:

* Active conversations
* Execution Runtime
* Progress notifications

Runs only while required.

---

## Accessibility Service

Activated only after explicit user permission.

Handles supported UI automation.

---

## WorkManager Workers

Background workers:

* File Indexer
* Embedding Generator
* OCR
* Graph Builder
* Cleanup

Heavy work is deferred when appropriate.

---

# 6. Permission Model

Permissions are grouped by capability.

| Capability    | Android Permission                         |
| ------------- | ------------------------------------------ |
| Contacts      | READ_CONTACTS                              |
| Calendar      | READ/WRITE_CALENDAR                        |
| Notifications | POST_NOTIFICATIONS / Notification Listener |
| Media         | READ_MEDIA_*                               |
| Files         | Storage Access Framework                   |
| Microphone    | RECORD_AUDIO                               |
| Accessibility | Accessibility Service                      |

Permissions are requested just-in-time.

---

# 7. Capability Mapping

| Capability    | Preferred Implementation | Fallback      |
| ------------- | ------------------------ | ------------- |
| Communication | Android Intent           | Accessibility |
| Calendar      | Calendar Provider        | None          |
| Alarm         | AlarmManager             | None          |
| Knowledge     | MediaStore + SAF         | File APIs     |
| Device        | Android SDK              | None          |

---

# 8. Background Execution Strategy

Priority Levels

1. User interaction
2. Execution Runtime
3. Index updates
4. Embedding generation
5. Graph maintenance

The runtime pauses lower-priority work under:

* Low battery
* Thermal throttling
* High CPU load

---

# 9. Lifecycle Integration

## Application Launch

```text id="x5f7dn"
Application Start
      ↓
Runtime Initialization
      ↓
Capability Discovery
      ↓
Permission Check
      ↓
Background Workers Registration
      ↓
Ready
```

---

## Application Background

```text id="tovgnh"
Conversation Ends
      ↓
Persist Working State
      ↓
Stop Foreground Service
      ↓
Allow Scheduled Workers
```

---

## Device Reboot

```text id="n1p0bp"
BOOT_COMPLETED
      ↓
Register Workers
      ↓
Rebuild Runtime State
      ↓
Resume Indexing (if required)
```

---

# 10. Security

Android integration follows least-privilege principles.

Rules:

* Never request permissions proactively.
* Never duplicate user files.
* Never export runtime databases.
* Never expose Android objects above the Adapter layer.
* All sensitive runtime data remains encrypted.

---

# 11. Error Handling

Platform failures are translated into structured runtime errors.

Examples:

* Permission denied
* App unavailable
* Intent failed
* Accessibility timeout
* Content provider unavailable

Android exceptions never propagate into the runtime.

---

# 12. Performance Targets

| Operation            | Target  |
| -------------------- | ------- |
| Launch adapter       | <20 ms  |
| Intent dispatch      | <100 ms |
| Contact lookup       | <50 ms  |
| Calendar lookup      | <100 ms |
| Notification publish | <50 ms  |
| Media query          | <150 ms |

---

# 13. MVP Scope

Included:

* Intents
* Accessibility
* MediaStore
* Contacts
* Calendar
* AlarmManager
* Notifications
* WorkManager
* Storage Access Framework

Deferred:

* Cross-device execution
* Android Auto
* Wear OS
* Nearby devices
* Multi-user support

---

# 14. Engineering Risks

* Android version fragmentation
* Accessibility UI changes
* OEM background restrictions
* Permission revocation
* Battery optimization policies

Mitigation:

* Adapter abstraction
* Capability fallbacks
* Runtime health monitoring
* Graceful degradation

---

# 15. Acceptance Criteria

This document is complete when:

* Every Android API is accessed only through an adapter.
* Every capability has at least one Android provider.
* Background work complies with Android lifecycle constraints.
* Runtime remains platform-independent.
* Android failures are isolated from runtime logic.
* Permission flow is fully implemented and testable.
