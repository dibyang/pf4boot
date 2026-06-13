# Plugin Framework Next-Stage Production Hardening Implementation Plan

## Scope

This plan tracks [plugin-framework-next-stage-hardening.md](plugin-framework-next-stage-hardening.md):

- P7 offline plugin repository governance.
- P8 strict version-range prechecks.
- P9 Gradle/CI runtime smoke.

The P0-P6 production-hardening track remains complete. This is a separate follow-up plan.

## Principles

- One task card changes one clear boundary.
- Keep defaults disabled or warning-only.
- Add models and unit tests before wiring deployment, management, and sample smoke.
- Update Chinese docs and English translations when public APIs, config, HTTP responses, or sample commands change.
- Mark acceptance items `Done` only after actual verification.

## Overview

| Phase | Topic | Status | Main Deliverables |
| --- | --- | --- | --- |
| P7 | Offline plugin repository governance | Done | repository API, offline-index resolver, dry-run, sample index |
| P8 | Strict version-range prechecks | Done | version range matcher, pf4boot/spring/capability checks, error codes |
| P9 | Gradle/CI runtime smoke | Done | Gradle `runtimeSmoke` task, `result.json`, CI docs |

## Task Card Template

| Field | Requirement |
| --- | --- |
| Task ID | Use IDs such as `P7-1a` |
| Input files | List design, code, and tests to read first |
| Allowed edits | State modules and packages |
| Forbidden edits | State behavior or security boundaries that must not change |
| Evidence | Test command, doc check, or smoke output |
| Rollback | Feature remains disabled by default; each task is independently revertible |

## Dependencies

| Task | Depends On | Notes |
| --- | --- | --- |
| P7 | P1, P2, P5 | Reuses trust manifests, deployment records, and management smoke |
| P8 | P4 | Reuses capability manifests and deployment prechecks |
| P9 | P5 | Reuses the stable runtime smoke script and sample runtime |

## P7 Offline Plugin Repository Governance

### Goal

Support local or mounted offline-index repositories so plugin releases can be indexed, verified, resolved, dry-run, and audited. Disabled by default.

### Steps

1. Add public models under `pf4boot-api/src/main/java/net/xdob/pf4boot/repository/`:
   - `PluginRepositoryResolver`
   - `PluginRepositoryIndex`
   - `PluginReleaseRecord`
   - `PluginReleaseRequest`
   - `PluginRepositoryResolution`
   - `PluginRepositoryStatus`
2. Add `Pf4bootProperties` fields:
   - `pluginRepositoryEnabled`
   - `pluginRepositoryType`
   - `pluginRepositoryLocation`
   - `pluginRepositoryTrustMode`
   - `pluginRepositoryCacheDirectory`
3. Add `OfflineIndexPluginRepositoryResolver` in `pf4boot-core`.
4. Connect repository release resolution to staging and existing deployment plans.
5. Add repository dry-run or plan support in `pf4boot-management-starter`.
6. Add repository summary to `pf4boot-actuator`.
7. Add sample offline index docs and a minimal sample index.

### Required Tests

| Test Class | Cases |
| --- | --- |
| `OfflineIndexPluginRepositoryResolverTest` | `disabledRepositoryDoesNotLoadIndex`, `loadsValidIndex`, `rejectsPathTraversal`, `rejectsPackageChecksumMismatch`, `selectsExactVersion`, `selectsRollbackCandidate` |
| `DefaultPluginDeploymentServiceTest` | `planReplacementFromRepositoryRelease`, `repositoryReleaseRecordsSafeSummary` |
| `PluginManagementControllerTest` | `repositoryDryRunRequiresWriteAuthorization`, `repositoryDryRunReplaysIdempotencyKey` |
| `Pf4bootGovernanceEndpointTest` | `exposesRepositorySummaryWhenResolverAvailable` |

### Forbidden

