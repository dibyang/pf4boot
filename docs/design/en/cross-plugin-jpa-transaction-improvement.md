# Cross-Plugin JPA Transaction Improvement Design

> Status note: the `pf4boot.plugin.jpa.mode/domain-id`, `plugins.*`, and `entity-packages`
> priority described here belongs to the early compatibility design. The current plan is
> [jpa-plugin-owned-configuration-plan.md](jpa-plugin-owned-configuration-plan.md):
> new plugins declare structural JPA contracts through
> `JpaDomainDefinitionProvider` / `JpaConsumerBindingProvider`; old configuration is only a `3.x`
> compatibility fallback.

## 1. Background

The first phase of cross-plugin JPA transactions has already landed:

- `pf4boot-jpa-starter` supports `LOCAL / SHARED` modes.
- `pf4boot-jpa-domain-starter` lets a domain capability plugin create and export `DataSource / EntityManagerFactory / TransactionManager`.
- Business plugins depending on the same `domain-id` can explicitly bind the same EntityManagerFactory and TransactionManager.
- Plugins that do not depend on that domain should not be affected by domain startup failures.

The direction is sound, but the current implementation is still minimal. To make it a long-term framework capability, we should improve configuration granularity, entity visibility, lifecycle diagnostics, transaction boundary rules, and acceptance coverage.

## 2. Goals

- Preserve current `LOCAL / SHARED` compatibility.
- Allow multiple JPA plugins in one application to bind different `domain-id` values by plugin or repository package.
- Make shared EMF entity ownership and classloader visibility verifiable.
- Provide clearer domain readiness state, diagnostics, and error codes.
- State clearly that cross-plugin transactions only support same-domain transactions, not cross-domain atomic transactions.
- Add runtime smoke, failure injection, and dependency-isolation acceptance.

## 3. Non-Goals

- No JTA/XA.
- No cross-datasource atomic transaction support.
- No runtime contribution of new entities into an already-started provider EMF.
- No change to the default `mode=LOCAL` compatibility behavior.
- No plugin hot replacement design in this document.

## 4. Current Capability and Gaps

| Area | Current State | Main Gap |
| --- | --- | --- |
| Mode selection | `pf4boot.plugin.jpa.mode=LOCAL/SHARED` | Configuration is too global for multiple JPA plugins |
| Domain provider | provider creates and exports `domain.{id}.*` | No domain descriptor or ready state |
| Entity scan | provider configures `entity-packages` | Entity ownership and model jar visibility are not verified |
| Repository binding | consumer uses explicit `@EnableJpaRepositories` | Multi-domain diagnostics are weak |
| Transaction boundary | business code explicitly specifies `transactionManager` | Proxy/self-invocation rules are under-documented and under-tested |
| Failure isolation | dependency chain should fail while unrelated plugins continue | Runtime smoke and observable state are missing |
| Sample | minimal demo works | complex sample still needs clearer module boundaries |

## 5. Improvement Design

### 5.1 Plugin-Level JPA Binding

Keep existing configuration:

```yaml
pf4boot:
  plugin:
    jpa:
      mode: SHARED
      domain-id: demo
```

Add recommended plugin-level binding:

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

Resolution priority:

1. `pf4boot.plugin.jpa.plugins.<plugin-id>` for the current plugin.
2. Existing `pf4boot.plugin.jpa.mode/domain-id`.
3. Default `LOCAL`.

This keeps old configuration valid while supporting multiple JPA plugins.

### 5.2 Domain Descriptor and Ready State

The provider currently exports:

- `domain.{domain-id}.dataSource`
- `domain.{domain-id}.entityManagerFactory`
- `domain.{domain-id}.transactionManager`

It should also export a read-only descriptor:

- `domain.{domain-id}.descriptor`

Suggested fields:

| Field | Meaning |
| --- | --- |
| `domainId` | domain ID |
| `providerPluginId` | plugin that provides this domain |
| `entityPackages` | entity packages used to build the EMF |
| `dataSourceBeanName` | exported DataSource bean name |
| `entityManagerFactoryBeanName` | exported EMF bean name |
| `transactionManagerBeanName` | exported TM bean name |
| `ready` | whether the domain was initialized and exported |
| `createdAt` | creation time |

Consumers in `SHARED` mode should check the descriptor first. Diagnostics should include `domain-id`, consumer plugin ID, provider plugin ID, and missing bean names.

### 5.3 Entity and Model Jar Visibility

Shared EMF entities must be visible to the provider before EMF creation. Recommended structure:

