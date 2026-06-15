# JPA Runtime Refresh Acceptance Checklist

## 1. Usage

This file tracks acceptance for [jpa-runtime-refresh-plan.md](jpa-runtime-refresh-plan.md).

Statuses:

- `Planned`: planned but not implemented.
- `In Progress`: implementation is in progress.
- `Done`: implemented and verified.
- `Blocked`: blocked by external conditions.

## 2. R0 Documentation

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| R0-AC1: Chinese design, plan, and acceptance docs exist | Done | `jpa-runtime-refresh.md`, `jpa-runtime-refresh-plan.md`, `jpa-runtime-refresh-acceptance.md` |
| R0-AC2: English translations exist | Done | `en/jpa-runtime-refresh.md`, `en/jpa-runtime-refresh-plan.md`, `en/jpa-runtime-refresh-acceptance.md` |
| R0-AC3: README indexes include the new docs | Done | `docs/design/README.md` and `docs/design/en/README.md` updated |
| R0-AC4: stale complex sample isolation status is aligned with P10 | Done | `cross-plugin-jpa-transaction-complex-sample-acceptance.md` marks it as passed |
| R0-AC5: docs pass whitespace and encoding checks | Done | `git diff --check`; U+FFFD scan |

## 3. R1 Public Models and Configuration

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| R1-AC1: `pf4boot-jpa` exposes reload models and SPI | Done | `pf4boot-jpa/src/main/java/net/xdob/pf4boot/jpa/reload/*` |
| R1-AC2: `pf4boot.plugin.jpa.domain-reload.*` binds correctly | Done | `Pf4bootJpaProperties.DomainReload` |
| R1-AC3: default `DISABLED` mode changes no runtime behavior | Done | `DefaultJpaDomainReloadServiceTest.reloadDoesNotExecuteWhenConfiguredDisabledEvenIfRequestAsksExecuteMode`; runtime smoke `jpaReloadDisabledNoMutation` |
| R1-AC4: Java 8 compilation passes | Done | `.\gradlew.bat :pf4boot-jpa:compileJava :pf4boot-jpa-starter:compileJava` |
| R1-AC5: request validation covers empty domain, missing idempotency key, long reason, and unsupported providerReplacementPath | Done | `reloadRejectsInvalidRequestsBeforeExecution`, `reloadRejectsProviderReplacementPathWithoutExecuting` |
| R1-AC6: failure/blocker codes are stable enums and do not expose exception class names | Done | `JpaDomainReloadFailureCode`, `JpaDomainReloadBlocker`; management APIs return code/message |

## 4. R2 PLAN_ONLY

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| R2-AC1: descriptor lookup works | Done | `DefaultJpaDomainReloadPlanService.findDescriptor` |
| R2-AC2: provider and descriptor snapshot are visible | Done | `DefaultJpaDomainReloadPlanServiceTest` |
| R2-AC3: consumers, unrelated plugins, stop order, and start order are visible | Done | `DefaultJpaDomainReloadPlanServiceTest`; runtime smoke `jpaReloadPlanOnly` |
| R2-AC4: plan performs no stop/start/reload mutation | Done | read-only plan service; runtime smoke verifies business remains available after planning |
| R2-AC5: blockers are visible | Done | `JpaDomainReloadFailureCode` blockers; focused plan-service tests |
| R2-AC6: plan output ordering is stable | Done | sorted plugins and consumers; fixed-order tests |
| R2-AC7: inferred consumers make execute non-executable | Done | `INFERRED_CONSUMER_PRESENT` blocker; plan-service tests |

## 5. R3 Binding Registry

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| R3-AC1: shared consumers register `JpaPluginBinding` | Done | `PluginJPAStarter.registerBinding` |
| R3-AC2: bindings are removed on stop | Done | `PluginJPAStarter.destroy` |
| R3-AC3: local-mode plugins are excluded | Done | registry only records shared bindings; plan service uses matching-domain bindings |
| R3-AC4: other domains are excluded | Done | `DefaultJpaPluginBindingRegistry.findByDomainId` |
| R3-AC5: dependency-graph fallback is marked `INFERRED` | Done | `DefaultJpaDomainReloadPlanService.inferredConsumers` |
| R3-AC6: registry is thread-safe and does not expose mutable internal collections | Done | `DefaultJpaPluginBindingRegistryTest` |

## 6. R4 Management and Actuator

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| R4-AC1: plan endpoint works when the service exists | Done | `JpaDomainReloadManagementController.plan`; runtime smoke `jpaReloadPlanOnly` |
| R4-AC2: reload record query works | Done | `JpaDomainReloadManagementController.getRecord`; runtime smoke `jpaReloadRecord` |
| R4-AC3: disabled mode does not execute refresh | Done | runtime smoke `jpaReloadDisabledNoMutation` |
| R4-AC4: Actuator exposes read-only summary | Done | `Pf4bootJpaReloadEndpoint`; runtime smoke `actuatorJpaReload` |
| R4-AC5: security, audit, and idempotency are reused | Done | controller reuses `PluginManagementAuthorizer`, `PluginManagementRequestFactory`, `PluginManagementAuditRecorder`, and `X-Idempotency-Key` |
| R4-AC6: management output does not include sensitive absolute paths, tokens, or full stacks | Done | DTO output is plan/record/code/message only; smoke reports do not write tokens |
| R4-AC7: `current` and `record` query APIs distinguish missing and running records | Done | `JpaDomainReloadService.getCurrent/getRecord`; controller returns `notFound` for null |

