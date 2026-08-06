# runtime:events

Event Management System (EMS) module.

## Sprint 1

- `EventBus` / `InMemoryEventBus` — publish/subscribe and command dispatch
- `RuntimeEvent` / `RuntimeCommand` — event envelopes
- `SystemEvents` — well-known system event constants

## Tests

```bash
./gradlew :runtime:events:test
```
