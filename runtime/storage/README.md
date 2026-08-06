# runtime:storage

Data Platform module for NOVA Cognitive Runtime (DPS / DSS).

## Components

- **NovaDatabase** — Room SQLite database (v2)
- **StorageCoordinatorImpl** — single entry point for storage operations
- **Repositories** — typed access layer above DAOs
- **LruStorageCache** — in-memory cache with importance-aware eviction
- **VectorIndex** / **KnowledgeGraphStore** — placeholder interfaces (no AI/index implementation)

## Schema versions

| Version | Contents |
|---------|----------|
| 1 | `documents` only (Sprint 0 stub) |
| 2 | Full MVP schema per DSS §16 |

Migrations: `Migration_1_2` (forward-only, no destructive fallback).

## DI

```kotlin
startKoin {
    androidContext(app)
    modules(runtimeModule, storageModule(app))
}
```

## Build

Requires Android SDK (`local.properties` with `sdk.dir`).

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
./gradlew :runtime:storage:compileDebugKotlin :runtime:storage:test
```
