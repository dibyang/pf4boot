# Plugin Framework Follow-Up Hardening Design

## Background

P7-P9 are complete: offline repository dry-run, version-range prechecks, and Gradle runtime smoke are in place. The remaining work is not unfinished P7-P9 scope; it is the next independent enhancement batch:

1. Full execution from repository release to real replacement deployment.
2. Cross-platform runtime smoke runner and JUnit XML reporting.
3. A no-jpa/unrelated sample plugin to prove dependent plugin failures do not affect unrelated plugins.

This design groups those items as P10.

## Goals

- Allow an `offline-index` repository release to perform real replacement after dry-run verification, reusing authentication, idempotency, audit, trust checks, capability prechecks, and rollback.
- Provide a cross-platform smoke runner while keeping the PowerShell script as a Windows-friendly entry.
- Produce CI-friendly `result.json` and JUnit XML; preserve useful logs on failure.
- Add an unrelated sample plugin without JPA dependencies and verify it keeps working when a datasource provider or JPA consumer fails.
- Keep defaults compatible with existing deployment and sample startup behavior.

## Non-Goals

- No public plugin marketplace, remote HTTP download, object storage, release approval workflow, or account system.
- No cross-datasource atomic transactions.
- No JPA `EntityManagerFactory` online refresh.
- No management console UI.
- No root-level demo modules.

## Current State

| Area | Current State | P10 Gap |
| --- | --- | --- |
| Repository governance | `offline-index` can resolve releases and management supports plan/dry-run | Real replace, staging cache, rollback candidate enforcement, repository-source audit |
| Runtime smoke | Gradle `runtimeSmoke` wraps PowerShell and writes `result.json` | Cross-platform runner, JUnit XML, log retention policy, CI artifact contract |
| Sample isolation | Complex JPA sample covers cross-plugin transaction and rollback | Runtime comparison with a no-jpa/unrelated plugin |

## Core Constraints

- Keep Java 8 compatibility.
- Put public APIs in `pf4boot-api`, runtime code in `pf4boot-core`, management APIs in `pf4boot-management-starter`, and sample-only behavior under `samples/*`.
- Repository real replace must be disabled by default or explicitly non-dry-run.
- All write operations still use token auth, idempotency keys, audit, and path validation.
- Staging cache must use configured or runtime/build work directories only.
- Smoke reports must not contain tokens, private keys, full stacks, or sensitive absolute paths.

## Affected Modules

| Module | Responsibility |
| --- | --- |
| `pf4boot-api` | Add repository deployment request/result fields only if needed; prefer current models |
| `pf4boot-core` | Copy repository releases to staging cache, verify, execute replace, clean up |
| `pf4boot-management-starter` | Allow repository-sourced real replace with dry-run/idempotency/audit semantics |
| `pf4boot-actuator` | Read-only summary of recent repository execution and governance state |
| `samples/cross-plugin-jpa` | no-jpa/unrelated plugin, cross-platform smoke runner, JUnit XML |
| `docs/design` | P10 design, plan, acceptance tracking, and translations |

## Interface Design

### Repository Real Replace

`PluginDeploymentRequest` remains the management request model. Repository fields create a `PluginReleaseRequest`.

If a new API is required, add it as an additive method:

```java
DeploymentRecord replaceFromRepository(PluginReleaseRequest request, PluginDeploymentOptions options);
```

Rules:

| Rule | Behavior |
| --- | --- |
| `dryRun=true` | Resolve, verify, and plan only |
| `dryRun=false` and repository replace disabled | Return an explicit error |
| Release verification fails | Block staging and record a safe summary |
| Staging succeeds but replace fails | Reuse existing rollback orchestration |
| Idempotency replay | Return the first operation result without replacing again |

### Staging Cache

Repository releases must be copied into controlled staging before replace:

```text
{pluginRepositoryCacheDirectory}/operations/{operationId}/{pluginId}-{version}.zip
```

Requirements:

- `operationId` comes from the management operation or request hash.
- Recompute sha256 after copying.
- HTTP responses expose only summaries or a `stagingRef`, never absolute paths.
- Cleanup can keep recent operations or remove by operation completion in the first stage.

### Cross-Platform Smoke Runner

Recommended sample-only Java runner:

```text
samples/cross-plugin-jpa/app-run/src/smoke/java/.../RuntimeSmokeRunner.java
```

Gradle task:

```text
:samples:cross-plugin-jpa:app-run:runtimeSmoke
```

Parameters:

| Parameter | Default | Meaning |
| --- | --- | --- |
| `-Ppf4bootSmokePort=7791` | `7791` | Host port |
| `-Ppf4bootSmokeKeepWorkDir=true` | `false` | Keep work directory on success |
| `-Ppf4bootSmokeResultPath=...` | build reports path | `result.json` output |
| `-Ppf4bootSmokeJUnitPath=...` | build test-results path | JUnit XML output |

### no-jpa/unrelated Sample

Add sample modules such as:

```text
plugin-unrelated-api
plugin-unrelated-service
```

The plugin:

- Does not depend on the datasource provider.
- Does not declare JPA entity/repository packages.
- Exposes a simple HTTP endpoint or exported service.
- Remains available when a datasource provider is missing or a JPA consumer fails.

