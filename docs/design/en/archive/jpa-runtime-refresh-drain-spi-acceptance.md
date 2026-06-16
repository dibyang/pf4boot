# JPA Runtime Refresh Drain SPI Acceptance Checklist

## 1. Usage

This file tracks acceptance for [jpa-runtime-refresh-drain-spi-plan.md](jpa-runtime-refresh-drain-spi-plan.md).

Statuses:

- `Planned`: planned but not implemented.
- `In Progress`: implementation is in progress.
- `Done`: implemented and verified.
- `Blocked`: blocked by external conditions.

## 2. D0 Design Completion

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D0-AC1: design reuses `PluginTrafficDrainer` instead of adding a parallel SPI | Done | `jpa-runtime-refresh-drain-spi.md` |
| D0-AC2: design includes fields, constructors, and JSON example | Done | `jpa-runtime-refresh-drain-spi.md` 5.6 |
| D0-AC3: design includes coordinator pseudocode and auto-configuration boundary | Done | `jpa-runtime-refresh-drain-spi.md` 5.6.3, 5.6.4 |
| D0-AC4: design includes tests and acceptance requirements | Done | `jpa-runtime-refresh-drain-spi.md` 9 |
| D0-AC5: English translation is synchronized | Done | `en/jpa-runtime-refresh-drain-spi.md` |

## 3. D1 Public Model Extension

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D1-AC1: add `JpaDomainDrainerPhase` | Done | `pf4boot-jpa/src/main/java/net/xdob/pf4boot/jpa/reload/JpaDomainDrainerPhase.java` |
| D1-AC2: add `JpaDomainDrainerResult` | Done | `JpaDomainDrainerResult.java` |
| D1-AC3: `JpaDomainDrainReport` keeps old constructor and adds full constructor | Done | both constructors in `JpaDomainDrainReport` |
| D1-AC4: list fields are immutable and null-safe | Done | `JpaDomainDrainReport.copy/copyStrings` |
| D1-AC5: `JpaDomainReloadRecord` adds `drainReport` and keeps old constructor | Done | overloaded constructor and `getDrainReport()` |
| D1-AC6: `:pf4boot-jpa:compileJava` passes | Done | `.\gradlew.bat :pf4boot-jpa:compileJava` |

## 4. D2 Drain Coordinator

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D2-AC1: coordinator injects all `PluginTrafficDrainer` beans | Done | `JpaDomainReloadDrainCoordinator`; `JpaDomainReloadAutoConfiguration` |
| D2-AC2: impact plugin IDs are stopOrder + provider, de-duplicated and stable | Done | `JpaDomainReloadDrainCoordinator.impactPluginIds`; `JpaDomainReloadDrainCoordinatorTest.noDrainerContinuesForCompatibility` |
| D2-AC3: no drainer with `require-drainer=false` returns accepted + warning | Done | `noDrainerContinuesForCompatibility` |
| D2-AC4: no drainer with `require-drainer=true` returns `DRAIN_REJECTED` | Done | `noDrainerRejectsWhenStrictModeEnabled` |
| D2-AC5: begin exception maps to `DRAIN_REJECTED` and ends already-begun drainers | Done | `beginFailureEndsAlreadyBegunDrainers` |
| D2-AC6: await false maps to `DRAIN_TIMEOUT` | Done | `awaitFalseReturnsTimeoutAndEndsDrainers` |
| D2-AC7: await exception/interruption maps to `DRAIN_REJECTED`; interrupt status is restored | Done | `awaitExceptionReturnsRejectedAndEndsDrainers`; interrupt restore logic in coordinator |
| D2-AC8: multiple drainers share one timeout budget | Done | coordinator deadline/remaining logic; `successfulDrainEndsLaterByPlanId` covers multiple drainers |

## 5. D3 Reload Service Integration

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D3-AC1: `DRAINING` phase calls the coordinator | Done | `DefaultJpaDomainReloadService` calls `drainCoordinator.drain` |
| D3-AC2: drain failure does not call `stopPlugin/startPlugin` | Done | `DefaultJpaDomainReloadServiceTest.reloadDoesNotStopPluginsWhenDrainTimesOut` |
| D3-AC3: drain success preserves V1 stop/start order | Done | `reloadStopsConsumersRestartsProviderAndStartsConsumers` |
| D3-AC4: success path calls `endDrain` | Done | `reloadEndsDrainAfterSuccess` |
| D3-AC5: stop/start/health failure path still calls or records `endDrain` | Done | `reloadEndsDrainWhenProviderStartFails` |
| D3-AC6: failed record includes `drainReport`, failure code, and transitions | Done | `reloadDoesNotStopPluginsWhenDrainTimesOut` |

