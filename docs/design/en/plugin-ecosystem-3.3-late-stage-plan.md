# Plugin Ecosystem 3.3 Late-Stage Implementation Plan And Acceptance

## Scope

This document tracks the last three 3.3 goals:

1. E4 compatibility matrix and package verification.
2. E5 plugin repository/distribution.
3. E6 management console sample UI.

Design: [plugin-ecosystem-3.3-late-stage-design.md](plugin-ecosystem-3.3-late-stage-design.md).

## Phases

| Phase | Goal | Deliverable | Status |
| --- | --- | --- | --- |
| P0 | Late-stage design freeze | Chinese/English design, plan, and index links | Complete |
| P1 | E4 compatibility matrix docs | matrix format, rule list, WARN/ENFORCE semantics | Complete |
| P2 | E4 package check prototype | sample package report, host API bundling detection | Complete |
| P2.1 | E4.1 production compatibility precheck | trust manifest compatibility ranges connected to deployment precheck with WARN/ENFORCE | Complete |
| P3 | E5 offline repository dry-run | index schema, resolver, repository release to deployment plan | Complete |
| P4 | E5 repository replace integration | release copied to cache/staging and reuses replace/rollback | Complete |
| P5 | E6 console sample completion | replace, rollback, JPA reload, audit/error display | Complete |
| P6 | Late-stage smoke closure | package check, repository dry-run/replace, console UI smoke | Verified |

## P1 E4 Compatibility Matrix Docs

### Files

- `docs/design/plugin-loading-and-packaging.md`
- `docs/design/plugin-developer-guide.md`
- a new or expanded compatibility matrix document.
- English translations.

### Required Content

- Define matrix fields for `pf4boot`, `pf4boot-plugin`, Spring Boot, PF4J, JDK, and package format.
- Define WARN/ENFORCE rollout.
- Explain how old packages are allowed, warned, or rejected.

### Acceptance

- Developers can decide whether a plugin package fits the current host.
- Docs state `pf4boot-plugin 1.7.0` is the current 3.3 baseline.

## P2 E4 Package Check Prototype

### Possible Scope

- sample Gradle task or script.
- `samples/cross-plugin-jpa` verification report directory.
- later public models may go into `pf4boot-api` or `pf4boot-core`.

### Required Content

- Unzip plugin packages and inspect descriptor plus `lib/`.
- Detect host APIs bundled into plugin packages.
- Check checksum/trust manifest presence.
- Emit machine-readable JSON report.

### Acceptance

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

The build can generate or check a package verification report, and negative samples produce clear rule names.

### Delivered Result

- `samples/cross-plugin-jpa/demo-host` now has `verifySamplePluginPackages`.
- `assembleSamplePlugins` writes `build/reports/plugin-package-verification/result.json` after packaging.
- Current rules cover required descriptor fields, bundled host APIs, checksum sidecars, and trust manifest sidecars.
- ERROR rule failures fail the build. WARN rules are recorded but do not block the sample.

## P2.1 E4.1 Production Compatibility Precheck

### Required Content

- The trust manifest supports version ranges for pf4boot, pf4boot-plugin, Spring Boot, PF4J, JDK, and package format.
- Deployment precheck emits WARN or ERROR based on `plugin-compatibility-precheck-mode`.
- Both staged path and repository release flows end in `PluginDeploymentService.planReplacement(...)` and must not bypass compatibility precheck.

### Delivered Result

- `PluginTrustManifest` now includes `pf4jVersionRange`, `pf4bootPluginVersionRange`, `jdkVersionRange`, and `packageFormatVersionRange`.
- `DefaultPluginDeploymentService` checks all of these ranges in the compatibility precheck.
- `Pf4bootProperties` exposes actual-version configuration fields with defaults aligned to the 3.3 baseline.
- `.\gradlew.bat :pf4boot-core:test` covers WARN/ENFORCE behavior.

## P3 E5 Offline Repository Dry-Run

### Possible Scope

