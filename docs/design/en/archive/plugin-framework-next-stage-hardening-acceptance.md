# Plugin Framework Next-Stage Production Hardening Acceptance Tracking

## Usage

This file tracks P7-P9 from [plugin-framework-next-stage-hardening-plan.md](plugin-framework-next-stage-hardening-plan.md). Suggested statuses:

- `Planned`: planned but not implemented.
- `In Progress`: currently being implemented.
- `Done`: complete with verification evidence.
- `Blocked`: blocked by external conditions, with explanation.

Only mark an item `Done` after code, docs, and verification are actually complete.

## P7 Offline Plugin Repository Governance

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P7-AC0: Design and plan define offline-index boundaries, APIs, index format, disabled defaults, and forbidden changes | Done | `docs/design/plugin-framework-next-stage-hardening.md`, `docs/design/plugin-framework-next-stage-hardening-plan.md` |
| P7-AC1: Repository public APIs and config fields compile | Done | Added `net.xdob.pf4boot.repository.*` and repository properties; ran `.\gradlew.bat :pf4boot-api:compileJava` |
| P7-AC2: Offline-index resolver loads valid indexes and rejects path traversal | Done | `OfflineIndexPluginRepositoryResolverTest.loadsValidIndex`, `rejectsPathTraversal`; ran `.\gradlew.bat :pf4boot-core:test` |
| P7-AC3: Package sha256 mismatch blocks staging | Done | `OfflineIndexPluginRepositoryResolverTest.rejectsPackageChecksumMismatch` |
| P7-AC4: Repository release converts to deployment plan, and dry-run does not change runtime state | Done | `DefaultPluginDeploymentServiceTest.planReplacementFromRepositoryRelease`, `repositoryReleaseRecordsSafeSummary` |
| P7-AC5: Management dry-run passes authorization, idempotency, and sanitization tests | Done | `PluginManagementControllerTest.repositoryPlanEndpointUsesReleaseRequestAndReplaysIdempotency`; ran `.\gradlew.bat :pf4boot-management-starter:test --tests "*PluginManagementControllerTest"` |
| P7-AC6: Actuator exposes read-only repository summary | Done | `Pf4bootGovernanceEndpointTest.summaryIncludesGovernanceConfigurationAndDeploymentMetrics`; ran `.\gradlew.bat :pf4boot-actuator:test` |
| P7-AC7: Sample provides repository-index example and usage docs | Done | `samples/cross-plugin-jpa/repository/repository-index.example.json`, sample README, developer guide |

## P8 Strict Version-Range Prechecks

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P8-AC0: Design and plan define range syntax, WARN/ENFORCE behavior, and error codes | Done | `docs/design/plugin-framework-next-stage-hardening.md`, `docs/design/plugin-framework-next-stage-hardening-plan.md` |
| P8-AC1: Version range public APIs compile | Done | Added `net.xdob.pf4boot.version.*`; ran `.\gradlew.bat :pf4boot-api:compileJava` |
| P8-AC2: Default matcher covers exact versions, bounded ranges, open ranges, and invalid expressions | Done | `DefaultVersionRangeMatcherTest`; ran `.\gradlew.bat :pf4boot-core:test` |
| P8-AC3: Unsatisfied capability `versionRange` produces a deployment check | Done | `PluginCapabilityPrecheckTest.rejectsCapabilityVersionOutsideRange`, `warnsInvalidCapabilityVersionRange` |
| P8-AC4: Unsatisfied `pf4bootVersionRange` warns or rejects by mode | Done | `DefaultPluginDeploymentServiceTest.planWarnsPf4bootVersionMismatch` |
| P8-AC5: Unsatisfied `springBootVersionRange` warns or rejects by mode | Done | `DefaultPluginDeploymentServiceTest.planRejectsSpringBootVersionMismatchInEnforceMode` |
| P8-AC6: Sample manifests and developer guide include range syntax and migration notes | Done | Bilingual developer guide includes syntax, config, and WARN-to-ENFORCE guidance |

## P9 Gradle/CI Runtime Smoke

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P9-AC0: Design and plan define Gradle task, report paths, CI exit codes, and security constraints | Done | `docs/design/plugin-framework-next-stage-hardening.md`, `docs/design/plugin-framework-next-stage-hardening-plan.md` |
| P9-AC1: `:samples:cross-plugin-jpa:app-run:runtimeSmoke` is visible to Gradle | Done | Ran `.\gradlew.bat :samples:cross-plugin-jpa:app-run:tasks --all`; output includes `runtimeSmoke` |
| P9-AC2: Successful runtime smoke exits 0 and generates `result.json` | Done | Ran `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke`; output included all `SMOKE_*` markers and `result.json` status `PASSED` |
| P9-AC3: Failed runtime smoke exits non-zero, reports failed checks, and prints log tail | Done | Ran `runtime-smoke.ps1 -SkipAssemble -TimeoutSeconds 0 -ResultPath ...failure-result.json`; report status is `FAILED` and contains a failed `runtimeSmoke` check |
| P9-AC4: Reports do not contain tokens, private keys, full stacks, or sensitive absolute paths | Done | Success report contains only check names, statuses, HTTP summaries, and error codes; failure report contains only `PFS-001 host not ready within 0 seconds` |
| P9-AC5: README and developer guide provide local/CI commands and troubleshooting | Done | Sample README and bilingual developer guide |

## Current Recommendation

P7-P9 are complete. Future remote repositories, a cross-platform Java smoke runner, JUnit XML, or fuller semantic-version comparison should use separate plans and acceptance items.
