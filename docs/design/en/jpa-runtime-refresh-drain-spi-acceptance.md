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
| D2-AC1: coordinator injects all `PluginTrafficDrainer` beans | Planned | Pending implementation |
| D2-AC2: impact plugin IDs are stopOrder + provider, de-duplicated and stable | Planned | Pending implementation |
| D2-AC3: no drainer with `require-drainer=false` returns accepted + warning | Planned | Pending implementation |
| D2-AC4: no drainer with `require-drainer=true` returns `DRAIN_REJECTED` | Planned | Pending implementation |
| D2-AC5: begin exception maps to `DRAIN_REJECTED` and ends already-begun drainers | Planned | Pending implementation |
| D2-AC6: await false maps to `DRAIN_TIMEOUT` | Planned | Pending implementation |
| D2-AC7: await exception/interruption maps to `DRAIN_REJECTED`; interrupt status is restored | Planned | Pending implementation |
| D2-AC8: multiple drainers share one timeout budget | Planned | Pending implementation |

## 5. D3 Reload Service Integration

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D3-AC1: `DRAINING` phase calls the coordinator | Planned | Pending implementation |
| D3-AC2: drain failure does not call `stopPlugin/startPlugin` | Planned | Pending implementation |
| D3-AC3: drain success preserves V1 stop/start order | Planned | Pending implementation |
| D3-AC4: success path calls `endDrain` | Planned | Pending implementation |
| D3-AC5: stop/start/health failure path still calls or records `endDrain` | Planned | Pending implementation |
| D3-AC6: failed record includes `drainReport`, failure code, and transitions | Planned | Pending implementation |

## 6. D4 Management And Actuator Summary

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D4-AC1: reload record query returns `drainReport` | Planned | Pending implementation |
| D4-AC2: Actuator `pf4bootjpareload` exposes latest drain summary | Planned | Pending implementation |
| D4-AC3: Actuator does not throw with no history | Planned | Pending implementation |
| D4-AC4: output does not include stack traces, absolute paths, tokens, or sensitive raw requests | Planned | Pending implementation |

## 7. D5 Unit And Integration Tests

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D5-AC1: no-drainer compatibility and strict mode are tested | Planned | Pending implementation |
| D5-AC2: begin/await/end exception paths are tested | Planned | Pending implementation |
| D5-AC3: timeout and remaining budget are tested | Planned | Pending implementation |
| D5-AC4: drain failure causes no stop/start | Planned | Pending implementation |
| D5-AC5: stop/start failure still ends drain | Planned | Pending implementation |
| D5-AC6: `:pf4boot-jpa-starter:test` passes | Planned | Pending verification |

## 8. D6 Sample Runtime Smoke

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D6-AC1: runtime smoke includes `jpaReloadDrainSuccess` | Planned | Pending implementation |
| D6-AC2: runtime smoke includes `jpaReloadDrainTimeoutNoMutation` | Planned | Pending implementation |
| D6-AC3: runtime smoke includes `actuatorJpaReloadDrainSummary` | Planned | Pending implementation |
| D6-AC4: `result.json` and JUnit XML contain drain checks | Planned | Pending implementation |
| D6-AC5: workflow and unrelated plugins stay available after drain timeout | Planned | Pending implementation |
| D6-AC6: `:samples:cross-plugin-jpa:app-run:runtimeSmoke` passes | Planned | Pending verification |

## 9. D7 Documentation Closure

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| D7-AC1: main JPA refresh design updates drain semantics | Planned | Pending implementation |
| D7-AC2: JPA integration docs update drain behavior | Planned | Pending implementation |
| D7-AC3: developer guide updates drain usage and risks | Planned | Pending implementation |
| D7-AC4: English translations are synchronized | Planned | Pending implementation |
| D7-AC5: acceptance checklist is updated with actual evidence | Planned | Pending implementation |
| D7-AC6: `git diff --check` and U+FFFD scan pass | Planned | Pending verification |

## 10. Current Conclusion

Drain SPI is designed and planned, but implementation has not started. Recommended implementation order is D1-D7. After D3, run `:pf4boot-jpa-starter:test`; after D6, run runtime smoke.