- `pf4boot-core` repository resolver.
- `pf4boot-management-starter` deployment request parsing.
- `samples/cross-plugin-jpa/repository/repository-index.example.json`.

### Required Content

- Parse `repository-index.json`.
- Resolve releases by `repositoryVersion` or `repositoryVersionRange`.
- Verify package paths remain under repository root.
- Produce deployment plan during dry-run without mutating runtime.

### Acceptance

- release not found returns stable error.
- path escape is rejected.
- checksum mismatch is rejected or warned based on config.
- direct staged path requests still work.

### Delivered Result

- `pf4boot-core` already provides the offline repository resolver for `repository-index.json`, `repositoryVersion`, `repositoryVersionRange`, path escape rejection, and checksum validation.
- `pf4boot-management-starter` deployment requests support repository release dry-run.
- `samples/cross-plugin-jpa/repository/repository-index.example.json` was updated to 3.3.0 examples and includes the compatibility matrix id.

## P4 E5 Repository Replace Integration

### Required Content

- Copy release package into controlled cache/staging.
- Reuse `PluginDeploymentService.replace(...)`.
- Deployment record stores repository id, release version, and safe summary.
- Rollback selects repository rollback candidate.

### Acceptance

- repository dry-run and replace use the same plan/precheck logic.
- replace failures reuse existing rollback.
- HTTP response does not leak cache absolute paths.

### Delivered Result

- `PluginDeploymentService` already exposes repository-release variants of `planReplacement` and `replace`.
- Repository releases are copied into controlled cache/staging and then reuse existing replace/precheck/rollback logic.
- The management API connects this capability through `repositoryVersion`, `repositoryVersionRange`, and `repositoryRollback`.

## P5 E6 Console Sample Completion

### Scope

- `samples/plugin-management-console`.
- optional README updates for the `cross-plugin-jpa` embedded console.
- no core/starter published module changes.

### Required Content

- plugin list and detail.
- deployment plan, replace, confirm, rollback.
- repository release dry-run.
- JPA reload plan, execute, record/current.
- Actuator governance/JPA reload summaries.
- 401/403, 409, precheck failed, manual intervention display.

### Acceptance

```powershell
.\gradlew.bat :samples:plugin-management-console:test
```

If headless UI smoke is added later:

```powershell
.\gradlew.bat :samples:plugin-management-console:uiSmoke
```

### Delivered Result

- The static sample UI now covers plugin list, lifecycle operations, deployment plan/replace/confirm/rollback, repository release dry-run/replace, JPA reload, Actuator summaries, and uniform error display.
- Contract tests cover key paths, token header, `X-Idempotency-Key`, repository fields, and the rule that tokens must not be persisted through `localStorage`.
- No starter-embedded UI was introduced; the UI remains an independent sample.

## P6 Late-Stage Smoke Closure

### Required Commands

```powershell
git diff --check
Select-String -Path docs\design\*.md,docs\design\en\*.md,samples\*\README.md -Pattern ([char]0xFFFD) -Encoding UTF8
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat :samples:plugin-management-console:test
```

### Current Verification Result

- `.\gradlew.bat :pf4boot-core:test`: passed.
- `.\gradlew.bat :pf4boot-management-starter:test`: passed.
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`: passed and generated the package verification report.
- `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke`: passed.
- `.\gradlew.bat :samples:plugin-management-console:test`: passed.

### Recommended Command

```powershell
.\gradlew.bat :samples:saga-outbox:app-run:runtimeSmoke
```

## Definition Of Done

E4-E6 are complete when:

1. The compatibility matrix helps developers determine runtime compatibility.
2. Official sample plugin packages can emit or pass package verification reports.
3. Repository release dry-run and replace reuse deployment service and do not bypass precheck/rollback.
4. Management console sample UI covers deployment, JPA reload, Actuator summaries, and error display.
5. Chinese design changes have matching English translations.
6. Required commands pass, or any environment failure records the command, reason, and next step.
