# NOVA — Local AI Runtime for Android

Sprint 0 + Sprint 1: multi-module Gradle project with interface stubs and a fully implemented runtime kernel.

See `docs/` for canonical architecture (PRD, TDD, MSP, EMS, DSS, DPS, AIS).

## Modules

- `:app` — Android application shell with Koin bootstrap
- `:runtime:models` — Shared domain models and error types
- `:runtime:utils` — Logging and trace utilities
- `:runtime:events` — Event bus (EMS)
- `:runtime:kernel` — Runtime kernel infrastructure (Sprint 1)
- `:runtime:*` — Cognitive runtime module stubs (Sprint 0)

## Build

```bash
./gradlew assemble
./gradlew :runtime:kernel:test :runtime:events:test
```

Requires JDK 17+.

## DI

**Koin** (per TDD §20). Kernel infrastructure is wired via `com.nova.runtime.kernel.di.kernelModule`; Sprint 0 service stubs are in `com.nova.runtime.app.di.sprint0StubsModule`.
