# Cross-Plugin JPA Transaction Improvement Plan

## 1. Goal and Scope

This plan tracks the work required to move cross-plugin JPA transactions from "minimal viable" to a long-term maintainable framework capability.

Scope:

- plugin-level JPA binding configuration
- local BeanDefinition bridge for shared JPA
- domain descriptor and ready state
- entity/model visibility validation
- multi-domain repository binding rules
- transaction proxy boundary samples and tests
- runtime smoke and failure-injection acceptance

Out of scope:

- JTA/XA
- cross-datasource atomic transactions
- plugin hot replacement design and implementation
- full complex sample implementation, which has a separate plan

## 2. Milestones

| Milestone | Goal | Deliverable | Passing Condition |
| --- | --- | --- | --- |
| M1 Design freeze | lock direction and compatibility strategy | improvement design and this plan | review passes with no blocking open issue |
| M2 Shared JPA bridge | consumer plugin local BeanFactory can see platform-exported EMF/TX | registrar fix, unit tests, complex sample rerun | Spring Data repository registrar can start |
| M3 Plugin-level binding | resolve JPA binding by plugin-id | properties, resolver, unit tests | old config compatible, new config works |
| M4 Domain descriptor | provider exports domain metadata and ready state | descriptor type, export/rollback logic, tests | consumer can diagnose through descriptor |
| M5 Entity visibility validation | provider validates entity packages at startup | validation logic, error codes, tests | configuration errors are locatable |
| M6 Transaction and multi-domain acceptance | cover proxy, multi-domain, and failure isolation | tests and sample notes | key failure paths are reviewable |
| M7 Documentation closure | update guides and acceptance records | Chinese/English docs and acceptance docs | docs, implementation, and acceptance align |

## 3. Task Breakdown

### M1 Design freeze

- [x] Summarize current cross-plugin transaction capability.
- [x] Keep the provider + `SHARED` consumer architecture.
- [x] State that cross-domain atomic transactions are unsupported.
- [x] Keep plugin hot replacement out of this plan.
- [x] Review plugin-level JPA binding configuration.
- [x] Decide whether descriptor is internal or public API.

Review conclusion:

- Accept `pf4boot.plugin.jpa.plugins.<plugin-id>` as the plugin-level binding configuration. It has higher priority than the legacy global `mode/domain-id`.
- Keep the first `JpaDomainDescriptor` version as an internal JPA integration capability instead of promoting it to the public `pf4boot-api`. If third-party plugins need compile-time access later, evaluate API stability separately.

### M2 Shared JPA bridge

- [x] Fix shared JPA BeanDefinition visibility so the Spring Data JPA repository registrar no longer fails when platform-exported beans lack BeanDefinitions.
- [x] Register local BeanDefinitions for `domain.{id}.entityManagerFactory` and `domain.{id}.transactionManager` in the consumer plugin BeanFactory.
- [x] Keep instance resolution delegated to the parent/platform BeanFactory, avoiding real EMF/TX duplication.
- [x] Let the shared bridge read `mode/domain-id` from the current plugin context and keep old fallback behavior.
- [x] Add tests showing that, after parent/platform exports EMF/TX, the plugin BeanFactory supports both `containsBeanDefinition` and `getBean`.
- [x] Add tests showing that dynamically shared platform beans have both singleton instances and BeanDefinitions for Spring Data JPA lookup.
- [x] Rerun the complex sample and verify service/workflow repositories start.

Implementation notes:

- `Pf4bootPluginManagerImpl.registerBeanToContext()` now registers a `RootBeanDefinition` when dynamically sharing a bean. This fixes Spring Data JPA's parent BeanFactory scan followed by `getBeanDefinition()`.
- `SharedJpaAutoConfiguration` now keeps only `@Import(SharedJpaBeanDefinitionRegistrar.class)`, avoiding duplicate registrar registration through both import and static `@Bean` paths.

### M3 Plugin-level binding

- [x] Extend `Pf4bootJpaProperties` with `plugins.<plugin-id>` binding.
- [x] Add a binding resolver that prefers the current plugin ID.
- [x] Keep existing `mode/domain-id` as fallback.
- [x] Keep default `LOCAL` behavior unchanged.
- [x] Emit `PJF-006` for missing or conflicting binding.
- [x] Add unit tests for old config, plugin-level config, fallback, and invalid config.

Implementation notes:

