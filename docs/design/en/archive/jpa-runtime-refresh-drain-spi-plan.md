# JPA Runtime Refresh Drain SPI Implementation Plan

## 1. Scope

This plan tracks [jpa-runtime-refresh-drain-spi.md](../jpa-runtime-refresh-drain-spi.md). The goal is to connect JPA domain restart-based refresh to the common `PluginTrafficDrainer`, so the `DRAINING` phase really rejects new work, waits for in-flight work, and records the result in reload records.

## 2. Phases

| Phase | Status | Goal |
| --- | --- | --- |
| D0 Design completion | Done | fields, constructors, pseudocode, config, errors, tests, and acceptance are specified |
| D1 Public model extension | Done | extend drain report, drainer result, and reload record |
| D2 Drain coordinator | Done | implement common drainer orchestration in JPA starter |
| D3 Reload service integration | Done | execute drain during `DRAINING`; no stop on drain failure |
| D4 Management and Actuator summary | Done | expose drain report summaries |
| D5 Unit and integration tests | Done | cover no-drainer, timeout, rejected, endDrain, and stop/start failures |
| D6 Sample runtime smoke | Done | cover drain success, timeout/no-mutation, and Actuator summary |
| D7 Documentation and acceptance closure | Done | update docs, acceptance, and translations |

## 3. D1 Public Model Extension

Affected module:

- `pf4boot-jpa`

Tasks:

1. Add `JpaDomainDrainerPhase`: `BEGIN`, `AWAIT`, `END`.
2. Add immutable `JpaDomainDrainerResult`.
3. Extend `JpaDomainDrainReport`:
   - keep `JpaDomainDrainReport(boolean accepted, String message)`;
   - add the full constructor;
   - convert null lists to immutable empty lists;
   - derive `durationMillis` from timestamps.
4. Extend `JpaDomainReloadRecord`:
   - add `drainReport`;
   - keep the old constructor;
   - add a constructor with `drainReport`;
   - add `getDrainReport()`.
5. Trim messages to 512 chars.

Verification:

```powershell
.\gradlew.bat :pf4boot-jpa:compileJava
```

## 4. D2 Drain Coordinator

Affected module:

- `pf4boot-jpa-starter`

Tasks:

1. Add `JpaDomainReloadDrainCoordinator`.
2. Inject `ObjectProvider<PluginTrafficDrainer>` and `Pf4bootJpaProperties`.
3. Compute impact plugin IDs as `plan.stopOrder + plan.providerPluginId`, de-duplicated and stable.
4. Collect drainers in Spring injection order, preferring bean names and falling back to class names.
5. Implement `beginDrain`.
6. Implement `awaitDrain` with one shared timeout budget.
7. Map `await=false` to `DRAIN_TIMEOUT`.
8. Map runtime exceptions and interruptions to `DRAIN_REJECTED`; restore interrupt status.
9. Implement reverse-order `endDrain`; failures become warnings.
10. Handle no-drainer compatibility and strict mode.

Verification:

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test
```

## 5. D3 Reload Service Integration

Affected module:

- `pf4boot-jpa-starter`

Tasks:

1. Register `JpaDomainReloadDrainCoordinator` in auto-configuration.
2. Inject the coordinator into `DefaultJpaDomainReloadService`.
3. Call the coordinator after adding `DRAINING`.
4. If drain fails:
   - append `FAILED`;
   - write failure code;
   - save record;
   - do not call plugin stop/start.
5. Continue the current stop/start flow after drain success.
6. Always call or record `endDrain` on success and failure paths.
7. Preserve current lock and `currentReloads` release order.

Verification:

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test
```

## 6. D4 Management And Actuator Summary

Affected modules:

- `pf4boot-management-starter`
- `pf4boot-actuator`

Tasks:

1. Reload record queries include `drainReport`.
2. `Pf4bootJpaReloadEndpoint` includes:
   - `lastDrainAccepted`
   - `lastDrainDurationMillis`
   - `lastDrainFailureCode`
   - `lastDrainPluginCount`
   - `lastDrainWarningCount`
3. Do not expose stack traces, absolute paths, tokens, or sensitive raw request fields.
4. No-history endpoint output must not throw.

Verification:

```powershell
.\gradlew.bat :pf4boot-management-starter:test :pf4boot-actuator:test
```

## 7. D5 Unit And Integration Tests

Tasks:

1. Test no-drainer compatibility and strict mode.
2. Test begin, await, and end exception paths.
3. Test timeout and remaining budget behavior.
4. Test drain failure causes no stop/start.
5. Test stop/start failure still calls `endDrain`.
6. Reuse existing Web drain tests for low-level Web behavior.

Verification:

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-web-starter:test :pf4boot-core:test
```

## 8. D6 Sample Runtime Smoke

Affected module:

- `samples/cross-plugin-jpa`

Tasks:

1. Add a sample test drainer or long-request path to deterministically trigger timeout.
2. Add `jpaReloadDrainSuccess`.
3. Add `jpaReloadDrainTimeoutNoMutation`.
4. Add `actuatorJpaReloadDrainSummary`.
5. Write checks to `result.json` and JUnit XML.
6. Update sample README.

Verification:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

## 9. D7 Documentation And Acceptance Closure

Tasks:

1. Update the main JPA refresh design.
2. Update JPA integration docs.
3. Update the developer guide.
4. Sync English translations.
5. Update acceptance status with actual evidence.

Verification:

```powershell
git diff --check
rg -n "U\+FFFD" docs/design docs/design/en samples/cross-plugin-jpa
```

## 10. Completion Gates

| Gate | Standard |
| --- | --- |
| GATE-1 | JPA reload execute calls `PluginTrafficDrainer` |
| GATE-2 | drain failure does not stop consumers or provider |
| GATE-3 | success, failure, exception, and manual-intervention paths call or record `endDrain` |
| GATE-4 | reload records expose drain reports |
| GATE-5 | management APIs and Actuator do not leak sensitive information |
| GATE-6 | unit tests, module tests, and runtime smoke pass |
| GATE-7 | Chinese and English design, plan, and acceptance docs are synchronized |

## 11. Rollback Strategy

1. JPA reload remains `DISABLED` by default.
2. `require-drainer=false` keeps compatibility if drain integration has issues.
3. Hosts can remove a faulty drainer bean.
4. `drainReport=null` remains valid for old records.