```text
model-user-book        -> only entities/value objects/enums
model-workflow-audit   -> only entities/value objects/enums
plugin-demo-jpa-domain -> depends on models and scans model packages
service/workflow       -> defines repositories and services
```

Hard rules:

- provider does not define business entities.
- provider does not define business repositories.
- consumer repositories reference entities from model modules visible to the provider.
- provider startup validates that `entity-packages` can be resolved from the current plugin classpath; invisible packages or scan failures fail with a clear error code.
- In the first phase, packages that are visible but contain no managed entity emit a warning with the same error code; after sample and production feedback, this can be reconsidered as fail-fast.

### 5.4 Repository and Multi-Domain Binding

A consumer plugin may depend on multiple domains, but repositories must be grouped by package:

```yaml
pf4boot:
  plugin:
    jpa:
      plugins:
        sample-workflow:
          mode: SHARED
          domain-id: order
          additional-domains:
            - domain-id: audit
```

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.sample.order.repository",
    entityManagerFactoryRef = "domain.order.entityManagerFactory",
    transactionManagerRef = "domain.order.transactionManager"
)
public class OrderJpaConfig {
}
```

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.sample.audit.repository",
    entityManagerFactoryRef = "domain.audit.entityManagerFactory",
    transactionManagerRef = "domain.audit.transactionManager"
)
public class AuditJpaConfig {
}
```

Constraints:

- Repository packages for different domains must not be mixed.
- The primary `domain-id` and `additional-domains` both register EMF/TM BeanDefinitions in the consumer BeanFactory and validate descriptor readiness at startup.
- One `@Transactional` method binds to one TM by default.
- Cross-domain orchestration must use compensation, outbox, or saga patterns; no atomic commit is promised.

### 5.5 Transaction Proxy Boundary Rules

The developer guide and samples must state:

- `@Transactional` methods must be called through a Spring proxy.
- `REQUIRES_NEW` must not rely on self-invocation.
- Exported cross-plugin services should be Spring-managed proxy beans.
- Controllers/workflow plugins should not inject repositories owned by other plugins.
- Shared TM transaction methods should explicitly declare `transactionManager`.

### 5.6 Diagnostics, Error Codes, and Logs

Keep existing `PJF-001` to `PJF-005` and add:

| Error Code | Scenario | Suggested Message |
| --- | --- | --- |
| `PJF-006` | plugin-level JPA binding cannot be resolved | output plugin-id, mode, domain-id |
| `PJF-007` | domain descriptor is missing or not ready | output consumer plugin, domain-id, candidate provider |
| `PJF-008` | entity package is invisible, scan fails, or has no managed entity | output provider plugin, domain-id, entity-packages |
| `PJF-009` | domain bean export conflict | output bean name, existing provider, new provider |
| `PJF-010` | multi-domain repository binding conflict | output repository package and EMF/TM refs |

Startup logs should include:

- provider plugin ID
- domain ID
- entity packages
- exported bean names
- consumer plugin ID
- resolved EMF/TM

### 5.7 Acceptance Closure

Acceptance should cover:

- compilation and plugin packaging
- provider package content inspection
- consumer responsibilities
- runtime HTTP smoke
- normal commit, forced rollback, and `REQUIRES_NEW` proxy boundary
- provider missing, provider startup failure, provider recovery
- unrelated plugins continue when provider fails

## 6. Compatibility

- Existing `pf4boot.plugin.jpa.mode/domain-id` remains valid as a `3.x` compatibility fallback and emits migration guidance.
- Default `LOCAL` behavior remains unchanged.
- Descriptor and plugin-level bindings are enhancements and should not affect non-JPA plugins.
- New error codes improve diagnostics without changing success paths.
- Provider entity package validation may expose previously hidden misconfiguration earlier; this is expected fail-fast behavior.

## 7. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| plugin-level config increases complexity | higher learning cost | keep old config and recommend new config only for multi-plugin/domain cases |
| entity package validation false positives | provider blocked incorrectly | start with warning plus tests, then move to fail-fast |
| descriptor state inconsistent with beans | consumer reads wrong readiness | register descriptor only after all beans export successfully |
| multi-domain is mistaken for cross-database transaction | wrong consistency expectation | docs and samples continue stating no cross-domain atomic transaction support |
| transaction proxy rules are misused | rollback behavior differs from expectation | cover self-invocation anti-pattern and correct pattern in tests |

## 8. Recommendation

- Keep the current provider plugin + `SHARED` consumer architecture.
- Prioritize plugin-level binding, domain descriptor, entity visibility validation, transaction proxy rules, and acceptance coverage.
- Do not introduce JTA/XA.
- Do not mix plugin hot replacement into the cross-plugin transaction design.
