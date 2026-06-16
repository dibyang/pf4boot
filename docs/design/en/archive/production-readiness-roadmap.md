# Production Readiness Roadmap

## Background

`pf4boot` already provides the core capabilities for PF4J plugin loading, Spring Boot plugin contexts, shared beans, dynamic MVC, the plugin JPA starter, plugin packaging, and sample verification. Recent design documents also cover lifecycle cleanup, starter boundaries, runtime safety, and the verification foundation.

The next phase should move the framework from "functionally usable" to "production governable". The main gaps are regression verification, runtime observability, plugin upgrade governance, JPA capability boundaries, and user-facing documentation.

## Goals

- Establish continuous regression coverage for lifecycle, Web, JPA, packaging, and sample smoke paths.
- Expose operational plugin status, resource counts, errors, and lifecycle metrics.
- Keep the JPA dynamic capability boundary explicit and provide later design entry points for schema management and runtime refresh.
- Add integrity, upgrade, rollback, compatibility, and release governance for plugin packages.
- Let plugin developers complete dependency declaration, Web/JPA plugin development, packaging, and troubleshooting from documentation.

## Non-Goals

- Do not implement runtime JPA metamodel hot refresh in this roadmap.
- Do not raise the runtime baseline above Java 8 or Spring Boot 2.7.x.
- Do not turn the sample application into a full plugin marketplace or console product.
- Do not change lifecycle ordering, class loading priority, or the existing plugin package format by default. Breaking changes require staged migration.

## Current State / Existing Flow

| Capability | Current State | Main References |
| --- | --- | --- |
| Plugin lifecycle | Loading, start, stop, reload, and cleanup are designed and implemented | `docs/design/plugin-lifecycle.md`, `pf4boot-core` |
| Shared beans and dynamic registration | Root, platform, application scopes and conflict policy exist | `docs/design/context-and-bean-sharing.md`, `DynamicBeanConflictPolicy` |
| Web integration | Dynamic MVC mappings, interceptors, and static resources exist | `docs/design/web-integration.md`, `pf4boot-web-starter` |
| JPA integration | Entities are scanned at startup; runtime metadata sync is explicitly unsupported | `docs/design/jpa-integration.md`, `DefaultDynamicMetadata` |
| Tests | Some core, web-starter, and jpa-starter tests exist; end-to-end and packaging verification are still thin | `pf4boot-core/src/test`, `pf4boot-web-starter/src/test`, `pf4boot-jpa-starter/src/test` |
| Build policy | No root-level global test-disable logic was found; some historical documents still contain the old statement | `build.gradle`, `docs/design/verification-foundation.md` |

## Core Constraints

- Keep `jdkVersion=1.8`; do not use Java 9+ APIs or syntax.
- Public APIs, annotations, and shared model types belong in `pf4boot-api`.
- PF4J runtime, plugin manager behavior, repositories, loaders, lifecycle, class loading, and scheduling belong in `pf4boot-core`.
- Spring Boot auto-configuration belongs in `pf4boot-starter`, `pf4boot-web-starter`, or `pf4boot-jpa-starter` according to capability area.
- Web capabilities stay in `pf4boot-web-*`; JPA/Hibernate capabilities stay in `pf4boot-jpa*`.
- Plugin modules continue to use `compileOnlyApi` for host-provided APIs and `bundle` for plugin-packaged dependencies.
- Breaking default changes must include migration notes, compatibility switches, and at least one minor-version transition path.

## Interface Design

### Operational Observability

| Interface | Module | Output |
| --- | --- | --- |
| `PluginRuntimeSnapshot` | `pf4boot-api` | Plugin ID, version, state, start duration, recent error summary, resource counts |
| `PluginRuntimeInspector` | `pf4boot-api` | Query one plugin or all plugin runtime snapshots |
| Actuator endpoint | New standalone `pf4boot-actuator` module | `/actuator/pf4boot` returns snapshots and never mutates state |
| Micrometer metrics | Conditional feature | Plugin counts, start failures, operation latency, dynamic resource residue counts |

### Plugin Governance

| Capability | Design Direction |
| --- | --- |
| Integrity verification | First phase uses SHA-256 checksums and a pluggable verifier before load |
| Signature verification | Signature format, trust roots, and certificate rotation are designed later; the first phase does not bind to JAR signing or a custom signature format |
| Compatibility checks | Check PF4Boot version, Java version, host capabilities, and plugin dependency ranges before load |
| Rollback | Keep the previous plugin package and state so failed reload/upgrade can roll back |
| Gray rollout | Enable selected plugins, versions, or plugin groups by configuration |

