# Plugin Framework Priority Roadmap Implementation Plan

## 1. Scope

This plan tracks [plugin-framework-priority-roadmap.md](plugin-framework-priority-roadmap.md). The fixed priority order is:

1. P1 persistent management/JPA reload records;
2. P2 `providerReplacementPath`;
3. P3 Saga/Outbox sample;
4. P4 management console UI.

## 2. Phases

| Phase | Status | Goal | Main Acceptance |
| --- | --- | --- | --- |
| P0 Design and planning | Done | define priorities and boundaries | Chinese/English design, plan, and index are synchronized |
| P1 Persistent records | Done | harden management file stores and add JPA reload file records | records survive restart; idempotency replays; latest JPA reload recovers |
| P2 providerReplacementPath | Done | JPA reload supports staged provider package replacement | success, rollback on failure, unrelated isolation |
| P3 Saga/Outbox sample | Done (V1) | demonstrate cross-boundary eventual consistency | success, duplicate delivery, retry smoke |
| P4 Management console UI | Planned | independent sample UI using HTTP APIs/Actuator | local UI smoke, auth and idempotency display |

## 3. P1 Persistent Records

Affected modules:

- `pf4boot-management-starter`
- `pf4boot-jpa`
- `pf4boot-jpa-starter`
- `pf4boot-actuator`
- `samples/cross-plugin-jpa`

Tasks:

1. Verify and harden `FilePluginOperationStore`:
   - atomic write;
   - idempotency key survives restart;
   - `scanRecoverableRecords()` covers in-progress records;
   - corrupt files obey `failClosed`.
2. Verify and harden `FilePluginDeploymentRecordStore`:
   - `save/findById/recent`;
   - stable recent ordering;
   - no sensitive absolute paths.
3. Extend `JpaDomainReloadRecordRepository` with binary-compatible default methods:
   - `recent(int limit)`;
   - `scanRecoverableRecords()`.
4. Add `FileJpaDomainReloadRecordRepository`:
   - `save/findById/findByIdempotencyKey/bindIdempotencyKey/findLatest/recent`;
   - `latest.json`;
   - safe idempotency filenames.
5. Extend `Pf4bootJpaProperties.DomainReload`:
   - `recordStore.type`;
   - `recordStore.directory`;
   - `recordStore.failClosed`.
6. Update `JpaDomainReloadAutoConfiguration`:
   - memory default;
   - file repository when configured;
   - initialization failure follows `failClosed`.
7. Add Actuator summary fields:
   - `recordStoreType`;
   - `recoverableRecordCount`;
   - `latestReloadId`.
8. Extend sample runtime smoke for file-store mode:
   - execute management/JPA reload;
   - restart;
   - query old operation/deployment/JPA reload records.

Acceptance:

| Item | Evidence |
| --- | --- |
| P1-AC1 operation file store retains idempotency after restart | `pf4boot-management-starter:test` |
| P1-AC2 deployment file store has stable recent/findById | `pf4boot-management-starter:test` |
| P1-AC3 JPA reload file repository supports latest/recent/idempotency | `pf4boot-jpa-starter:test` |
| P1-AC4 corrupt records obey failClosed/failOpen | unit tests |
| P1-AC5 runtime smoke can query records after restart | `:samples:cross-plugin-jpa:app-run:runtimeSmoke` |
| P1-AC6 docs and translations are synchronized | `git diff --check`; U+FFFD scan |

Implementation notes:

- Hardened stable recent ordering for management operation/deployment records and added corrupt-record skip coverage.
- Added `FileJpaDomainReloadRecordRepository` with JSON Lines records, `latest.json`, idempotency indexes, fail-open, and fail-closed behavior.
- Added `pf4boot.plugin.jpa.domain-reload.record-store.*`; default remains `memory`, while `file` enables the persistent repository.
- Enhanced `/actuator/pf4bootjpareload` with `recordStoreType`, `latestReloadId`, `recentRecordCount`, and `recoverableRecordCount`.
- Enhanced `samples/cross-plugin-jpa` runtime smoke to verify latest JPA reload visibility after host restart.

## 4. P2 `providerReplacementPath`

Affected modules:

- `pf4boot-jpa`
- `pf4boot-jpa-starter`
- `pf4boot-core`
- `pf4boot-management-starter`
- `samples/cross-plugin-jpa`

Tasks:

1. Add failure/blocker codes:
   - `PROVIDER_REPLACEMENT_MISMATCH`;
   - `PROVIDER_REPLACEMENT_VERIFY_FAILED`;
   - `PROVIDER_REPLACEMENT_ACTIVATION_FAILED`;
   - `PROVIDER_REPLACEMENT_ROLLBACK_FAILED`.
2. Parse `providerReplacementPath` during planning:
   - path must stay under staging root;
   - descriptor pluginId must match current provider;
   - version, checksum, and trust manifest use existing verifiers;
   - dry-run returns replacement summary.
3. Integrate the provider replacement adapter:
   - reuse `PluginDeploymentService` package verification, drain, impact-chain stop/start, and rollback capability;
   - keep core free of JPA types;
   - JPA reload service maps deployment results into reload records and verifies that the JPA descriptor is ready after replacement.
4. Execute:
   - delegate drain, impact-chain stop/start, staged provider activation, and rollback to `PluginDeploymentService.replace(...)`;
   - verify that the descriptor is ready;
   - write replacement summary.
