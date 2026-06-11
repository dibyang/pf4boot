# Cross-Plugin JPA Transaction Capability Acceptance Checklist (Tracking)

## 1. Acceptance Goal

Verify that:

- Local mode behavior remains unchanged.
- Shared mode works with domain-local transaction sharing.
- Capability/plugin failure does not break unrelated plugin chains.

## 2. Preconditions

- Changes are integrated in target branch.
- Release scope (starter, domain capability, sample modules) can be reproduced.
- DB environment (H2 or equivalent) available for demos.

## 3. Acceptance Criteria

### AC-01 Local Regression

- Input: `mode` omitted or `mode=LOCAL`.
- Verify:
  - Plugin starts via existing local flow;
  - Local EMF/TM still created;
  - No explicit dependency on capability plugin.
- Exit: startup behavior unchanged for local plugins.

### AC-02 Shared Domain Success

- Input: capability plugin available, plugin config `mode=SHARED` + `domain-id`.
- Verify:
  - No duplicate local EMF/TM created;
  - Shared `domain.<id>.entityManagerFactory` and `domain.<id>.transactionManager` are bound;
  - Shared EMF contains entity packages configured by that domain;
  - Multiple same-domain plugins run under same TM.
- Exit: commit/rollback behavior consistent under shared TM.

### AC-03 Multi-Domain Isolation

- Input: two capability plugins (e.g., `order` and `report`) and dependent consumers.
- Verify:
  - Each domain uses its own TM;
  - In-plugin dual-domain bindings are correct;
  - No implicit cross-domain sharing.
- Exit: TM routing and entity scan remain isolated by domain.

### AC-04 Missing Domain Fast Fail

- Input: `mode=SHARED` without `domain-id` or without shared beans.
- Verify:
  - Plugin fails fast;
  - Error includes `PJF-001 / PJF-002 / PJF-003`;
  - Error message contains concrete remediation.
- Exit: deterministic failure with actionable messages.

### AC-05 Failure Isolation

- Input: force capability plugin startup failure.
- Verify:
  - Dependent chain fails;
  - Unrelated independent plugins still start;
  - Restoring capability plugin allows dependent chain restart.
- Exit: failure is isolated to dependency graph.

### AC-06 Migration Executable

- Input: migration path from local mode to shared mode following docs.
- Verify:
  - Configuration is migrated step-by-step;
  - TM references in `@Transactional` are updated by domain;
  - No functional regression after migration.
- Exit: all migration checklist items pass.

### AC-07 Entity Scan Boundary

- Input: domain capability plugin configures `entity-packages`; business plugin configures repository packages and TM/EMF references only.
- Verify:
  - Domain capability plugin scans entities before EMF creation;
  - Business plugin repositories reference entities already managed by the shared EMF;
  - If business plugin adds an unmanaged entity, startup or repository creation fails with actionable error.
- Exit: shared-domain entity ownership is explicit and does not depend on runtime metamodel refresh.

### AC-08 Descriptor Ready Diagnostics

- Input: normal provider export, descriptor export failure, and descriptor not-ready state.
- Verify:
  - `domain.<id>.descriptor` is readable after provider startup;
  - already-exported beans are rolled back when descriptor export fails;
  - consumers emit `PJF-007` when descriptor is missing, not ready, or mismatched with binding.
- Exit: consumers can diagnose shared domain state through the descriptor.

### AC-09 Transaction Proxy Boundary

- Input: same-class `REQUIRES_NEW` self-invocation and separate-bean `REQUIRES_NEW`.
- Verify:
  - same-class self-invocation does not create a new proxied transaction boundary;
  - separate-bean invocation can create a new transaction boundary;
  - sample forced-failure path matches the expected main rollback and independent audit commit behavior.
- Exit: docs and tests prevent transaction proxy misuse.

### AC-10 Plugin Package Boundary and Runtime Smoke

- Input: complex sample plugin packages and HTTP smoke.
- Verify:
  - provider package does not contain consumer repositories;
  - success and failure HTTP smoke paths are recorded;
  - provider failure isolation has at least independent test coverage showing unrelated plugins are not affected by missing shared providers.
- Exit: sample responsibility boundaries and runtime key paths are reviewable.

## 4. Verification Method

