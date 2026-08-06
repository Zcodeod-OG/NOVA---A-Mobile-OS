# NOVA Cognitive Runtime (NCR)

# Document 07 — Database Schema Specification (DSS) v1.0

**Status:** Engineering Ready

**Owner:** Runtime & Data Platform Team

**Purpose:** Define the persistent data model for NOVA. This document is the canonical reference for all database entities, relationships, indexing strategy, and migration rules.

---

# 1. Scope

This specification covers:

* SQLite schema
* Room entities
* Foreign-key relationships
* Indexing strategy
* Data ownership
* Migration policy

It does **not** define:

* Vector index implementation
* Knowledge graph internals
* Runtime memory
* Cache structures

Those are separate runtime systems.

---

# 2. Storage Architecture

```text id="iqmz4j"
                 Storage Coordinator
                        │
      ┌─────────────────┼──────────────────┐
      │                 │                  │
 SQLite (Room)     Vector Index     Knowledge Graph
      │
 Android Storage (Files remain here)
```

SQLite stores metadata only.

Large binaries are never copied into the database.

---

# 3. Entity Relationship Diagram

```text id="nhtgj6"
Project
   │
   ├──────── Documents
   │
   ├──────── Photos
   │
   ├──────── Notes
   │
   └──────── Sessions

Contact ───── ExecutionHistory

Document ─── Embedding

Photo ───── Embedding
```

---

# 4. Core Tables

## 4.1 Documents

Purpose:

Metadata for indexed documents.

Columns

| Column      | Type    |
| ----------- | ------- |
| id          | UUID    |
| path        | TEXT    |
| name        | TEXT    |
| extension   | TEXT    |
| mimeType    | TEXT    |
| size        | LONG    |
| checksum    | TEXT    |
| createdAt   | LONG    |
| modifiedAt  | LONG    |
| indexedAt   | LONG    |
| projectId   | UUID?   |
| embeddingId | UUID?   |
| importance  | INTEGER |

Indexes

* path
* modifiedAt
* checksum

---

## 4.2 Photos

Purpose

Photo metadata.

Columns

* id
* uri
* takenAt
* width
* height
* latitude
* longitude
* ocrText
* embeddingId
* favorite

Indexes

* takenAt
* uri

---

## 4.3 Contacts

Columns

* id
* displayName
* phone
* email
* lastInteraction
* importance

Indexes

* displayName
* phone

---

## 4.4 Projects

Purpose

Logical grouping.

Columns

* id
* title
* description
* createdAt
* updatedAt
* status

Projects are runtime concepts, not Android folders.

---

## 4.5 Preferences

Purpose

Persistent user preferences.

Columns

* key
* value
* confidence
* updatedAt

Examples

* preferred_messenger
* default_language
* preferred_alarm_app

---

## 4.6 Sessions

Purpose

Conversation metadata.

Columns

* sessionId
* traceId
* startedAt
* endedAt
* state

Conversation text is not permanently stored by default.

---

## 4.7 ExecutionHistory

Purpose

Execution auditing.

Columns

* graphId
* traceId
* status
* duration
* retryCount
* completedNodes
* failedNodes

---

## 4.8 Embeddings

Purpose

Reference table.

Columns

* embeddingId
* objectType
* objectId
* modelVersion
* dimension
* createdAt

Vectors themselves live in the Vector Index.

---

# 5. Foreign Keys

```text id="2c8pm0"
Document.projectId
    →
Project.id

Document.embeddingId
    →
Embedding.embeddingId

Photo.embeddingId
    →
Embedding.embeddingId
```

Foreign keys are enabled.

Cascade deletes are avoided unless explicitly required.

---

# 6. Room Package Layout

```text id="9u8g1g"
storage/

├── entities/
├── dao/
├── migrations/
├── converters/
├── database/
└── repository/
```

---

# 7. DAO Contracts

Every entity exposes:

```kotlin id="mflh83"
insert()

update()

delete()

getById()

observe()

search()
```

Business logic is prohibited inside DAOs.

---

# 8. Repository Layer

Repositories sit above Room.

Example

```text id="blhczr"
ConversationRepository

ProjectRepository

DocumentRepository

ContactRepository

PreferenceRepository
```

Repositories communicate with the Storage Coordinator, not directly with runtime modules.

---

# 9. Migration Strategy

Every schema change:

* Increment version
* Add migration
* Preserve user data
* Validate checksum
* Run integrity tests

Destructive migrations are disabled in production.

---

# 10. Indexing Strategy

Primary indexes:

* id
* modifiedAt
* projectId
* displayName
* checksum

Composite indexes:

* (projectId, modifiedAt)
* (displayName, lastInteraction)

Indexes should optimize retrieval, not writes.

---

# 11. Transactions

Transactions are required for:

* Project creation
* Multi-table updates
* Execution history
* Index synchronization

Long-running indexing never blocks user-facing transactions.

---

# 12. Data Retention

Persistent:

* Preferences
* Metadata
* Execution history
* Projects

Temporary:

* Active sessions
* Intermediate indexing state

Cache is not persisted.

---

# 13. Backup Strategy

Included in backup:

* SQLite database

Excluded:

* Embeddings (can be regenerated)
* Cache
* Temporary runtime state

Knowledge graph may be rebuilt if necessary.

---

# 14. Performance Targets

| Operation          | Target  |
| ------------------ | ------- |
| Primary key lookup | <5 ms   |
| Indexed search     | <20 ms  |
| Transaction commit | <50 ms  |
| Database startup   | <150 ms |

---

# 15. Engineering Rules

* UUID primary keys everywhere.
* No nullable IDs.
* Metadata only.
* Immutable IDs.
* One owner per dataset.
* No duplicated metadata.
* Database never stores runtime objects.

---

# 16. MVP Schema

Tables included in MVP:

* Documents
* Photos
* Contacts
* Projects
* Sessions
* Preferences
* ExecutionHistory
* Embeddings

Deferred:

* Goal table
* Plugin registry
* Reflection history
* Multi-device synchronization
* Analytics snapshots

---

# 17. Acceptance Criteria

The schema is approved when:

* All entities have defined ownership.
* Every foreign key is documented.
* Required indexes exist.
* Migrations are versioned.
* Room entities map one-to-one with the schema.
* No runtime module bypasses the Repository layer.