## 6. D4 Management And Actuator Summary

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D4-AC1: reload record query returns `drainReport` | Done | `JpaDomainReloadRecord.getDrainReport()`; management API returns the record DTO |
| D4-AC2: Actuator `pf4bootjpareload` exposes latest drain summary | Done | `Pf4bootJpaReloadEndpoint.summary`; `Pf4bootJpaReloadEndpointTest.summaryReturnsLatestDrainSummary` |
| D4-AC3: Actuator does not throw with no history | Done | `summaryReturnsEmptyDrainFieldsWhenNoRecordExists` |
| D4-AC4: output does not include stack traces, absolute paths, tokens, or sensitive raw requests | Done | Actuator only exposes reloadId and drain boolean/duration/code/count summaries |

## 7. D5 Unit And Integration Tests

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D5-AC1: no-drainer compatibility and strict mode are tested | Done | `JpaDomainReloadDrainCoordinatorTest.noDrainerContinuesForCompatibility`, `noDrainerRejectsWhenStrictModeEnabled` |
| D5-AC2: begin/await/end exception paths are tested | Done | `beginFailureEndsAlreadyBegunDrainers`, `awaitExceptionReturnsRejectedAndEndsDrainers`, `endFailureIsWarningOnly` |
| D5-AC3: timeout and remaining budget are tested | Done | `awaitFalseReturnsTimeoutAndEndsDrainers`; coordinator uses deadline/remaining |
| D5-AC4: drain failure causes no stop/start | Done | `DefaultJpaDomainReloadServiceTest.reloadDoesNotStopPluginsWhenDrainTimesOut` |
| D5-AC5: stop/start failure still ends drain | Done | `reloadEndsDrainWhenProviderStartFails` |
| D5-AC6: `:pf4boot-jpa-starter:test` passes | Done | `.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-web-starter:test :pf4boot-core:test` |

## 8. D6 Sample Runtime Smoke

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D6-AC1: runtime smoke includes `jpaReloadDrainSuccess` | Done | `RuntimeSmokeRunner.checkJpaReload`; output `SMOKE_JPA_RELOAD_DRAIN_SUCCESS` |
| D6-AC2: runtime smoke includes `jpaReloadDrainTimeoutNoMutation` | Done | `RuntimeSmokeRunner.checkJpaReloadDrainTimeoutNoMutation`; output `SMOKE_JPA_RELOAD_DRAIN_TIMEOUT_NO_MUTATION` |
| D6-AC3: runtime smoke includes `actuatorJpaReloadDrainSummary` | Done | `RuntimeSmokeRunner.checkActuator` validates `lastDrainFailureCode` and `lastDrainPluginCount` |
| D6-AC4: `result.json` and JUnit XML contain drain checks | Done | `addCheck("jpaReloadDrainSuccess")`, `addCheck("jpaReloadDrainTimeoutNoMutation")`, `addCheck("actuatorJpaReloadDrainSummary")` |
| D6-AC5: workflow and unrelated plugins stay available after drain timeout | Done | after timeout, `/api/sample/workflow/summary` and `/api/sample/unrelated/health` both return 200 |
| D6-AC6: `:samples:cross-plugin-jpa:app-run:runtimeSmoke` passes | Done | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke` |

## 9. D7 Documentation Closure

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D7-AC1: main JPA refresh design updates drain semantics | Done | `jpa-runtime-refresh.md` 7.3 |
| D7-AC2: JPA integration docs update drain behavior | Done | `jpa-integration.md` restart-based refresh section |
| D7-AC3: developer guide updates drain usage and risks | Done | `plugin-developer-guide.md` JPA plugins |
| D7-AC4: English translations are synchronized | Done | `en/jpa-runtime-refresh.md`, `en/jpa-integration.md`, `en/plugin-developer-guide.md` |
| D7-AC5: acceptance checklist is updated with actual evidence | Done | this file marks D0-D7 as Done |
| D7-AC6: `git diff --check` and U+FFFD scan pass | Done | `git diff --check`; U+FFFD scan |

## 10. Current Conclusion

Drain SPI has completed D0-D7. JPA reload execute mode now uses the common `PluginTrafficDrainer` and is covered by unit tests, management/Actuator summary tests, and runtime smoke for successful drain, drain-timeout no-mutation, and Actuator summaries.