- `JpaPluginBindingResolver` resolves the effective JPA binding for the current plugin: plugin-level config first, legacy config second, and `LOCAL` by default.
- `SharedJpaBeanDefinitionRegistrar` uses the same result to register local placeholder BeanDefinitions for shared EMF/TM, preventing plugin-level `SHARED` from being misread by legacy global conditions.
- Local `EntityManagerFactory`/`TransactionManager` beans now use `LocalJpaModeCondition` and are created only when the effective binding is not `SHARED`.
- `PluginJPAStarter.afterPropertiesSet()` validates the effective shared binding and emits `PJF-006` when a plugin-level shared binding lacks `domain-id`.
- Unit tests cover legacy config, plugin-level config overriding global LOCAL, fallback, and missing domain-id.

Suggested configuration:

```yaml
pf4boot:
  plugin:
    jpa:
      plugins:
        plugin-user-book-service:
          mode: SHARED
          domain-id: demo
        plugin-workflow:
          mode: SHARED
          domain-id: demo
```

### M4 Domain Descriptor

- [x] Define `JpaDomainDescriptor`.
- [x] Export descriptor only after all `domain.{id}.*` beans are exported successfully.
- [x] Roll back exported beans and descriptor when provider export fails.
- [x] Let consumers read descriptor first in `SHARED` mode.
- [x] Emit `PJF-007` when descriptor is missing or `ready=false`.
- [x] Add tests for normal export, partial export rollback, and consumer diagnostics.

Suggested fields:

```text
domainId
providerPluginId
entityPackages
dataSourceBeanName
entityManagerFactoryBeanName
transactionManagerBeanName
ready
createdAt
```

Implementation notes:
- `JpaDomainDescriptor` lives in the `net.xdob.pf4boot.jpa.domain` package of `pf4boot-jpa` as an internal JPA integration metadata type, and is not promoted to `pf4boot-api` yet.
- `pf4boot-jpa-domain-starter` exports `domain.{id}.descriptor` only after DataSource, EntityManagerFactory, and TransactionManager are exported successfully; if descriptor export or any previous export fails, previously exported beans are unregistered in reverse order.
- `pf4boot-jpa-starter` shared consumers read the descriptor first, then verify `ready=true` and that the descriptor EMF/TM bean names match the current binding; missing, not-ready, or mismatched descriptors emit `PJF-007`.
- The default descriptor name is `domain.{id}.descriptor`; providers can override it with `pf4boot.plugin.jpa.domain.descriptor-name`, and consumers can point to the same descriptor with global or plugin-level `descriptor-ref`.
- Tests cover normal provider export, descriptor export rollback, and consumer diagnostics for missing or not-ready descriptors.

### M5 Entity Visibility Validation

- [x] Validate that provider `entity-packages` is not empty.
- [x] Try resolving at least one `@Entity` from configured packages.
- [x] Start with warnings where needed, then move to fail-fast after verification.
- [x] Emit `PJF-008` when no managed entity is found.
- [x] Document that entities come from model modules.
- [x] Add tests for empty packages, invisible classes, and normal model jars.

Implementation notes:
- `pf4boot-jpa-domain-starter` validates entity package visibility before creating the `LocalContainerEntityManagerFactoryBean`.
- Empty `entity-packages` still fails fast with `PJF-005`; packages that are invisible from the provider classpath or fail during scanning fail fast with `PJF-008`.
- Packages that are visible but contain no `@Entity` emit a first-phase `PJF-008` warning and continue startup; after sample and production feedback, this can be reconsidered as fail-fast.
- The developer guide and JPA integration documentation now state that entities come from model modules, providers stay narrowly scoped, and consumer repositories explicitly bind shared EMF/TM.

### M6 Transaction Boundary and Multi-Domain Acceptance

- [x] Add tests for self-invocation anti-pattern and separate-bean success path.
- [x] Add multi-domain repository binding tests.
- [x] Add provider missing/failure isolation tests.
- [x] Add forced rollback tests.
- [x] Add plugin package content checks.
- [x] Add runtime smoke documentation.

Implementation notes:
- `TransactionProxyBoundaryTest` covers the self-invocation anti-pattern and the separate-bean `REQUIRES_NEW` success path.
- `PluginJPAStarterTest.pluginLevelBindingRegistersAdditionalSharedDomains` covers EMF/TM BeanDefinition registration and descriptor validation when one consumer binds a primary domain plus `additional-domains`.
- `PluginJPAStarterTest.providerMissingFailureDoesNotAffectUnrelatedNonJpaPluginContext` covers provider-missing failure for the shared consumer while an unrelated non-JPA plugin context still starts.
- Forced rollback, provider package boundaries, and runtime HTTP smoke are covered by the complex sample acceptance record; cross-domain atomic transactions remain explicitly unsupported.

