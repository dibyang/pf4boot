# Plugin Framework Production Hardening Acceptance Tracking

## Usage

This file tracks acceptance status for [plugin-framework-production-hardening-plan.md](plugin-framework-production-hardening-plan.md). Recommended statuses:

- `Planned`: Planned but not implemented.
- `In Progress`: Implementation is in progress.
- `Done`: Completed with evidence.
- `Blocked`: Blocked by an external condition; record the reason.

After each completed task, add evidence such as commit hash, verification command, key logs, or document links.

## P0 Design And Tracking Baseline

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P0-AC1: Chinese production hardening design exists | Done | `docs/design/plugin-framework-production-hardening.md` |
| P0-AC2: English translation exists and matches the Chinese intent | Done | `docs/design/en/plugin-framework-production-hardening.md` |
| P0-AC3: Implementation plan and acceptance tracking docs are separate | Done | `docs/design/plugin-framework-production-hardening-plan.md`, `docs/design/plugin-framework-production-hardening-acceptance.md` |
| P0-AC4: Chinese and English design indexes are updated | Done | `docs/design/README.md`, `docs/design/en/README.md` |
| P0-AC5: Diff shows no obvious encoding damage or missing links | Done | Ran `git diff --check`, `rg -n "plugin-framework-production-hardening" docs\design\README.md docs\design\en\README.md`, and U+FFFD checks |

## P1 Package Signing And Trust Chain

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P1-AC0: Design and plan define packages, classes, configuration, manifest format, error codes, and required tests | Done | `docs/design/plugin-framework-production-hardening.md` "Implementation Conventions"; `docs/design/plugin-framework-production-hardening-plan.md` P1 implementation steps |
| P1-AC1: `PluginPackageTrustVerifier` and request/result/trust root SPI compile | Done | Added `net.xdob.pf4boot.trust.*`; ran `.\gradlew.bat :pf4boot-core:test --tests "net.xdob.pf4boot.DefaultPluginPackageTrustVerifierTest" --tests "net.xdob.pf4boot.DefaultPluginTrustManifestLoaderTest"` |
| P1-AC2: Default configuration does not block historical unsigned plugins | Done | `DefaultPluginPackageTrustVerifierTest.disabledModeIgnoresMissingManifest` |
| P1-AC3: WARN mode records missing manifest, signature metadata issues, and missing trust root | Done | `DefaultPluginPackageTrustVerifierTest.warnModeRecordsMissingManifest`, `warnModeRecordsMissingTrustRootForSignatureMetadata` |
| P1-AC4: ENFORCE mode blocks untrusted plugin packages | Done | `DefaultPluginPackageTrustVerifierTest.enforceModeRejectsMissingManifest`, `enforceModeRejectsManifestChecksumMismatch` |
| P1-AC5: Management errors do not leak tokens, private key paths, full stacks, or sensitive paths | Done | `PluginManagementExceptionHandlerTest.sanitizesFailureMessage`, `PluginManagementResponseSanitizerTest.safeTextRedactsSecretsPathsAndStackFrames`, `PluginManagementControllerTest.deploymentResponseAndAuditMessageAreSanitized`; ran `.\gradlew.bat :pf4boot-management-starter:test` |
| P1-AC6: Developer guide includes manifest examples and WARN-to-ENFORCE migration | Done | `docs/design/plugin-developer-guide.md` and English translation now include `.pf4boot-trust.json` example and `DISABLED/WARN/ENFORCE` migration steps |

## P2 Operation/Deployment/Audit Persistence

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P2-AC0: Design and plan define store reuse, directories, JSON Lines format, recovery scan rules, and fail-closed behavior | Done | `docs/design/plugin-framework-production-hardening.md` persistence format; `docs/design/plugin-framework-production-hardening-plan.md` P2 implementation steps |
| P2-AC1: `PluginOperationStore` exposes recovery scanning and compiles | Done | `PluginOperationStore.scanRecoverableRecords()`; ran `.\gradlew.bat :pf4boot-management-starter:test` |
| P2-AC2: Default in-memory implementation preserves current management behavior | Done | `PluginManagementIdempotencyServiceTest`, `.\gradlew.bat :pf4boot-management-starter:test` |
| P2-AC3: File recorder supports append writes and does not treat corrupted records as success | Done | `FilePluginOperationStoreTest.skipCorruptedLineWhenReloading` |
| P2-AC4: The same idempotency key returns the existing result or conflict after host restart | Done | `FilePluginOperationStoreTest.appendAndReadLatestRecordAfterRestart`, `saveIfIdempotencyKeyAbsentReturnsExistingRecord` |
| P2-AC5: Running operation records can be recognized by recovery scanning after restart, and deployment records can be queried across restart | Done | `FilePluginOperationStoreTest.scanRecoverableRecordsReturnsOnlyRunningStates`, `FilePluginDeploymentRecordStoreTest.appendAndReadDeploymentRecordAfterRestart` |
| P2-AC6: Audit records do not include tokens, full sensitive paths, or full exception stacks | Done | `PluginManagementIdempotencyServiceTest.markFinishedSanitizesPersistedMessages` covers operation store sanitization; `PluginManagementControllerTest.deploymentResponseAndAuditMessageAreSanitized` covers audit message sanitization; ran `.\gradlew.bat :pf4boot-management-starter:test` |