- Do not add remote HTTP downloads, object storage SDKs, or central services.
- Do not replace PF4J descriptors.
- Do not store tokens, private keys, or sensitive absolute paths in responses, logs, or indexes.
- Do not enable repository governance by default.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P7-1 | Define repository API and config fields | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P7-2 | Implement offline-index resolver | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*OfflineIndexPluginRepositoryResolverTest*"` |
| P7-3 | Wire deployment plan/staging | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginDeploymentServiceTest*"` |
| P7-4 | Add management repository dry-run/plan | `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P7-5 | Add Actuator repository summary | `pf4boot-actuator` | `.\gradlew.bat :pf4boot-actuator:test` |
| P7-6 | Add sample index and guide | `samples/cross-plugin-jpa`, `docs/design` | sample packaging and doc checks |

### Small Task Cards

| ID | Inputs | Allowed Edits | Key Steps | Evidence |
| --- | --- | --- | --- | --- |
| P7-1a | `PluginDeploymentService`, `DeploymentPlan`, `Pf4bootProperties` | `pf4boot-api` | Add POJOs/SPI, JavaDoc, null-safe defaults | API compile |
| P7-2a | `DefaultPluginTrustManifestLoader`, core test style | `pf4boot-core` | JSON parse, schema validation, relative-path checks, checksum checks | targeted test |
| P7-2b | `PluginCapabilityPrecheck`, version matcher if present | `pf4boot-core` | Select exact-version release; versionRange selection can follow P8 | targeted test |
| P7-3a | `DefaultPluginDeploymentService` | `pf4boot-core` | release -> staged path -> plan; dry-run does not start/replace plugins | deployment service test |
| P7-4a | `PluginManagementController`, request factory, security tests | `pf4boot-management-starter` | authorization, idempotency, sanitization, error codes | management starter test |
| P7-5a | `Pf4bootGovernanceEndpoint` | `pf4boot-actuator` | read-only repository summary and resolver warning handling | actuator test |
| P7-6a | sample README and bilingual developer guide | `samples/cross-plugin-jpa`, `docs/design` | repository-index example and dry-run steps | doc diff and U+FFFD check |

### Exit Criteria

- Disabled defaults keep historical behavior.
- Valid offline-index releases resolve; path traversal and checksum mismatch are rejected.
- Management dry-run shows repository release to deployment plan checks.
- Actuator summary is read-only.

## P8 Strict Version-Range Prechecks

### Goal

Implement unified version-range parsing and matching for `pf4bootVersionRange`, `springBootVersionRange`, and `capability.versionRange`. Defaults remain disabled or warning-only.

### Steps

1. Add public models under `pf4boot-api/src/main/java/net/xdob/pf4boot/version/`.
2. Add `DefaultVersionRangeMatcher` in `pf4boot-core`.
3. Extend `PluginCapabilityPrecheck` to compare provider capability versions.
4. Check framework and Spring Boot ranges during deployment precheck.
5. Add compatibility precheck properties.
6. Update sample manifests.
7. Update the developer guide.

### Required Tests

| Test Class | Cases |
| --- | --- |
| `DefaultVersionRangeMatcherTest` | `matchesExactVersion`, `matchesInclusiveExclusiveRange`, `matchesOpenEndedRange`, `rejectsInvalidRange`, `comparesQualifiedVersionsDeterministically` |
| `PluginCapabilityPrecheckTest` | `rejectsCapabilityVersionOutsideRange`, `warnsInvalidCapabilityVersionRange` |
| `DefaultPluginDeploymentServiceTest` | `planWarnsPf4bootVersionMismatch`, `planRejectsSpringBootVersionMismatchInEnforceMode` |
| `DefaultPluginTrustManifestLoaderTest` | `loadsFrameworkCompatibilityRanges` |

### Forbidden

- Do not add Maven Artifact Resolver or OSGi version libraries as mandatory dependencies.
- Do not block historical plugins by default.
- Do not claim full semver support.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P8-1 | Define version range API | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P8-2 | Implement parser/matcher | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultVersionRangeMatcherTest*"` |
| P8-3 | Wire capability versionRange | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*PluginCapabilityPrecheckTest*"` |
| P8-4 | Wire pf4boot/spring range checks | `pf4boot-core`, `pf4boot-starter` | deployment service tests |
| P8-5 | Update sample manifests and guide | `samples/cross-plugin-jpa`, `docs/design` | sample packaging and doc checks |

### Small Task Cards