## Data Structures

### `PluginRuntimeSnapshot`

| Field | Type | Description |
| --- | --- | --- |
| `pluginId` | `String` | Plugin ID |
| `version` | `String` | Loaded version |
| `state` | `String` | PF4J plugin state |
| `enabled` | `boolean` | Whether the plugin is enabled |
| `startedAt` | `Long` | Last start timestamp in milliseconds |
| `lastOperation` | `String` | Most recent lifecycle operation |
| `lastError` | `String` | Recent error summary without sensitive stack details |
| `resourceCounts` | `Map<String, Integer>` | Dynamic bean, MVC mapping, interceptor, scheduled task, and other resource counts |

### `PluginPackageVerification`

| Field | Type | Description |
| --- | --- | --- |
| `pluginId` | `String` | Plugin ID |
| `version` | `String` | Plugin version |
| `checksum` | `String` | Plugin package digest |
| `signatureStatus` | `String` | `NOT_CONFIGURED`, `VALID`, or `INVALID` |
| `compatibilityStatus` | `String` | `PASS`, `WARN`, or `FAIL` |
| `messages` | `List<String>` | Verification notes and failure reasons |

## State Machine

| State | Description | Allowed Transitions |
| --- | --- | --- |
| `DISCOVERED` | Plugin package found but not loaded | `VERIFYING`, `REJECTED` |
| `VERIFYING` | Integrity, signature, and compatibility checks are running | `VERIFIED`, `REJECTED` |
| `VERIFIED` | Checks passed and PF4J may load the plugin | `LOADED`, `REJECTED` |
| `LOADED` | Loaded but not started | `STARTING`, `DISABLED`, `UNLOADING` |
| `STARTING` | Plugin context and shared resources are starting | `STARTED`, `FAILED` |
| `STARTED` | Plugin is running and serving capabilities | `STOPPING`, `RELOADING` |
| `STOPPING` | Plugin is stopping and dynamic resources are being released | `STOPPED`, `FAILED` |
| `STOPPED` | Stopped but can be started again | `STARTING`, `UNLOADING` |
| `RELOADING` | Stop, unload, reload, and start are in progress | `STARTED`, `FAILED_ROLLBACK_REQUIRED` |
| `FAILED_ROLLBACK_REQUIRED` | Upgrade or reload failed and needs rollback or manual handling | `ROLLING_BACK`, `REJECTED` |
| `ROLLING_BACK` | Previous package and state are being restored | `STARTED`, `STOPPED`, `FAILED` |
| `REJECTED` | Verification failed or policy rejected the plugin | Terminal unless the external package is replaced |

## Sequence Flow

### Plugin Upgrade / Reload

1. A new package is discovered or the user triggers reload.
2. Integrity, signature, and compatibility checks run.
3. The current package and state are copied as a rollback point.
4. The current plugin is stopped and dynamic resources are released.
5. The old classloader is unloaded and the new package is loaded.
6. The new plugin starts and resource counts, state, and smoke checks are verified.
7. Any failure enters rollback flow, restoring the previous version or stopping in a diagnosable failed state.

## Exception Handling

| Category | External Behavior | Logs / Observability | Compensation |
| --- | --- | --- | --- |
| Package verification failure | Reject load or upgrade | Record checksum, signature, or compatibility reason | Keep old version |
| Start failure | Enter failed state or roll back | Record startup phase, recent error summary, and resource counts | Run cleanup chain |
| Stop failure | Return failure and keep diagnostics | Record unreleased resource counts | Allow stop/release retry |
| Rollback failure | Enter manual-handling state | High-priority log and metric | Preserve failure scene |

## Idempotency

- `startPlugin` remains idempotent for already started plugins and returns current state instead of registering resources again.
- `stopPlugin` remains idempotent for stopped or unloaded plugins and must not throw unexpected exceptions that block batch stop.
- `reloadPlugin` uses plugin ID and target version as the operation de-duplication key; reloads for the same plugin must be serialized by lifecycle locking.
- Resource registration records are keyed by plugin ID, scope, group, bean name, or mapping key. Stop releases by records instead of rescanning a half-closed context.
- Metrics may count attempts, but resource state must not duplicate on retry.