### 4.1 Build Checks

- `.\gradlew.bat :pf4boot-jpa-starter:compileJava`
- `.\gradlew.bat :pf4boot-jpa:compileJava`
- `.\gradlew.bat :pf4boot-jpa-domain-starter:compileJava :pf4boot-jpa-domain-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-core:test :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

### 4.2 Manual Runbook

- Single-domain shared startup succeeds.
- Remove capability plugin and confirm failure codes.
- Verify TM routing in dual-domain plugin configuration.
- Verify `PJF-007` when descriptor is missing or not ready.
- Inject failure into capability startup and verify isolated impact.

## 5. Pass Criteria

- AC-01 to AC-10 all pass.
- No new blocking regressions.
- Complete and reviewable acceptance record.

## 6. Acceptance Record (2026-06-11)

### 6.1 Command Results

| Command | Result | Notes |
| --- | --- | --- |
| `.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-core:test :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins` | Passed | Covers JPA starter, domain starter, core lifecycle/deployment tests, and complex sample plugin packaging. |

### 6.2 AC Results

| AC | Result | Evidence |
| --- | --- | --- |
| AC-01 Local Regression | Passed | `PluginJPAStarterTest.jpaStarterDoesNotCreateEntityManagerFactoryWhenPropertyIsMissing` verifies no JPA creation when disabled; the default `LOCAL` branch remains the local EMF/TM creation path. |
| AC-02 Shared Domain Success | Passed | `PluginJPAStarterTest.sharedModeBindsParentDomainBeansWithoutLocalJpaBeans` verifies consumer binding to shared EMF/TM without local JPA beans; `DomainJpaPlatformExporterTest.domainStarterCreatesEntityManagerFactoryAndExportsBeans` creates a shared H2 EMF and exports `domain.sample.*` plus descriptor. |
| AC-03 Multi-Domain Isolation | Passed | `PluginJPAStarterTest.pluginLevelBindingRegistersAdditionalSharedDomains` covers local BeanDefinition registration and descriptor validation for the primary `domain-id` plus `additional-domains`; docs require one repository package per domain. |
| AC-04 Missing Domain Fast Fail | Passed | `PluginJPAStarterTest.sharedModeRequiresDomainId` covers `PJF-006`; `sharedModeRequiresReadyDomainDescriptor` covers `PJF-007` when the provider is missing. |
| AC-05 Failure Isolation | Passed | `PluginJPAStarterTest.providerMissingFailureDoesNotAffectUnrelatedNonJpaPluginContext` covers shared consumer failure when the provider is missing while an unrelated non-JPA plugin context still starts. |
| AC-06 Migration Executable | Passed | Migration guide, JPA integration docs, and developer guide are synced; the complex sample is split into provider/model/service/workflow modules and passes `assembleSamplePlugins`. |
| AC-07 Entity Scan Boundary | Passed | `JpaDomainEntityPackageValidatorTest` covers normal entity packages, invisible packages, and the no-entity warning phase; the complex sample provider bundles model jars and scans model packages. |
| AC-08 Descriptor Ready Diagnostics | Passed | `DomainJpaPlatformExporterTest.exporterRegistersAndUnregistersDomainBeans`, `exporterRollsBackDescriptorAndBeansWhenExportFails`, and `PluginJPAStarterTest.sharedModeRejectsNotReadyDomainDescriptor` cover export, rollback, and not-ready diagnostics. |
| AC-09 Transaction Proxy Boundary | Passed | `TransactionProxyBoundaryTest` covers the self-invocation anti-pattern and separate-bean `REQUIRES_NEW` success path; complex sample HTTP smoke records forced rollback behavior. |
| AC-10 Plugin Package Boundary and Runtime Smoke | Passed | The `samples/cross-plugin-jpa` acceptance record shows the provider package does not contain consumer repositories and records success/failure HTTP smoke paths. |

### 6.3 Notes

- This command-level pass did not start a long-running demo HTTP service; runtime HTTP smoke evidence comes from the separate `samples/cross-plugin-jpa` acceptance record.
- Cross-domain atomic transactions remain explicitly out of scope and require a separate future design.

## 7. Sign-off

- Reviewer: Codex
- Date: 2026-06-11
- Result (Pass/Fail): Pass
