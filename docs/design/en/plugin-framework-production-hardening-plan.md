# Plugin Framework Production Hardening Implementation Plan

## Scope

This plan tracks the work described in [plugin-framework-production-hardening.md](plugin-framework-production-hardening.md). It covers framework governance capabilities only. It does not include a management console UI, cross-datasource transaction implementation, or runtime JPA metamodel hot refresh.

## Principles

- Add public models and evidence first, then tighten defaults.
- New capabilities default to no-op, in-memory, or WARN mode.
- Every completed phase must update Chinese docs, English translations, acceptance records, and developer docs when applicable.
- Every phase needs a minimal Gradle verification command; runtime behavior also needs sample smoke.
- Do not introduce hard dependencies on a specific security, signing, migration, or persistence framework.

## Phase Overview

| Phase | Topic | Status | Deliverables |
| --- | --- | --- | --- |
| P0 | Design and tracking baseline | Planned | Design, plan, acceptance docs, indexes |
| P1 | Package signing and trust chain | Planned | SPI, result model, WARN mode, manifest example |
| P2 | Operation/deployment/audit persistence | Planned | Recorder SPI, file implementation, recovery scan |
| P3 | Lifecycle concurrency and leak verification | Planned | Lifecycle lock tests, cleanup reports, failure injection |
| P4 | Capability manifests and compatibility matrix | Planned | Capability manifest, precheck, JPA multi-datasource declarations |
| P5 | Management smoke and observability closure | Planned | Management smoke, Actuator diagnostics, metrics |
| P6 | Follow-up decision topics | Planned | JPA runtime refresh and cross-datasource transaction decision docs |

## P0 Design And Tracking Baseline

### Goal

Freeze production hardening scope and create implementation and acceptance tracking documents.

### Tasks

| ID | Task | Files/Modules | Verification |
| --- | --- | --- | --- |
| P0-1 | Add production hardening design | `docs/design/plugin-framework-production-hardening.md` | Doc check |
| P0-2 | Add English translation | `docs/design/en/plugin-framework-production-hardening.md` | Doc check |
| P0-3 | Add implementation plan and acceptance docs | `docs/design/*production-hardening-plan.md`, `*acceptance.md` | Doc check |
| P0-4 | Update design indexes | `docs/design/README.md`, `docs/design/en/README.md` | Link check |

### Exit Criteria

- Chinese and English documents exist.
- New documents are listed in design indexes.
- P0 acceptance entries can be updated with evidence.

## P1 Package Signing And Trust Chain

### Goal

