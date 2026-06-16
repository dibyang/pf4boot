# Cross-Plugin JPA Transaction Capability Design (Execution-Ready)

## 1. Goals and Non-Goals

### 1.1 Goals

- Provide an optional cross-plugin transaction capability without changing existing non-JPA plugin behavior.
- Plugins without JPA requirements should not load this capability and should remain unaffected.
- Plugins that need shared transactions enter the same domain through a dependency on a domain-capability plugin.
- Support repository grouping by domain within business plugins and entity grouping by domain capability plugin.
- Support multiple domains (data sources) as independent transaction domains; cross-domain atomicity is explicitly out of scope for phase 1.

### 1.2 Non-Goals

- Cross-database transaction (XA/JTA).
- Runtime refresh of metamodels after `EntityManagerFactory` has started.
- Runtime contribution of new entities from business plugins into an already-started shared EMF.
- Rebuild of `pf4boot-jpa-starter` architecture; only incremental extension.

## 2. Problem

Each plugin currently runs with an isolated Spring context and usually creates its own JPA EMF and TransactionManager. As a result:

- Shared transaction boundaries are hard to define.
- Multiple plugins operating the same datasource can still end up with independent transactions.
- Cross-plugin configuration becomes coupled to internal bean wiring.

At the same time, `pf4boot-jpa` and `pf4boot-jpa-starter` already provide stable JPA basics and should be reused directly.

## 3. Design Overview

Introduce a **domain capability plugin** instead of hard-linking this into core:

1. Keep plugin-level isolated Spring contexts unchanged.
2. Extend `pf4boot-jpa-starter` with `mode`:
   - `LOCAL`: existing behavior, plugin creates its own JPA context.
   - `SHARED`: only bind to shared EMF/TM from a declared domain.
3. Domain capability plugin (named in this design as `pf4boot-jpa-domain-starter`) provides domain-level `DataSource/EntityManagerFactory/TransactionManager` and exports them at `PLATFORM` scope.

## 4. Module Responsibilities

- `pf4boot-jpa`
  - Keep current JPA abstraction and integration foundations.
- `pf4boot-jpa-starter`
  - Add `mode` handling.
  - In `SHARED`, do not create local EMF/TM.
  - In `SHARED`, register local placeholder BeanDefinitions that resolve shared EMF/TM from the parent context, so Spring Data Repository infrastructure can resolve them by name.
  - Support `additional-domains`, so one consumer plugin can register local EMF/TM BeanDefinitions for multiple shared domains and bind different repository packages explicitly.
  - Read `domain.{id}.descriptor` during startup to verify that the domain is ready and that EMF/TM names match the binding.
  - Exclude `DataSourceAutoConfiguration` and `JpaRepositoriesAutoConfiguration`; datasource comes from existing local beans or domain capability plugins, and repositories are configured explicitly by business plugins.
  - Add shared-domain validation and error signaling.
- `pf4boot-jpa-domain-starter` (new)
  - Optional provider module for domain-level shared beans.
  - Create local plugin beans:
    - `DataSource`
    - `EntityManagerFactory`
    - `PlatformTransactionManager` / `JpaTransactionManager`
  - Export them through `DomainJpaPlatformExporter` into the `PLATFORM` context for the current plugin `group`:
    - `domain.{domain-id}.dataSource`
    - `domain.{domain-id}.entityManagerFactory`
    - `domain.{domain-id}.transactionManager`
    - `domain.{domain-id}.descriptor`
  - Export happens after singleton initialization of the domain plugin context; beans are unregistered in reverse when that context closes.
  - If export fails or conflicts, already-exported beans must be rolled back to avoid a half-initialized domain in the platform context.
  - Exclude Spring Boot DataSource/JPA Repository auto-configuration; the domain starter explicitly creates its own DataSource/EMF/TM.
  - Default `ddl-auto` is `none`; empty `entity-packages` fails fast with `PJF-005`; invisible packages or scan failures fail fast with `PJF-008`.
- `pf4boot-core`
  - No new JPA runtime logic; keep dependency-based startup behavior.
- `pf4boot-api`
  - Prefer not to add public API; add only required constants/docs when unavoidable.
