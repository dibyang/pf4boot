# Plugin Framework Follow-Up Hardening Implementation Plan

## Scope

This plan implements [plugin-framework-follow-up-hardening.md](plugin-framework-follow-up-hardening.md) and tracks three P10 enhancement streams:

- P10-A repository release to real replace.
- P10-B cross-platform runtime smoke runner and JUnit XML.
- P10-C no-jpa/unrelated isolation sample.

P7-P9 are complete and are not reopened by this plan.

## Principles

- Commit each sub-stage separately.
- Preserve defaults; real replace and new smoke behavior must be explicit or keep old entry points.
- Sample code stays under `samples/*`.
- Mark acceptance rows `Done` only after commands are actually run.
- Keep Chinese and English docs synchronized.

## Stage Overview

| Stage | Topic | Status | Deliverables |
| --- | --- | --- | --- |
| P10-A | Repository real replace | Planned | staging cache, repository replace, audit summary, rollback verification |
| P10-B | Cross-platform runtime smoke | Planned | Java runner, JUnit XML, shared report schema |
| P10-C | no-jpa/unrelated isolation sample | Planned | unrelated plugin, failure scenarios, runtime smoke checks |

## Dependencies

| Task | Depends On | Notes |
| --- | --- | --- |
| P10-A | P7, P8 | Repository release and compatibility precheck already support plan |
| P10-B | P9 | Reuse `runtimeSmoke` task name and `result.json` schema |
| P10-C | complex JPA sample, optional P10-B | Can first verify through current PowerShell smoke |

## P10-A Repository Real Replace

### Goal

Move repository releases from dry-run to real replacement while keeping default safety: real replacement only when explicitly enabled.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P10-A1 | Define repository replace configuration and options | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P10-A2 | Implement staging cache copy, digest recheck, and cleanup | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*Repository*"` |
| P10-A3 | Connect real replace and rollback | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginDeploymentServiceTest*"` |
| P10-A4 | Add management API support for repository real replace | `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P10-A5 | Add repository execution summaries to records and Actuator | `pf4boot-core`, `pf4boot-actuator` | `.\gradlew.bat :pf4boot-actuator:test` |
| P10-A6 | Document real replace examples | `samples/cross-plugin-jpa`, `docs/design` | Doc check |

### Forbidden Changes

- Do not add remote downloads.
- Do not expose staging cache absolute paths.
- Do not bypass management auth, idempotency, or audit.
- Do not let dry-run requests perform replacement.

### Exit Criteria

- Dry-run and real replace are clearly separated by configuration.
- Failed release verification cannot enter staging.
- Replace failure after staging enters existing rollback.
- Idempotency replay does not execute replace again.

## P10-B Cross-Platform Runtime Smoke

### Goal

Keep the `runtimeSmoke` Gradle entry and run it through a cross-platform runner that writes `result.json` and JUnit XML for CI.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P10-B1 | Extract smoke checks and report schema | `samples/cross-plugin-jpa` | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:tasks --all` |
| P10-B2 | Implement Java runtime smoke runner | `samples/cross-plugin-jpa/app-run` | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke` |
| P10-B3 | Generate JUnit XML | `samples/cross-plugin-jpa/app-run` | Check `build/test-results/runtimeSmoke/*.xml` |
| P10-B4 | Align PowerShell and Java runner report schemas | `samples/cross-plugin-jpa/scripts` | Windows smoke |
| P10-B5 | Document CI artifact collection and troubleshooting | `samples/cross-plugin-jpa`, `docs/design` | Doc check |

### Forbidden Changes

- Do not delete the PowerShell script.
- Do not depend on browsers, external networks, or private user directories.
- Do not turn the smoke runner into a public framework API.

### Exit Criteria

- `runtimeSmoke` runs on Windows.
- Non-Windows has a clear runner path and clear dependency errors.
- Success and failure both generate `result.json`.
- JUnit XML can be collected by CI.

## P10-C no-jpa/unrelated Isolation Sample

### Goal

Add an unrelated plugin without datasource/JPA dependencies and prove JPA provider or consumer failures do not affect it.

### Tasks

| ID | Task | Modules | Verification |
| --- | --- | --- | --- |
| P10-C1 | Design no-jpa/unrelated sample module boundaries | `samples/cross-plugin-jpa` | Doc check |
| P10-C2 | Add unrelated API/service plugins | `samples/cross-plugin-jpa` | `.\gradlew.bat :samples:cross-plugin-jpa:assembleSampleRuntime` |
| P10-C3 | Add unrelated HTTP endpoint or exported service | `samples/cross-plugin-jpa` | Runtime smoke |
| P10-C4 | Add provider-missing/failure runtime comparison | `samples/cross-plugin-jpa` | Runtime smoke failure scenario |
| P10-C5 | Document isolation semantics | `samples/cross-plugin-jpa`, `docs/design` | Doc check |

### Forbidden Changes

- Do not put entities or unrelated business logic in datasource provider plugins.
- Do not make unrelated plugins depend on `pf4boot-jpa-starter`.
- Do not change production startup defaults for the demo.

### Exit Criteria

- Unrelated plugin starts and responds in the normal scenario.
- When JPA provider is missing or fails, JPA consumer failure is recorded and unrelated plugin still works.
- Smoke report includes `unrelatedPluginAlive=PASSED`.

## Recommended Order

1. P10-C first: establish runtime isolation baseline.
2. P10-B second: make the verification cross-platform and CI-friendly.
3. P10-A last: enable real repository deployment after the stronger sample verification exists.

## Definition Of Done

- Java 8 compatibility is preserved.
- Chinese and English docs are synchronized.
- Required Gradle verification commands are run.
- Acceptance rows contain real command evidence.
- Local commit messages clearly describe the stage scope.