## 7. R5 Executable Refresh

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| R5-AC1: reload is serialized per domain | Done | `DefaultJpaDomainReloadService` global lock and per-domain lock |
| R5-AC2: repeated idempotency keys do not execute twice | Done | `InMemoryJpaDomainReloadRecordRepository`; `reloadReplaysSameIdempotencyKey` |
| R5-AC3: drain failure does not stop plugins | Done | `DefaultJpaDomainReloadServiceTest.reloadDoesNotStopPluginsWhenDrainTimesOut`; runtime smoke covers `jpaReloadDrainTimeoutNoMutation` |
| R5-AC4: consumers stop in downstream-first order | Done | `reloadStopsConsumersRestartsProviderAndStartsConsumers` |
| R5-AC5: provider restart removes old JPA exports and produces a ready descriptor | Done | `verifyProviderExportsRemoved`; post-start health check |
| R5-AC6: consumers start in upstream-first order | Done | `reloadStopsConsumersRestartsProviderAndStartsConsumers` |
| R5-AC7: failure records include state transitions and codes | Done | failure record path; `reloadFailsWhenProviderExportsRemainAfterStop` |
| R5-AC8: unrelated plugins survive provider or consumer restart failures | Done | runtime smoke `jpaProviderIsolation`, `unrelatedPluginAlive` |
| R5-AC9: repeated execute requests with the same idempotency key return the same reloadId | Done | runtime smoke `jpaReloadIdempotency` |
| R5-AC10: non-empty providerReplacementPath returns `UNSUPPORTED_REPLACEMENT_PATH` and does not execute | Done | `reloadRejectsProviderReplacementPathWithoutExecuting` |
| R5-AC11: old descriptor, EMF, TM, and datasource exports are absent after provider stop | Done | `verifyProviderExportsRemoved`; `reloadFailsWhenProviderExportsRemainAfterStop` |
| R5-AC12: provider start failure retries recovery once and enters manual intervention if recovery fails | Done | `reloadRetriesProviderStartOnce`; failure path returns `MANUAL_INTERVENTION_REQUIRED` |

## 8. R6 Runtime Smoke

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| R6-AC1: runtime smoke includes `jpaReloadPlanOnly` | Done | `RuntimeSmokeRunner` |
| R6-AC2: runtime smoke includes `jpaReloadSuccess` | Done | `RuntimeSmokeRunner` |
| R6-AC3: runtime smoke includes reload failure isolation | Done | `unrelatedPluginAlive`, `jpaProviderIsolation` |
| R6-AC4: `result.json` contains reload checks | Done | `samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json` |
| R6-AC5: JUnit XML contains reload checks | Done | `samples/cross-plugin-jpa/app-run/build/test-results/runtimeSmoke/TEST-runtime-smoke.xml` |
| R6-AC6: reports do not leak sensitive paths, tokens, or full stacks | Done | smoke runner writes check names, status, and summaries only |
| R6-AC7: runtime smoke includes `jpaReloadDisabledNoMutation` | Done | `RuntimeSmokeRunner` |
| R6-AC8: runtime smoke includes `jpaReloadIdempotency` | Done | `RuntimeSmokeRunner` |

## 9. R7 Documentation Closure

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| R7-AC1: developer guide documents JPA runtime refresh | Done | `plugin-developer-guide.md` |
| R7-AC2: JPA integration doc documents refresh boundary | Done | `jpa-integration.md` |
| R7-AC3: HTTP management API doc documents JPA reload APIs | Done | `plugin-http-management-api.md` |
| R7-AC4: English translations are synchronized | Done | matching docs under `docs/design/en` |
| R7-AC5: acceptance checklist reflects actual results | Done | this file |
| R7-AC6: V1 is clearly restart-based and does not promise zero downtime | Done | `jpa-runtime-refresh.md`, `jpa-integration.md`, developer guide |

## 10. V1 Completion Gates

| Gate | Status | Evidence |
| --- | --- | --- |
| V1-GATE-1: R1-R4 produce an independently releasable `PLAN_ONLY` capability | Done | plan service, management plan API, Actuator summary |
| V1-GATE-2: R5 executable mode stays disabled by default | Done | default `DISABLED`; sample explicitly sets `STOP_CONSUMERS_AND_REBUILD` |
| V1-GATE-3: R5 executable mode passes unit, integration, and runtime smoke verification | Done | `:pf4boot-jpa-starter:test`; `:samples:cross-plugin-jpa:app-run:runtimeSmoke` |
| V1-GATE-4: failure scenarios do not stop unrelated plugins | Done | runtime smoke `unrelatedPluginAlive`, `jpaProviderIsolation` |
| V1-GATE-5: all changed docs have English translations | Done | matching docs under `docs/design/en` |
| V1-GATE-6: targeted framework tests pass | Done | `:pf4boot-jpa-starter:test`, `:pf4boot-management-starter:test`, `:pf4boot-actuator:test` |
| V1-GATE-7: `:samples:cross-plugin-jpa:app-run:runtimeSmoke` passes | Done | runtime smoke |

## 11. Current Conclusion

JPA runtime refresh V1 is complete: disabled by default, plan-only capable, explicitly executable through restart-based refresh, integrated with the common `PluginTrafficDrainer`, observable through management APIs and Actuator, and covered by runtime smoke for disabled mode, planning, execution, idempotency, successful drain, drain-timeout no-mutation, and isolation. Follow-up work moves to persistent records, provider package replacement, and more advanced refresh strategies.