### HTTP Management API

HTTP management APIs are tracked as a dedicated production-readiness topic:

- [plugin-http-management-api.md](../plugin-http-management-api.md)
- [plugin-http-management-api-plan.md](plugin-http-management-api-plan.md)
- [plugin-http-management-api-implementation-guide.md](plugin-http-management-api-implementation-guide.md)
- [plugin-http-management-api-acceptance.md](plugin-http-management-api-acceptance.md)

Core decision: mutation APIs are disabled by default; local calls use loopback plus token as minimum protection; remote calls must integrate authentication, authorization, CSRF/origin controls, rate limits, audit, and idempotency. `pf4boot-actuator` remains read-only and does not perform start, stop, reload, or deployment operations.

## Rollback Strategy

- Before plugin upgrade, keep the previous zip and parsed descriptor summary. Initially use only in-memory state and file-level rollback points.
- If the new version fails to start, try to restore the previous version by default. If that restore fails, keep the plugin stopped or failed and expose diagnostics.
- Breaking public API changes must add the new API first, deprecate the old API, and remove it only in a later breaking window.
- When documents and build policy diverge, fix the document or add verification instead of propagating historical statements as facts.

## Compatibility

- New SPIs default to no-op and must not force applications to add Actuator or Micrometer.
- Actuator support lives in a standalone `pf4boot-actuator` module and remains read-only. It must not expose plugin start, stop, or reload operations.
- Metrics should be conditional to avoid adding dependencies to non-Web or lightweight applications.
- Plugin integrity checks should initially support `WARN` mode so historical plugin packages can migrate gradually.
- JPA schema management is explicitly configured by the host or plugin. The framework documents boundaries and examples only, without binding to Flyway or Liquibase.
- If runtime JPA sync is implemented later, it must be a separate design and must not silently change the current explicit failure semantics of `DynamicMetadata.sync()`.

## Gray Rollout / Migration

| Phase | Default Behavior | Migration Action | Exit Criteria |
| --- | --- | --- | --- |
| P0 Verification loop | Verification is disabled by default and historical plugin loading behavior is unchanged | Add tests, align documentation with build policy, and deliver the plugin package verification SPI | Core paths are repeatably verifiable |
| P1 Observability loop | A read-only endpoint is enabled only after adding standalone `pf4boot-actuator` | Operations consume status and metrics; read-only plugin snapshot endpoint is delivered | Plugin failures and residue are diagnosable |
| P2 Governance enhancement | Verification starts as WARN then ENFORCE | Plugin packages add checksum/signature | New packages can be verified before load |
| P3 JPA boundary | Plugin JPA defaults to `ddl-auto=none` | Make schema ownership explicit for host or plugin migration tools; default DDL test is delivered | Plugin developers do not inherit unsafe auto-DDL defaults |

## Test Plan

- `pf4boot-core`: lifecycle concurrency, dependency-chain start/stop, failure cleanup, classloader release, shared bean idempotency.
- `pf4boot-web-starter`: dynamic mapping/interceptor registration, cleanup, repeated registration, and conflict handling.
- `pf4boot-jpa-starter`: JPA default-off behavior, package scan boundaries, default DDL policy, runtime sync failure semantics.
- `samples/cross-plugin-jpa`: plugin dependencies, bundle/compileOnlyApi boundaries, and Web/JPA sample smoke checks.
- Documentation consistency: verify that `docs/design` statements about test policy, JPA boundaries, plugin packaging, and observability boundaries match code.

Minimal verification commands by phase:

- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :pf4boot-web-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

## Risks

| Risk | Severity | Detection | Mitigation |
| --- | --- | --- | --- |
| Observability dependencies leak into lightweight apps | Medium | Dependency tree checks | Use conditional auto-configuration and optional modules |
| Plugin upgrade rollback is incomplete | High | Failure-injection tests | Keep old package, state snapshot, and failed state |
| JPA dynamic refresh misleads users | High | Documentation and tests | Keep `sync()` explicitly failing and design enhancement separately |
| Documents drift from code again | Medium | Documentation consistency checks | Update Chinese/English design and README with each change |

## Phased Implementation Plan

### Phase 1: Close the Verification Loop