Extend the current checksum/verifier foundation with signature and trust-chain verification. Start in WARN mode to avoid breaking existing plugins.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P1-1 | Define `PluginPackageTrustVerifier`, request/result, and trust root SPI | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P1-2 | Chain trust verification before plugin load/deployment precheck | `pf4boot-core`, `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-core:test` |
| P1-3 | Add an external manifest example with checksum and signature metadata | `pf4boot-core`, `samples/*` | Sample packaging check |
| P1-4 | Add `DISABLED/WARN/ENFORCE` configuration and safe error summaries | `pf4boot-starter`, `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P1-5 | Document plugin package manifest and WARN-to-ENFORCE migration | Developer guide and English translation | Doc check |

### Constraints

- Do not hard depend on KMS, CA, or JAR signing tools.
- Default configuration must keep unsigned historical plugins compatible.
- ENFORCE failures must not expose private key paths, tokens, full stacks, or sensitive environment variables.

### Exit Criteria

- Historical plugins still load by default.
- WARN mode records missing signature, invalid signature, and missing trust root.
- ENFORCE mode blocks untrusted plugin packages.

## P2 Operation/Deployment/Audit Persistence

### Goal

Make management operations, deployment records, audit events, and idempotency records recoverable across host restarts.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P2-1 | Define `PluginOperationRecorder`, `PluginIdempotencyStore`, and query model | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P2-2 | Provide default in-memory implementations | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test` |
| P2-3 | Provide a file recorder with atomic writes and recovery scan | `pf4boot-core` or a support module | Targeted test |
| P2-4 | Connect management idempotency, audit, and deployment records to persistence | `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P2-5 | Add crash recovery docs and sample configuration | `docs/design`, `samples/*` | Doc and sample check |

### Constraints

- Write operations fail closed when recorder is unavailable.
- Half-written file records must not be treated as success.
- Database recorders are SPI-only in this phase.

### Exit Criteria

- The same idempotency key returns the existing result or conflict after restart.
- `EXECUTING` records are recognized during recovery scanning.
- Audit records do not contain tokens or sensitive paths.

## P3 Lifecycle Concurrency And Leak Verification

### Goal

Add tests and diagnostics for lifecycle mutual exclusion, dependency-chain replacement, stop cleanup, and failure injection.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P3-1 | Define and lock start/stop/reload/delete mutual exclusion | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test` |
| P3-2 | Add cleanup diagnostic model and test helpers | `pf4boot-api`, `pf4boot-core` | Targeted test |
| P3-3 | Assert Web mappings, interceptors, schedulers, and shared beans after stop | `pf4boot-web-starter`, `pf4boot-core` | Targeted test |
| P3-4 | Add failure injection for load, startup, health check, and rollback failures | `pf4boot-core`, `pf4boot-management-starter` | Targeted test |
| P3-5 | Add a failure-demo plugin to the complex sample | `samples/cross-plugin-jpa` | Sample smoke |

### Constraints

- Do not use `Thread.stop` or forcibly abort JDBC transactions for draining.
- Java 8 classloader leak checks are primarily test helpers; runtime diagnostics stay weak/read-only.
- Lifecycle locks must preserve PF4J dependency ordering semantics.

### Exit Criteria

- Concurrent start/stop/reload for the same plugin cannot duplicate registrations.
- Dynamic resource counts are zero after stop or a clear leak report is emitted.
- Hot replacement failures enter known failed or rolled-back states.

## P4 Capability Manifests And Compatibility Matrix

### Goal

Let plugins declare capabilities and requirements before deployment, so the host can detect missing runtime conditions early.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P4-1 | Define capability manifest model and parsing rules | `pf4boot-api`, `pf4boot-core` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P4-2 | Support capability declarations in descriptor or a standalone manifest | Gradle plugin/packaging modules | Sample packaging check |
| P4-3 | Add capability missing checks to deployment precheck | `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P4-4 | Let JPA datasource plugins declare `jpa.datasource` and consumers declare `jpa.consumer` | `pf4boot-jpa*`, `samples/cross-plugin-jpa` | JPA sample smoke |
| P4-5 | Add framework, Java, and PF4Boot capability compatibility matrix | `docs/design` | Doc check |

### Constraints

- Capability manifests supplement, not replace, PF4J dependencies.
- Historical plugins without manifests WARN by default.
- Multiple named datasources can be declared, but cross-datasource transactions remain unsupported.

### Exit Criteria

- Missing JPA datasource capability fails or warns during consumer deployment precheck.
- Plugins depending on multiple datasources can declare entity and Repository package scans per datasource.
- The compatibility matrix explains why a plugin cannot be deployed on a given host.

## P5 Management Smoke And Observability Closure

### Goal

Use the sample host to verify management APIs, read-only observability, deployment records, JPA examples, and hot replacement end to end.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P5-1 | Add a sample host management smoke script or Gradle task | `samples/cross-plugin-jpa` | Sample smoke |
| P5-2 | Expose trust summary, deployment summary, and cleanup report summary through Actuator | `pf4boot-actuator` | `.\gradlew.bat :pf4boot-actuator:test` |
| P5-3 | Add metrics for management requests, rejections, idempotency hits, deployment duration, and rollback count | `pf4boot-management-starter`, `pf4boot-actuator` | Targeted test |
| P5-4 | Cover successful transaction, rollback, missing datasource, and failed hot replacement in complex sample | `samples/cross-plugin-jpa` | Runtime smoke |
| P5-5 | Document smoke startup, calls, and troubleshooting | Developer guide and English translation | Doc check |

### Constraints

- Smoke must not require private credentials or external commercial services.
- Local management still uses tokens.
- Actuator remains read-only.

### Exit Criteria

- One command packages the complex sample plugins.
- One smoke flow starts the host, calls management APIs, verifies JPA examples, checks observability, and shuts down.
- Failure smoke emits useful HTTP responses, deployment records, and logs.

## P6 Follow-Up Decision Topics

### Goal

Create decision documents for architectural questions that should not block P1-P5 implementation.

### Tasks

| ID | Task | Scope | Deliverable |
| --- | --- | --- | --- |
| P6-1 | Evaluate JPA runtime refresh / EntityManagerFactory rebuild | `pf4boot-jpa*` | Standalone design |
| P6-2 | Evaluate cross-datasource transaction strategies: forbidden, Saga, Outbox, optional XA module | JPA/transaction capability | Standalone design |
| P6-3 | Evaluate plugin marketplace/repository governance | packaging/management | Standalone design |
| P6-4 | Evaluate management console UI boundaries | management | Standalone design |

### Exit Criteria

- Each topic has a recommendation, non-goals, compatibility impact, and a decision on whether it enters implementation planning.

## Recommended Order

1. Complete and commit P0.
2. Implement P1 and P2 first because trust and persistence underpin enforcement, audit, and recovery.
3. Implement P3 next to avoid extending hot replacement without a verification loop.
4. P4 and P5 can proceed in parallel but must converge in sample smoke.
5. P6 is design-only and should not block P1-P5.

## Definition Of Done Per Phase

- Code is implemented and remains Java 8 compatible.
- Chinese docs and English translations are updated.
- Acceptance entries are updated to `Done` or `Blocked` with evidence.
- The minimal verification commands are run, or the exact blocker is recorded.
- The local commit contains one coherent phase.