- `samples/cross-plugin-jpa`
  - Provide runnable complex examples, runtime packaging, and local-to-shared migration guidance.

### 4.1 Core Constraint: Entity Ownership and Scan Boundary

Same-domain transaction sharing depends on a single `JpaTransactionManager`, and a `JpaTransactionManager` is bound to one concrete `EntityManagerFactory`. Therefore phase 1 fixes these boundaries:

- Domain capability plugin creates shared EMF/TM and completes entity scanning before EMF creation.
- Business plugins can define their own repository scan paths, but their repositories must use entities already managed by the domain EMF.
- Business plugins should use explicit `@EnableJpaRepositories` for repository packages and EMF/TM references instead of relying on Spring Boot repository auto-scan.
- Shared-domain entity classes must live in the domain capability plugin, a domain shared library, or another location visible to the domain capability plugin class loader.
- Business plugins cannot dynamically add new entities into an already-started shared EMF. Adding entities requires updating the domain capability plugin or shared library and restarting that domain.
- Multi-domain access inside one plugin must split repository packages and transaction manager references by domain. Cross-domain atomicity remains unsupported.

## 5. Configuration Contract

### 5.1 Provider Plugin

`plugin.properties`:

```properties
plugin.id=order-jpa-domain
plugin.class=net.xdob.demo.domain.order.OrderJpaDomainPlugin
plugin.dependencies=
```

`application.yml`:

```yaml
pf4boot:
  plugin:
    jpa:
      domain:
        id: order
        entity-packages:
          - net.xdob.demo.domain.order.entity
        datasource:
          url: jdbc:h2:mem:orderdb
          username: sa
          password:
        ddl-auto: none
```

### 5.2 Consumer Plugin

`plugin.properties`:

```properties
plugin.id=order-service
plugin.class=net.xdob.demo.order.OrderServicePlugin
plugin.dependencies=order-jpa-domain
```

`application.yml`:

```yaml
pf4boot:
  plugin:
    jpa:
      enabled: true
      mode: SHARED
      domain-id: order
      entity-manager-factory-ref: domain.order.entityManagerFactory # optional
      transaction-manager-ref: domain.order.transactionManager        # optional
```

Default bean names (with override supported):

- `domain.{domain-id}.dataSource`
- `domain.{domain-id}.entityManagerFactory`
- `domain.{domain-id}.transactionManager`

## 6. Multi-Domain Entity and Repository Binding

To keep scan boundaries explicit, the domain capability plugin owns entity scanning. Business plugins own repository scanning and bind each repository package to the target domain EMF/TM.

Provider-side entity package example:

```yaml
pf4boot:
  plugin:
    jpa:
      domain:
        id: order
        entity-packages:
          - net.xdob.demo.domain.order.entity
```

Consumer-side repository package example:

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.demo.order.domain.order.repository",
    entityManagerFactoryRef = "domain.order.entityManagerFactory",
    transactionManagerRef = "domain.order.transactionManager"
)
public class OrderDomainJpaConfig {}
```

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.demo.order.domain.report.repository",
    entityManagerFactoryRef = "domain.report.entityManagerFactory",
    transactionManagerRef = "domain.report.transactionManager"
)
public class ReportDomainJpaConfig {}
```

Rules:

- One domain capability plugin owns entity scanning for one `domain-id`.
- Business plugins use one `@EnableJpaRepositories` per domain.
- `basePackages` should map to repository packages for one business domain only.
- Repository entities must already be managed by the shared EMF from the domain capability plugin.
- In production, set `@Transactional` transaction manager explicitly for each domain.

## 7. Runtime Behavior

### 7.1 Startup

1. Host starts and resolves plugin dependency graph.
2. Capability plugins are started before dependents automatically by PF4J.
   - Validate `pf4boot.plugin.jpa.domain.id`;
   - Validate and resolve `entity-packages`;
   - Create local domain `DataSource/EMF/TM`;
   - Export `domain.{id}.*` beans and descriptor into the platform context for the current plugin `group`;
   - If creation or export fails, fail fast with `PJF-004/PJF-005/PJF-008` and roll back already-exported beans.
