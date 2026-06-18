# Plugin Developer Experience 3.3 Implementation Plan And Acceptance

## Scope

This document tracks only the first three 3.3 goals:

1. Rewrite the official plugin developer guide.
2. Align with the `pf4boot-plugin 1.7.0` baseline.
3. Organize official templates and complex samples.

The last three 3.3 goals are included in [plugin-ecosystem-3.3-roadmap.md](plugin-ecosystem-3.3-roadmap.md), but are not expanded here.

## Phases

| Phase | Goal | Deliverable | Status |
| --- | --- | --- | --- |
| P0 | Planning freeze | Roadmap, design, plan, and English translations | Done |
| P1 | Developer guide structure rewrite | New `plugin-developer-guide.md` structure and English translation | Done |
| P2 | 1.7.0 capability baseline table | Capability table, sample usage points, verification commands | Done |
| P3 | Template matrix | Developer guide template matrix and sample README template notes | Done |
| P4 | Sample docs cleanup | Aligned READMEs for `cross-plugin-jpa`, `saga-outbox`, and console | Done |
| P5 | Acceptance closure | Links, encoding checks, sample verification commands | Done |

## P1 Developer Guide Rewrite

### Files

- `docs/design/plugin-developer-guide.md`
- `docs/design/en/plugin-developer-guide.md`
- Update `plugin-loading-and-packaging.md` and its translation only if cross references need to change.

### Required Content

- Add a quick start path.
- Split by plugin type: service, web, JPA domain, JPA consumer, workflow, management client.
- Add dependency scope decision table and counterexamples.
- Add JPA layering and multi-domain package scanning.
- Add operations entry points for management, hot replacement, JPA reload, and package verification.
- Add troubleshooting tables.

### Acceptance

| Item | Standard |
| --- | --- |
| Structure | New developers can start from quick start and move into a plugin type |
| Scopes | Each scope has allowed and forbidden scenarios |
| JPA | Provider has no entity/repository/service; consumer binds packages to EMF/TM |
| Operations | Management API, hot replacement, rollback, and JPA reload are linked |
| English sync | English translation matches the Chinese structure |

## P2 `pf4boot-plugin 1.7.0` Baseline Alignment

### Files

- `docs/design/plugin-developer-guide.md`
- `docs/design/plugin-loading-and-packaging.md`
- matching files under `docs/design/en/`
- sample `build.gradle` only if old or misleading style is found.

### Required Content

- Add a capability baseline table.
- Confirm root `build.gradle` uses `pf4boot-plugin 1.7.0`.
- List official sample usages of `net.xdob.pf4boot-plugin`.
- Explain the recommendation and compatibility rules for `plugin.properties` and Gradle DSL.
- If 1.7.0 has template/check tasks, prefer them in docs; if this repository cannot confirm them, mark as to verify.

### Acceptance

| Item | Standard |
| --- | --- |
| Version baseline | Docs state `pf4boot-plugin 1.7.0` as the 3.3 baseline |
| Usage points | Sample plugin declarations are checkable with `rg` |
| Capability boundary | Confirmed facts and unverified capabilities are separated |
| External boundary | The external `pf4boot-plugin` repository is not modified |

## P3 Official Templates And Complex Samples

### Files

- `docs/design/plugin-developer-guide.md`
- `samples/cross-plugin-jpa/README.md`
- `samples/saga-outbox/README.md`
- `samples/plugin-management-console/README.md`
- English design translations under `docs/design/en/`; sample READMEs do not need new English versions unless the repository already has them.

### Required Content

- Add the template matrix to the developer guide.
- Mark each `cross-plugin-jpa` module as a template source where appropriate.
- Mark `saga-outbox` as a compensation consistency sample, not XA.
- Mark `plugin-management-console` as a management API consumer sample.
- Mark demo-only code and template-safe code.
- Provide minimal verification commands for each sample README.

### Acceptance

| Item | Standard |
| --- | --- |
| Template matrix | Six templates have source, dependencies, and verification commands |
| Sample responsibility | Every sample README states goals and non-goals |
| demo-only notes | Runtime scripts, hardcoded ports, and test data are clearly labeled |
| Verification commands | Commands can run in this repository or explicitly state dependencies |

## P4 Sample Docs Cleanup

### Required Content

- Remove or correct stale version numbers, old module names, and old root demo references in sample READMEs.
- Ensure `samples/*/build` outputs are not referenced as source paths.
- Check `settings.gradle` sample modules against README module lists.

### Acceptance

```powershell
rg -n "demo-app|demo-lib|plugin1|plugin2|root-level|根级" docs samples -g "*.md"
```

Results must not describe old root demos as current sample entry points.

## P5 Acceptance Closure

### Required Checks

```powershell
git diff --check
Select-String -Path docs\design\*.md,docs\design\en\*.md,samples\*\README.md -Pattern ([char]0xFFFD) -Encoding UTF8
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:plugin-management-console:test
```

### Recommended Checks

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat :samples:saga-outbox:app-run:runtimeSmoke
```

## Definition Of Done

E1-E3 are complete when:

1. `plugin-developer-guide.md` has been rewritten around task paths.
2. The `pf4boot-plugin 1.7.0` capability baseline table is complete.
3. The official template matrix is part of the developer guide.
4. The three sample READMEs explain template sources, goals, non-goals, and verification commands.
5. Chinese and English design docs are synchronized.
6. Required checks pass, or any environment failure is reported with command, reason, and next step.

## Current Implementation Status

| Item | Status | Evidence |
| --- | --- | --- |
| 3.3 roadmap | Done | `plugin-ecosystem-3.3-roadmap.md` and English translation |
| First three-topic design | Done | `plugin-developer-experience-3.3-design.md` and English translation |
| First three-topic plan | Done | this document and English translation |
| Developer guide rewrite | Done | `plugin-developer-guide.md` rewritten around quick start, 1.7.0 baseline, template matrix, plugin types, JPA, operations, and troubleshooting |
| 1.7.0 baseline | Done | developer guide records root `pf4boot-plugin:1.7.0` and sample usage points |
| Sample organization | Done | `samples/cross-plugin-jpa/README.md`, `samples/saga-outbox/README.md`, and `samples/plugin-management-console/README.md` now include template positioning |
| Acceptance closure | Done | `git diff --check`, UTF-8 replacement check, `assembleSamplePlugins`, console test, and both runtime smoke tasks passed |

## Executed Acceptance

```powershell
git diff --check
Select-String -Path docs\design\*.md,docs\design\en\*.md,samples\*\README.md -Pattern ([char]0xFFFD) -Encoding UTF8
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:plugin-management-console:test
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat :samples:saga-outbox:app-run:runtimeSmoke
```