## Data Structures

Repository execution records should include safe summaries:

| Field | Meaning |
| --- | --- |
| `repositoryId` | Source repository |
| `releaseVersion` | Selected release version |
| `releaseSha256` | Package digest |
| `rollbackCandidate` | Whether rollback release exists |
| `stagingRef` | Internal staging reference, not an absolute path |

JUnit XML maps each check to a testcase:

| Check | Testcase |
| --- | --- |
| `hostReady` | `RuntimeSmoke.hostReady` |
| `workflowOk` | `RuntimeSmoke.workflowOk` |
| `workflowRollback` | `RuntimeSmoke.workflowRollback` |
| `managementIdempotency` | `RuntimeSmoke.managementIdempotency` |
| `unrelatedPluginAlive` | `RuntimeSmoke.unrelatedPluginAlive` |

## State Machine

Repository replace:

```text
REQUESTED
  -> AUTHORIZED
  -> RELEASE_RESOLVED
  -> PACKAGE_VERIFIED
  -> STAGED_TO_CACHE
  -> PLAN_READY
  -> REPLACE_RUNNING
  -> REPLACE_SUCCEEDED / REPLACE_FAILED
  -> ROLLBACK_RUNNING
  -> ROLLBACK_SUCCEEDED / MANUAL_INTERVENTION
```

Invalid transitions:

- Failed package verification must not reach staging.
- `dryRun=true` must not reach replacement.
- Idempotency replay must not execute replacement again.

## Sequences

### Repository Real Deployment

1. Management receives a repository replace request.
2. Authentication, idempotency, request hash, and parameter validation pass.
3. Offline index is resolved and a release is selected.
4. Release path, sha256, trust manifest, version range, and capabilities are verified.
5. Package is copied to staging cache and digest is recomputed.
6. A deployment plan is generated.
7. If `dryRun=false`, existing replace executes.
8. Failure triggers existing rollback.
9. Operation, deployment record, repository release summary, and cleanup result are recorded.

### no-jpa Isolation Smoke

1. Start the host with datasource provider, JPA consumer, and unrelated plugin.
2. Check the unrelated endpoint.
3. Trigger a JPA rollback and check the unrelated endpoint again.
4. Run provider-missing or provider-failure scenarios; JPA consumer failure is recorded while unrelated plugin remains available.
5. Write `result.json` and JUnit XML.

## Failure Handling

| Scenario | Behavior |
| --- | --- |
| Repository replace disabled | Return a safe error; allow dry-run only |
| Staging cache write fails | Fail closed, no replace |
| Cache digest mismatches index | Delete cache file and fail verification |
| Replace succeeds but record write fails | Follow existing audit fail-closed/manual intervention policy |
| Unrelated plugin fails | Smoke fails |
| JUnit XML write fails | Smoke fails but still tries to preserve `result.json` |

## Idempotency

- Repository replace reuses management idempotency keys.
- Same key and request hash returns the original result.
- Same key with different request hash returns conflict.
- Replay of an executed replace must not call PF4J replace again.

## Rollback

- P10 features are config-enabled; disabling them returns to P7-P9 behavior.
- Repository replace failures reuse existing rollback.
- Staging cache can be deleted safely without touching repository source files.
- The PowerShell script remains available for troubleshooting.

## Compatibility

- APIs are additive only.
- Existing staged-path plan/replace requests continue working.
- The `runtimeSmoke` task name remains stable.
- no-jpa sample modules live only under `samples/*`.

## Rollout

1. Implement no-jpa sample and smoke checks first.
2. Add Java smoke runner and JUnit XML while keeping PowerShell.
3. Enable repository real replace last, after sample validation.

## Verification

| Level | Coverage |
| --- | --- |
| Unit | Staging paths, digest recheck, idempotency replay, safe errors |
| Core | Repository release replace, rollback on failure, cache cleanup |
| Management | Dry-run vs real replace auth, idempotency, conflict, sanitization |
| Sample | no-jpa plugin startup and JPA-provider failure isolation |
| Smoke | Windows/non-Windows runner, `result.json`, JUnit XML, failure logs |

## Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Repository replace runs accidentally | Runtime plugin is replaced unexpectedly | Explicit config, dry-run default, idempotency, audit |
| Staging cache leaks paths | Deployment directories exposed | Return only summaries and `stagingRef` |
| Java runner diverges from PowerShell | CI and local results differ | Share check definitions and report schema |
| no-jpa sample grows too complex | Sample maintenance cost rises | Keep it focused on isolation only |

## Implementation Plan

Detailed tasks and acceptance tracking live in:

- [plugin-framework-follow-up-hardening-plan.md](plugin-framework-follow-up-hardening-plan.md)
- [plugin-framework-follow-up-hardening-acceptance.md](plugin-framework-follow-up-hardening-acceptance.md)

## Open Questions

- Repository real replace config name: prefer `spring.pf4boot.plugin-repository-replace-enabled=false` or an execution mode under repository config.
- Staging cache cleanup: start with operation-end cleanup and `keepWorkDir`; defer TTL background cleanup.
- Java smoke runner location: keep it in the sample to avoid making it a public framework API.
