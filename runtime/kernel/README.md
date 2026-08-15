# runtime:kernel

Sprint 1 implementation of the NOVA Runtime Kernel (TDD §4, MSP §4).

## Components

- `RuntimeKernel` — bootstrap/shutdown facade with config validation
- `ServiceRegistry` / `DefaultServiceRegistry` — service registration with lifecycle listeners
- `LifecycleManager` / `DefaultLifecycleManager` — runtime lifecycle
- `ConfigurationManager` / `InMemoryConfigurationManager` — runtime config
- `ConfigurationValidator` / `DefaultConfigurationValidator` — kernel config validation
- `ModuleRegistry` / `DefaultModuleRegistry` — module registration
- `TraceContextHolder` / `TraceIdGenerator` / `TraceContextElement` — trace propagation
- `kernelModule` — Koin DI module

## Tests

```bash
./gradlew :runtime:kernel:test
```
