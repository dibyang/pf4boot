# Plugin Framework Follow-Up Hardening Acceptance Tracking

## Usage

This file tracks acceptance status for P10-A/P10-B/P10-C from [plugin-framework-follow-up-hardening-plan.md](plugin-framework-follow-up-hardening-plan.md).

Statuses:

- `Planned`: planned but not implemented.
- `In Progress`: implementation in progress.
- `Done`: completed with evidence.
- `Blocked`: blocked by external conditions.

Only mark rows `Done` after implementation, documentation, and verification are complete.

## P10-A Repository Real Replace

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P10-A-AC1: configuration separates repository dry-run and real replace | Done | Added `pluginRepositoryReplaceEnabled`, disabled by default |
| P10-A-AC2: release package enters controlled staging cache and sha256 is rechecked | Done | `replace(PluginReleaseRequest)` verifies sha256 before copying to `pluginRepositoryCacheDirectory/operations/{operationId}` |
| P10-A-AC3: verification failure does not enter replace | Done | Core tests cover disabled real replace and repository precheck failure |
| P10-A-AC4: real replace reuses existing rollback orchestration | Done | Repository replace delegates to existing `replace(targetPluginId, stagedPath)` |
| P10-A-AC5: idempotency replay does not execute replace again | Done | Management repository replace uses the existing idempotency gate |
| P10-A-AC6: records and Actuator expose repository execution summaries without absolute paths | Done | Actuator exposes only `repositoryReplaceEnabled` and `repositoryCacheConfigured` booleans |

## P10-B Cross-Platform Runtime Smoke

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P10-B-AC1: `runtimeSmoke` task remains discoverable and keeps the same command | Done | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke` passed |
| P10-B-AC2: Java or cross-platform runner executes the full smoke | Done | Added `RuntimeSmokeRunner` |
| P10-B-AC3: success and failure both generate `result.json` | Done | Success report status is `PASSED` |
| P10-B-AC4: JUnit XML is generated and CI-collectable | Done | `build/test-results/runtimeSmoke/TEST-runtime-smoke.xml`, failures=`0` |
| P10-B-AC5: PowerShell script remains available as the Windows entry | Done | PowerShell script retained and aligned with P10 checks |
| P10-B-AC6: reports contain no tokens, private keys, full stacks, or sensitive absolute paths | Done | Report contains check names, status, and summary messages only |

## P10-C no-jpa/unrelated Isolation Sample

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P10-C-AC1: unrelated plugin does not depend on JPA starter or datasource provider | Done | Added `plugin-unrelated-service`, depending only on `pf4boot-api` and `pf4boot-web-support` |
| P10-C-AC2: unrelated plugin starts and responds in the normal scenario | Done | Runtime smoke outputs `SMOKE_UNRELATED_PLUGIN_ALIVE status=200` |
| P10-C-AC3: when JPA provider is missing, JPA consumer fails and unrelated plugin still works | Done | Runtime smoke stops `sample-demo-jpa-domain`; unrelated endpoint remains 200 |
| P10-C-AC4: when JPA provider startup fails, unrelated plugin still works | Done | Runtime provider-stop scenario records `jpaProviderIsolation=PASSED` |
| P10-C-AC5: runtime smoke report includes `unrelatedPluginAlive` | Done | `result.json` includes `unrelatedPluginAlive` and `jpaProviderIsolation` |
| P10-C-AC6: README and developer guide document isolation semantics | Done | Sample README and developer guide updated |

## Current Recommendation

P10 is complete. For JPA runtime refresh, start next with a `PLAN_ONLY` design for `JpaDomainReloadService` impact analysis.