### M7 Documentation Closure

- [x] Update `cross-plugin-jpa-transaction-capability.md`.
- [x] Update `cross-plugin-jpa-transaction-capability-acceptance.md`.
- [x] Update `jpa-integration.md`.
- [x] Update `plugin-developer-guide.md`.
- [x] Sync English translations.
- [x] Add final acceptance records.

Final acceptance record:
- M2-M6 code, tests, complex sample packaging verification, and documentation are captured in their corresponding commits.
- Final command-level verification: `.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-core:test :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`.
- The acceptance checklist is synced in `cross-plugin-jpa-transaction-capability-acceptance.md` and its English translation.
- Explicit non-goals remain: JTA/XA, cross-datasource/cross-domain atomic transactions, and hot replacement deployment planning.

## 4. Acceptance Checklist

| ID | Item | Passing Standard |
| --- | --- | --- |
| AC-01 | old config compatibility | existing `pf4boot.plugin.jpa.mode/domain-id` behaves unchanged |
| AC-02 | plugin-level binding works | different plugin IDs resolve different domain bindings |
| AC-03 | descriptor exports normally | `domain.{id}.descriptor` is readable after provider startup |
| AC-04 | descriptor rollback | partial provider export failure leaves no stale descriptor |
| AC-05 | entity visibility diagnostics | entity package errors include clear error code and package name |
| AC-06 | multi-domain repository isolation | different repository packages bind different EMF/TM values |
| AC-07 | transaction proxy works | separate-bean `REQUIRES_NEW` behaves as expected |
| AC-08 | provider failure isolation | dependency chain fails while unrelated plugins still start |
| AC-09 | non-JPA plugins unaffected | plugins without JPA need no domain configuration |
| AC-10 | docs synced | Chinese design, English translation, guide, and acceptance docs align |
| AC-11 | shared JPA bridge is visible to Spring Data | consumer plugin local BeanFactory has EMF/TX BeanDefinitions, and repository registrar no longer fails with `NoSuchBeanDefinitionException` |

## 5. Recommended Verification Commands

For M2/M3/M4:

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test
```

For M5:

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test `
  :pf4boot-jpa-domain-starter:test `
  :pf4boot-core:test --tests net.xdob.pf4boot.Pf4bootPluginManagerLifecycleTest
```

When sample modules are involved:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

For runtime verification, add sample-specific HTTP smoke.

## 6. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| plugin-level config conflicts with old config | consumer resolves unexpected binding | define priority and log diagnostics |
| descriptor type is placed in the wrong module | future API compatibility pressure | keep it internal first; promote only if third-party plugins need it |
| entity scan validation is inaccurate | provider startup blocked incorrectly | warning first, fail-fast later with tests |
| multi-domain sample is mistaken for cross-db transaction | wrong consistency expectation | docs and examples keep saying cross-domain atomic transactions are unsupported |
| tests need real plugin lifecycle | unit tests are insufficient | combine starter unit tests, core lifecycle tests, and sample smoke |

## 7. Open Question Recommendations

### Q1: Where should `JpaDomainDescriptor` live?

Recommendation: keep it in `pf4boot-jpa-domain-starter` or an internal package in `pf4boot-jpa` first. Promote it to public API only if third-party plugins need compile-time access.

### Q2: Should entity package validation fail fast in the first version?

Recommendation: fail fast for missing packages or invisible classes; warn first when a package resolves but no entity is found, then move to fail-fast after complex sample verification.

### Q3: Should we add a helper annotation over `@EnableJpaRepositories`?

Recommendation: not yet. Keep explicit Spring Data configuration first, then evaluate `@EnablePf4bootJpaRepositories` after the complex sample stabilizes.

### Q4: Where does plugin ID come from for plugin-level config?

Recommendation: use the current PF4J `pluginId`. If it is not available during starter initialization, inject it through the existing `pf4j.plugin` bean or plugin context attributes.

## 8. Status

- Plan start date: 2026-06-11
- Current status: M7 Documentation Closure is complete; this cross-plugin JPA transaction improvement pass is closed
- Owner: Codex
- Blockers: none; the no-`@Entity` package case remains a first-phase warning, and moving it to fail-fast should be decided from later production feedback