| ID | Inputs | Allowed Edits | Key Steps | Evidence |
| --- | --- | --- | --- | --- |
| P8-1a | `PluginCapabilityRequirement`, `PluginCapabilityPrecheckResult` | `pf4boot-api` | Add version package; preserve existing result semantics | API compile |
| P8-2a | core tests | `pf4boot-core` | Parse ranges, compare versions, return structured invalid results | matcher test |
| P8-3a | `PluginCapabilityPrecheck` | `pf4boot-core` | Check versionRange after name/attribute match | capability precheck test |
| P8-4a | `DefaultPluginDeploymentService`, `Pf4bootProperties` | `pf4boot-api`, `pf4boot-core`, `pf4boot-starter` | Convert framework/Spring mismatch to deployment check | deployment service test |
| P8-5a | trust manifest examples and bilingual guide | `samples/cross-plugin-jpa`, `docs/design` | syntax, WARN-to-ENFORCE migration, troubleshooting | doc check |

### Exit Criteria

- Parser covers common Maven-style ranges.
- Capability provider version mismatches are detected.
- Framework/Spring Boot mismatches warn or reject by mode.
- Historical plugins are not blocked by default.

## P9 Gradle/CI Runtime Smoke

### Goal

Wrap the existing runtime smoke in a stable Gradle task with machine-readable reports and clear exit codes.

### Steps

1. Add a `runtimeSmoke` task in `samples/cross-plugin-jpa/app-run/build.gradle`.
2. Depend on `assembleSampleRuntime` and reuse `scripts/runtime-smoke.ps1 -SkipAssemble`.
3. Support Gradle properties:
   - `-Ppf4bootSmokePort=7791`
   - `-Ppf4bootSmokeKeepWorkDir=true`
   - `-Ppf4bootSmokeSkipAssemble=true`
4. Update the smoke script to write `result.json` on success and failure.
5. Optionally generate JUnit XML.
6. Update sample README, developer guide, and acceptance docs.

### Required Scenarios

| Scenario | Acceptance |
| --- | --- |
| Task success | Gradle exits 0 and report has `status=PASSED` |
| Host startup failure | Gradle exits non-zero, report has `hostReady=FAILED`, log tail printed |
| Management auth failure | Report includes sanitized security error code |
| Idempotency replay | Report has `managementIdempotency=PASSED` |
| Cleanup | Processes are cleaned on success and failure; failure keeps logs |

### Forbidden

- Do not disable management token checks or change default security.
- Do not put broken plugin packages in the startup scan directory.
- Do not depend on browsers, external networks, or global user directories.
- Do not treat runtime smoke as a normal unit test; keep it as an explicit task.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P9-1 | Add `runtimeSmoke` Gradle task | `samples/cross-plugin-jpa/app-run` | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:tasks --all` |
| P9-2 | Add `result.json` output | `samples/cross-plugin-jpa/scripts` | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke` |
| P9-3 | Add optional JUnit XML or CI docs | `samples/cross-plugin-jpa`, `docs/design` | report check |
| P9-4 | Update guide and README | `docs/design`, sample README | doc check |

### Small Task Cards

| ID | Inputs | Allowed Edits | Key Steps | Evidence |
| --- | --- | --- | --- | --- |
| P9-1a | `app-run/build.gradle`, runtime smoke script | `samples/cross-plugin-jpa/app-run` | Exec task, parameter pass-through, fixed output dirs | tasks --all |
| P9-2a | `runtime-smoke.ps1` | `samples/cross-plugin-jpa/scripts` | Structured checks, finally writes report, failure exits non-zero | runtimeSmoke |
| P9-2b | smoke report sample | `samples/cross-plugin-jpa` | Ensure report has no token, sensitive path, or stack | report check |
| P9-3a | CI docs | `docs/design`, sample README | Recommended CI command, failure triage, artifact collection | doc check |

### Exit Criteria

- One Gradle command runs runtime smoke.
- Success and failure both produce `result.json`.
- CI can use the task exit code and report.
- The existing PowerShell script remains directly runnable.

## Recommended Order

1. P8 first, because the matcher is small and supports P7 release selection.
2. P7 second, because repository governance depends on version selection and deployment prechecks.
3. P9 third, so P7/P8 sample verification can join the Gradle smoke.

P9-1/P9-2 can be done earlier for CI value, but reports must not mark unimplemented P7/P8 checks as passed.

## Definition of Done

- Java 8 compatibility is preserved.
- Chinese docs and English translations are updated.
- Acceptance entries contain real command evidence.
- Minimal Gradle verification ran or the failure reason is recorded.
- Local commits remain stage-scoped and clear.