5. Recover:
   - restore old provider when staged provider fails;
   - start consumers after old provider recovers;
   - enter `MANUAL_INTERVENTION_REQUIRED` when recovery fails;
   - keep unrelated plugins available.
6. Expose replacement summary in management responses and records.
7. Add runtime smoke for success, mismatch, activation failure rollback, and unrelated isolation.

Acceptance:

| Item | Evidence |
| --- | --- |
| P2-AC1 path outside staging root is rejected | unit test |
| P2-AC2 provider pluginId mismatch produces blocker | plan service test |
| P2-AC3 success replacement produces ready descriptor and consumers recover | service test + runtime smoke |
| P2-AC4 failed replacement rolls back old provider | service test |
| P2-AC5 unrelated plugin remains available | runtime smoke |
| P2-AC6 record persists replacement summary | repository test |

Implementation notes:

- Added provider replacement failure codes and `JpaProviderReplacementSummary`.
- JPA reload planning now calls `PluginDeploymentService.planReplacement(...)` when `providerReplacementPath` is present.
- JPA reload execution now calls `PluginDeploymentService.replace(...)` when `providerReplacementPath` is present and records the deployment outcome in the reload record.
- The management HTTP entrypoint reuses staging-root path validation so JPA reload cannot bypass deployment path governance.
- `samples/cross-plugin-jpa` runtime smoke now covers `SMOKE_JPA_PROVIDER_REPLACEMENT_PATH`.

## 5. P3 Saga/Outbox Sample

Affected modules:

- `samples/saga-outbox/demo-host`
- `samples/saga-outbox/app-run`
- `settings.gradle`
- `docs/design`

Tasks:

1. Add independent multi-module sample V1:
   - `demo-host`;
   - `app-run`.
2. Define two logical boundaries: `order` and `billing`.
3. Group tables:
   - order: `OrderRecord`, `OutboxEvent`;
   - billing: `BillingAccount`, `InboxEvent`.
4. Add business APIs for create order, query status, dispatcher tick, and failure injection.
5. Implement dispatcher retry/dead-letter behavior.
6. Implement inbox idempotency.
7. Add runtime smoke for success, duplicate delivery, retry, and unsupported atomic transaction diagnostics.
8. Document clearly that this is a business pattern, not framework transaction support.

Acceptance:

| Item | Evidence |
| --- | --- |
| P3-AC1 sample runtime assembles | Gradle task |
| P3-AC2 success path completes order and billing | runtime smoke |
| P3-AC3 duplicate delivery does not charge twice | runtime smoke |
| P3-AC4 failed delivery can retry successfully | runtime smoke |
| P3-AC5 docs state non-atomic transaction boundary | README + design docs |

Implementation notes:

- Added the independent `samples/saga-outbox` sample with `demo-host` and `app-run`.
- Used JDBC/H2 to make order outbox, billing inbox idempotency, and dispatcher tick behavior explicit.
- Added runtime smoke covering `SAGA_ORDER_PAID`, `SAGA_INBOX_IDEMPOTENT`, and `SAGA_RETRY_SUCCESS`.
- The multi-plugin split is intentionally left out of P3 V1 so Saga/Outbox behavior acceptance does not depend on another plugin matrix; it can be added as a later sample enhancement.

## 6. P4 Management Console UI

Affected modules:

- `samples/plugin-management-console/*`
- `samples/cross-plugin-jpa`
- `docs/design`

Tasks:

1. Write an API contract list for plugin list, lifecycle, deployment, JPA reload, Actuator, and metrics.
2. Add independent sample UI:
   - no dependency from published `pf4boot-*` modules;
   - lightweight static page or independent frontend project;
   - default local API base URL.
3. Add views for plugin list, deployment records, JPA reload, governance/Actuator, and operation results.
4. Ensure writes carry token and `X-Idempotency-Key`, and use plan before replace/confirm.
5. Add local smoke for list, dry-run plan, auth failure, and idempotency replay.

Acceptance:

| Item | Evidence |
| --- | --- |
| P4-AC1 UI is not depended on by published modules | Gradle/module check |
| P4-AC2 UI calls only HTTP APIs/Actuator | API mock/contract test |
| P4-AC3 writes include token and idempotency key | UI test |
| P4-AC4 plan blockers/warnings are visible | UI test |
| P4-AC5 local sample smoke runs | Browser/Playwright or equivalent smoke |

## 7. Documentation Requirements

Each phase must update:

- this plan status;
- relevant design docs;
- English translations;
- sample README;
- acceptance evidence.

Document checks:

- `git diff --check`
- actual U+FFFD replacement character scan

## 8. Suggested Commit Split

| Commit | Content |
| --- | --- |
| P0 | design and planning docs |
| P1a | management file store verification and tests |
| P1b | JPA reload file repository |
| P1c | runtime smoke persistent records |
| P2a | providerReplacementPath plan/blockers |
| P2b | provider replacement execute/rollback |
| P2c | sample smoke |
| P3a | Saga/Outbox sample skeleton |
| P3b | Outbox/Inbox business flow |
| P3c | sample smoke and docs |
| P4a | API contract and UI skeleton |
| P4b | main views and operations |
| P4c | UI smoke and docs |
