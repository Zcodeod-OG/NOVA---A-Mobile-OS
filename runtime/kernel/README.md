# runtime:kernel

Sprint 1 implementation of the NOVA Runtime Kernel (TDD §4, MSP §4).

## Components

- `RuntimeKernel` — bootstrap/shutdown facade
- `ServiceRegistry` / `DefaultServiceRegistry` — service registration
- `LifecycleManager` / `DefaultLifecycleManager` — runtime lifecycle
- `ConfigurationManager` / `InMemoryConfigurationManager` — runtime config
- `ModuleRegistry` / `DefaultModuleRegistry` — module registration
- `TraceContextHolder` / `TraceIdGenerator` — trace propagation
- `kernelModule` — Koin DI module

## Tests

```bash
./gradlew :runtime:kernel:test
```