3. During plugin startup:
   - `LOCAL`: existing local JPA initialization continues.
    - `SHARED`: resolve domain beans by `domain-id` and `additional-domains`.
      - If descriptor is missing or not ready, fail fast with `PJF-007`.
      - If found, bind shared EMF/TM and skip local EMF/TM creation.
4. Plugins in the same domain can share a transaction manager.

### 7.2 Failure Isolation and Rollback

- Capability plugin failure should fail only dependent chains, not unrelated plugins.
- Business plugin can be rolled back quickly by switching to `mode=LOCAL` or removing `plugin.dependencies`.
- Failed plugin can be restarted after fixing domain capability plugin.

## 8. Scenarios

### 8.1 Scenario A: Single Shared Domain

```java
@Transactional(transactionManager = "domain.order.transactionManager")
public void createOrder(...) {
  // Shared tx works across order-domain dependent plugins
}
```

### 8.2 Scenario B: Same Plugin, Two Domains (No cross-domain atomicity)

```yaml
pf4boot:
  plugin:
    jpa:
      plugins:
        report-workflow:
          mode: SHARED
          domain-id: order
          additional-domains:
            - domain-id: report
```

```java
@Transactional(transactionManager = "domain.order.transactionManager")
public void updateOrder() {}

@Transactional(transactionManager = "domain.report.transactionManager")
public void updateReport() {}
```

### 8.3 Migration Path (LOCAL -> SHARED)

1. Keep current `mode=LOCAL` and verify stability.
2. Add domain capability plugin with `domain-id`.
3. Add `plugin.dependencies` and switch plugin to `mode=SHARED`.
4. Move transaction annotations to domain TM references.
5. Remove duplicated local datasource/EMF config.

See [cross-plugin-jpa-transaction-migration.md](cross-plugin-jpa-transaction-migration.md) for the full migration guide.

## 9. Error Codes and Observability

| Code | Scenario | Expected Handling |
| --- | --- | --- |
| `PJF-001` | `SHARED` mode without `domain-id` | Fail fast; ask to set `domain-id` |
| `PJF-002` | Shared domain EMF missing | Fail fast; show required plugin and naming convention |
| `PJF-003` | Shared domain TransactionManager missing | Fail fast; instruct fixing TM reference |
| `PJF-004` | Domain capability startup failed, such as missing provider `domain.id`, invalid datasource config, or platform export conflict | Fail dependent chain; fix capability plugin config and restart the chain |
| `PJF-005` | Domain entity package configuration is empty | Fail provider startup; add `entity-packages` |
| `PJF-007` | domain descriptor is missing, not ready, or mismatched with binding | fail consumer startup; show provider, domain-id, descriptor, and EMF/TM names |
| `PJF-008` | Domain entity package is invisible, scan fails, or no managed entity is found | Fail provider startup or emit a first-phase warning; fix model module dependencies, `entity-packages`, or class loader visibility |

Logging requirements:

- log `domain-id`, capability plugin ID, TM/EMF names during startup.
- failure logs should include remediation steps.

## 10. Acceptance Criteria

See acceptance checklist:

- [archive/cross-plugin-jpa-transaction-capability-acceptance.md](archive/cross-plugin-jpa-transaction-capability-acceptance.md)

## 11. Planning (separate doc)

- [archive/cross-plugin-jpa-transaction-capability-plan.md](archive/cross-plugin-jpa-transaction-capability-plan.md)

## 12. Risks and Mitigations

- Bean name/domain-id mistakes -> enforce templates, startup errors, and clear messages.
- Scan misconfiguration in multi-domain plugin -> provide documented pair-binding rule.
- Operational overhead -> provide migration checklist and templates.

## 13. Decision Log

- Domain capability plugin creates and exports `DataSource/EMF/TM` (for this phase).
- No annotation wrapper like `@EnablePf4bootJpaSharedDomain`; configuration-driven integration first.
- No cross-domain transactions in phase 1.
- Shared-domain entities are provided by the domain capability plugin or domain shared library; business plugins bind repositories only in this phase.
- Keep current `DynamicMetadata.sync()` behavior (no runtime metamodel refresh).
