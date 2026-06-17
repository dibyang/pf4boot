# Runtime Safety Phase 3

## Problem

Several runtime safety boundaries are not explicit enough:

- The plugin administration controller mutates state with GET, which can be triggered accidentally by caches, crawlers, or cross-site requests. Enabling the controller also lacks a default security warning.
- The lock scope for `start`, `stop`, `restart`, and `reload` is spread across internal fragments. Concurrent operations on the same plugin can interleave start, stop, and reload work.
- Dynamic bean registration into root, platform, application, or the MVC main context relies on Spring's default duplicate-name behavior instead of a framework-level conflict policy.
- Cleanup for classloaders, scheduled tasks, MVC mappings, shared beans, and `ApplicationContextProvider` lacks stable observable assertions.

## Affected Modules

- `pf4boot-api`: add dynamic bean-name conflict policy configuration and `ApplicationContextProvider` observation methods.
- `pf4boot-core`: tighten lifecycle operation locking, apply conflict policy before shared bean registration, and expose test observations for scheduled tasks and shared beans.
- `pf4boot-web-starter`: remove the old administration controller and keep only MVC dynamic registration and resource mapping; protect MVC dynamic bean registration from conflicts.
- `pf4boot-management-starter`: becomes the only HTTP management module for plugin administration through `/pf4boot/admin/**`.
- `docs/design/en`: keep this English design in sync with the Chinese canonical document.

## Proposed Design

### Administration Controller

The old `pf4boot-web-starter` administration controller has been removed. The historical `/auto-start`, `/list`, and `/{pluginId}/...` endpoints are no longer kept. Plugin HTTP management is provided only by `pf4boot-management-starter`, disabled by default, and must be enabled explicitly with `spring.pf4boot.management.http.enabled=true`.

The only retained plugin management endpoints use `/pf4boot/admin` as the default base path:

- `GET /pf4boot/admin/plugins`
- `GET /pf4boot/admin/plugins/{pluginId}`
- `POST /pf4boot/admin/plugins/{pluginId}/enable`
- `DELETE /pf4boot/admin/plugins/{pluginId}/enable`
- `POST /pf4boot/admin/plugins/{pluginId}/start`
- `POST /pf4boot/admin/plugins/{pluginId}/stop`
- `POST /pf4boot/admin/plugins/{pluginId}/restart`
- `POST /pf4boot/admin/plugins/{pluginId}/reload`

Deployment, rollback, and JPA reload management are also exposed only through `/pf4boot/admin/**`, with authorization, idempotency, audit, rate limiting, and write-request security checks applied consistently.

### Lifecycle Lock Scope

Reuse the existing `stateLock` as the lifecycle operation lock. It now covers state reads, dependency traversal, and resource cleanup for `startPlugins`, `stopPlugins`, `startPlugin`, `stopPlugin`, `restartPlugin`, `reloadPlugin`, and `reloadPlugins`. The inner start/stop critical sections continue to use the same reentrant lock, so recursive dependency start and dependent stop do not self-deadlock.

This favors runtime consistency: concurrent operations for the same plugin are serialized, and dependency-chain changes complete as one lifecycle transaction. If cross-plugin concurrency becomes necessary later, a plugin-id lock can be added with deterministic dependency-graph lock ordering.

### Bean Name Conflict Policy

Add `DynamicBeanConflictPolicy`:

- `REJECT`: default. If an existing singleton or bean definition uses the same name, registration fails with context, scope, group, and beanName details.
- `REPLACE`: log before replacement, destroy the existing singleton, remove the existing bean definition, then register the new bean.

Namespacing is not the default in this phase because the current public registration API returns `void`; callers would not know the final bean name and stop-time unregistering could miss the actual name. Namespacing should be designed later with a compatible API extension.

### Cleanup Observability

Add testable assertions for:

- wrapper classloader closure after unload/reload;
- scheduled task count returning to zero after stop;
- MVC mappings and dynamic interceptors returning to zero after unregister;
- shared beans disappearing from root/platform/application after stop;
- `ApplicationContextProvider` no longer holding the plugin context after stop.

Observation methods expose only counts or existence checks, not mutable internals.

## Compatibility

The old `pf4boot-web-starter` administration endpoints are removed, which is an intentional safety break for the runtime management surface. Applications should migrate to the `pf4boot-management-starter` `/pf4boot/admin/**` API. Dynamic bean-name conflicts now fail explicitly by default, surfacing duplicate exports, extensions, controllers, or interceptors earlier. Applications that need previous replacement behavior can opt into `REPLACE`.

## Verification Plan

- `.\gradlew.bat :pf4boot-api:compileJava`
- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :pf4boot-web-starter:test`
- `.\gradlew.bat :pf4boot-management-starter:test`

The root build still disables tasks whose names contain `test`, so cleanup assertions should be verified with module-level test tasks.

## Open Questions

- Bean-name namespacing needs either an API that returns the actual registered bean name or a richer registration record model; this phase does not introduce it.