## P3 Lifecycle Concurrency And Leak Verification

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P3-AC0: Design and plan define lifecycle locks, diagnostics, failure injection, and forbidden changes | Done | `docs/design/plugin-framework-production-hardening-plan.md` P3 implementation steps, required tests, and forbidden changes |
| P3-AC1: Concurrent start/stop/reload for one plugin is serialized or rejected | Done | `Pf4bootPluginManagerLifecycleTest.concurrentStartsForSamePluginRunStartOnce`; `DefaultPluginLifecycleDiagnostic.inspectLifecycleLocks()` reports the `stateLock` strategy |
| P3-AC2: Repeated start does not duplicate shared beans, MVC mappings, interceptors, or schedulers | Done | `DefaultShareBeanMgrTest.duplicateExportRegistrationIsRemovedOnceOnStop`, `DefaultShareBeanMgrTest.exportsBeansToAllScopesAndCleansThemOnStop`, existing `PluginRequestMappingHandlerMappingTest` mapping/interceptor cleanup coverage |
| P3-AC3: Dynamic resource counts are zero after stop, or a clear leak report is emitted | Done | Added `PluginLifecycleDiagnostic`, `PluginCleanupReport`, `DefaultPluginLifecycleDiagnostic`; `DefaultShareBeanMgrTest.lifecycleDiagnosticReportsCleanedResourcesAfterStop` |
| P3-AC4: Load failure, startup failure, and health check failure enter diagnosable states | Done | `Pf4bootPluginManagerLifecycleTest.loadPluginVerifiesPackageBeforeCreatingClassLoader`, `failedStartClosesPluginContext`, `DefaultPluginDeploymentServiceTest.replaceRollsBackWhenPluginHealthProbeFails` |
| P3-AC5: Hot replacement failure can roll back the old package; rollback failure enters manual intervention with evidence | Done | `DefaultPluginDeploymentServiceTest.replaceRollsBackWhenNewPluginStartFails`, `replaceRollsBackWhenPackageActivationFails`, `replaceMovesToManualInterventionWhenRollbackFails` |
| P3-AC6: Complex sample includes a demo plugin or configuration that triggers failure paths | Planned | TBD |

## P4 Capability Manifests And Compatibility Matrix

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P4-AC0: Design and plan define capability manifest model, multi-datasource package-scan example, precheck modes, and required tests | Done | `docs/design/plugin-framework-production-hardening.md` manifest example; `docs/design/plugin-framework-production-hardening-plan.md` P4 multi-datasource example |
| P4-AC1: Capability manifest model and parsing rules compile | Done | Added `net.xdob.pf4boot.capability.*`; ran `.\gradlew.bat :pf4boot-core:test --tests "net.xdob.pf4boot.DefaultPluginTrustManifestLoaderTest" --tests "net.xdob.pf4boot.capability.*" --tests "net.xdob.pf4boot.deployment.DefaultPluginDeploymentServiceTest"` |
| P4-AC2: Historical plugins without manifests remain compatible or WARN by default | Done | `DefaultPluginCapabilityResolverTest.missingManifestReturnsEmptyDescriptorForHistoricalPlugin`; default `pluginCapabilityPrecheckMode=DISABLED` |
| P4-AC3: Deployment precheck detects missing capabilities and returns readable errors | Done | `DefaultPluginDeploymentServiceTest.planReplacementReportsMissingDatasourceCapabilityAsWarning`, `planReplacementRejectsMissingDatasourceCapabilityInEnforceMode` |
| P4-AC4: JPA datasource plugins can declare `jpa.datasource` | Done | `DefaultPluginCapabilityResolverTest.readsCapabilitiesFromTrustManifest` parses provider `jpa.datasource`, `datasource`, and `transactionManager` |
| P4-AC5: JPA consumer plugins can declare `jpa.consumer` and grouped entity/Repository package scans | Done | `DefaultPluginDeploymentServiceTest` consumer manifest declares `jpa.consumer` and `entityPackages`/`repositoryPackages`; `PluginCapabilityPrecheckTest.ignoresJpaConsumerPackageScanAttributesWhenMatchingProvider` |
| P4-AC6: Plugins depending on multiple datasources can report which package scan belongs to which datasource | Done | `DefaultPluginCapabilityResolverTest.readsCapabilitiesFromTrustManifest` covers `orderDs` and `billingDs` datasource/entity/repository package groups |
| P4-AC7: Compatibility matrix covers framework version, Java version, capability version, and plugin dependency ranges | Done | `docs/design/plugin-framework-production-hardening-plan.md` first-stage compatibility matrix; capability versions are diagnostic first, PF4J plugin dependencies remain PF4J-owned |

## P5 Management Smoke And Observability Closure

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P5-AC0: Design and plan define smoke steps, request headers, security constraints, observability checks, and cleanup requirements | Done | `docs/design/plugin-framework-production-hardening-plan.md` P5 implementation steps and required smoke cases |
| P5-AC1: Complex sample plugins can be packaged with one Gradle command | Planned | TBD |
| P5-AC2: Sample host smoke starts the host, calls management APIs, verifies JPA examples, and shuts down | Planned | TBD |
| P5-AC3: Local management smoke uses a token and does not bypass security | Planned | TBD |
| P5-AC4: Actuator exposes trust summary, deployment summary, and cleanup report summary | Planned | TBD |
| P5-AC5: Metrics cover management requests, rejections, idempotency hits, deployment duration, and rollback count | Planned | TBD |
| P5-AC6: Failure smoke emits HTTP response, deployment record, and useful logs | Planned | TBD |

## P6 Follow-Up Decision Topics

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P6-AC1: JPA runtime refresh / EntityManagerFactory rebuild has a standalone decision | Planned | TBD |
| P6-AC2: Cross-datasource transactions have a standalone decision covering forbidden, Saga, Outbox, or optional XA module | Planned | TBD |
| P6-AC3: Plugin marketplace/repository governance has a standalone decision | Planned | TBD |
| P6-AC4: Management console UI scope has a standalone decision | Planned | TBD |

## Current Recommendation

Prioritize P1 and P2. Trust-chain verification and persistent records are the foundation for enforcement, audit recovery, management smoke, and production ENFORCE mode.