- Clean historical documentation about test-task policy and confirm the current Gradle strategy.
- Add failure-path and concurrency tests for core/web/jpa.
- Add plugin package structure and smoke checks for `samples/cross-plugin-jpa`.
- Establish minimal CI commands and separate quick verification from full verification.

### Phase 2: Observability and Diagnostics

- Defined `PluginRuntimeSnapshot` and `PluginRuntimeInspector`.
- Added a standalone `pf4boot-actuator` module exposing the read-only actuator endpoint `pf4bootplugins`.
- Kept existing starters free of Actuator dependencies; observability is registered only when hosts explicitly add `pf4boot-actuator`.
- Conditionally publishes Micrometer metrics: after adding `pf4boot-actuator`, gauges are registered for total, started, and failed plugin counts.
- Includes recent error, resource-count placeholder, and start duration in plugin status queries.
- Acceptance: applications depending on existing starters do not pull in Actuator or Micrometer implicitly; after adding `pf4boot-actuator`, the observability endpoint can only read plugin snapshots and cannot start, stop, or reload plugins.

### Phase 3: Govern JPA Capabilities

- Kept the current startup-scan behavior and explicit `DynamicMetadata.sync()` failure.
- Documented the schema management boundary: plugin JPA defaults to `ddl-auto=none`; the plugin or host explicitly configures migration tooling. The framework documents boundaries and examples only, without binding to Flyway or Liquibase.
- Added a test that locks `HibernateDefaultDdlAutoProvider` to `none`, preventing embedded databases from falling back to `create-drop`.
- Design runtime JPA refresh or `EntityManagerFactory` rebuild separately.
- Show recommended JPA plugin configuration in the sample.
- Acceptance: JPA documentation explains the default `ddl-auto=none` policy, schema migration responsibility, and example integration; the framework dependency tree does not add mandatory Flyway or Liquibase dependencies.

### Phase 4: Plugin Package Governance and Rollback

- Delivered the basic plugin package verification capability: added the `PluginPackageVerifier` SPI so hosts can inject custom verifiers as Spring beans.
- Delivered the default SHA-256 sidecar verifier with `DISABLED`, `WARN`, and `ENFORCE` modes; `DISABLED` remains the default for compatibility.
- Delivered the pre-load verification hook: verification runs after descriptor parsing and before ClassLoader creation; `ENFORCE` failures block plugin loading.
- Added `upgradePlugin(pluginId, newPluginPath, rollbackPluginPath)`, which attempts to reload the previous package path and restore the original started state when upgrade fails.
- Added pre-load system-version compatibility verification with `DISABLED`, `WARN`, and `ENFORCE` modes.
- Compatibility boundaries now cover system-version checks, PF4J dependency resolution, and custom verifier extensions; signature format and persistent operation history are outside this delivery.
- Plugin dependency versions continue to use PF4J dependency resolution; host capabilities can be added through custom `PluginPackageVerifier` implementations.
- Acceptance: missing or failed package verification produces clear diagnostics; checksum mode can move from WARN to ENFORCE; failed upgrades can roll back from the previous package file; persistent operation history is not introduced.

### Later Topic: Plugin Signatures

- Design signature format, trust-root configuration, certificate rotation, revocation policy, and offline verification flow separately.
- Evaluate native JAR signing, an external manifest, and a framework-specific manifest for compatibility, implementation cost, and operational cost.
- This topic does not block the phase 4 checksum/verifier work.

### Phase 5: Developer Documentation and Examples

- Wrote the plugin developer guide covering dependency scopes, package verification, read-only observability, JPA, and upgrade rollback.
- Updated sample configuration to show `system-version`, package verification, and compatibility verification switches.
- Kept Chinese and English design indexes synchronized.
- Common failures are currently exposed through clear exception messages and actuator snapshots; an error-code catalog can be added later as a separate documentation enhancement.

## Decisions / Later Topics

- Actuator support should live in a standalone `pf4boot-actuator` module so existing starters do not pull in Actuator or Micrometer dependencies implicitly.
- Observability endpoints remain read-only and do not expose plugin start, stop, or reload operations.
- Plugin package governance starts with SHA-256 checksums and a pluggable verifier. Signature format, trust roots, and certificate rotation are designed separately later.
- Plugin upgrade initially keeps only in-memory state and file-level rollback points; persistent operation history is out of scope for the first phase.
- JPA schema management is explicitly configured by the host or plugin. Framework documentation explains boundaries and examples without binding to Flyway or Liquibase.
