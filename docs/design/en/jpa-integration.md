# JPA Integration

## Problem

Some plugins need JPA repositories and entities, but non-JPA plugins should not pay for or accidentally initialize an `EntityManagerFactory`. Plugin JPA support is opt-in and plugin-local.

The current implementation is centered on plugin-owned definitions: providers declare domains through
`JpaDomainDefinitionProvider`, and consumers declare shared bindings through `JpaConsumerBindingProvider`.
Structural configuration such as `pf4boot.plugin.jpa.domain.*`, `pf4boot.plugin.jpa.mode/domain-id`, and
`pf4boot.plugin.jpa.plugins.*` remains only as a `3.x` compatibility fallback. See
[JPA Plugin-Owned Configuration Remediation Plan](jpa-plugin-owned-configuration-plan.md).

## Modules

- `pf4boot-jpa`: provides `Pf4bootJpaPersistenceProvider`, which merges managed class names and managed packages for Hibernate.
- `pf4boot-jpa-starter`: provides `PluginJPAStarter`, a plugin-side Spring Boot auto-configuration class.
- `pf4boot-jpa-domain-starter`: provides `Pf4bootJpaDomainStarter`, the provider-side module that creates shared domain DataSource/EMF/TM and exports them to the platform context.
- `pf4boot-api`: declares `DynamicMetadata` and `DynamicMetadataSupport`; it currently records candidate entity classes only and does not support runtime synchronization into an already-created JPA metamodel.

## Plugin Starter Behavior

`PluginJPAStarter` is annotated with `@SpringBootPlugin` and guarded by classpath conditions:

- `LocalContainerEntityManagerFactoryBean`;
- `EntityManager`;
- Hibernate `SessionImplementor`.

This means JPA is not enabled for every plugin by default. A plugin explicitly enables JPA by including the starter in
`@PluginStarter`; `pf4boot.plugin.jpa.enabled=true` is no longer required.

Shared JPA consumer example:

```java
@PluginStarter({UserBookServiceStarter.class, PluginJPAStarter.class})
public class UserBookServicePlugin extends Pf4bootPlugin implements JpaConsumerBindingProvider {
  @Override
  public JpaConsumerBinding jpaConsumerBinding() {
    return JpaConsumerBinding.shared("demo").build();
  }
}
```

## Entity Scan Rules

`PluginJPAStarter.getPackagesToScan()` collects packages in this order:

1. packages from Spring Boot `@EntityScan` / `EntityScanPackages`;
2. Spring Boot auto-configuration packages;
3. the plugin main class package from the registered `pf4j.plugin` bean.

If no package can be found, startup fails with an explicit error instructing the plugin to use `@EntityScan` or ensure the plugin bean is registered.

## EntityManager And Transaction Beans

`PluginJPAStarter` creates:

- `LocalContainerEntityManagerFactoryBean` when no `EntityManagerFactory` exists in the plugin context;
- `JpaTransactionManager` when no `TransactionManager` exists.

The data source, `JpaProperties`, `HibernateProperties`, and `HibernatePropertiesCustomizer` list are injected from the plugin context and its parent bean factories.

## Shared Domain Starter

`pf4boot-jpa-domain-starter` is used by datasource capability plugins. Once a capability plugin includes
`Pf4bootJpaDomainStarter` in `@PluginStarter`, the starter creates local domain beans from a
`JpaDomainDefinition` supplied by the plugin main class or plugin context:

- `domainDataSource`
- `domainEntityManagerFactory`
- `domainTransactionManager`

Then `DomainJpaPlatformExporter` exports them into the platform context for the current plugin `group`:

- `domain.{domain-id}.dataSource`
- `domain.{domain-id}.entityManagerFactory`
- `domain.{domain-id}.transactionManager`
- `domain.{domain-id}.descriptor`

Export happens after singleton initialization of the domain plugin context; beans are unregistered in reverse when
that context closes. If platform export conflicts or the definition is invalid, the domain plugin fails startup with
`PJF-004/PJF-005/PJF-008/PJF-009` and rolls back already-exported beans.

Before creating the `EntityManagerFactory`, the domain plugin validates the entity packages in `JpaDomainDefinition`:

- empty configuration fails fast with `PJF-005`;
- packages that are invisible from the current plugin classpath, or fail during scanning, fail fast with `PJF-008`;
- packages that are visible but currently contain no `@Entity` emit a first-phase `PJF-008` warning; after the behavior is proven stable, this can be reconsidered as fail-fast.

In shared JPA scenarios, the datasource capability plugin should stay narrowly focused and should not define business entities, repositories, controllers, or business services in its own source. Entities should live in independent model modules, either bundled by the domain plugin or provided by the host on a visible classpath. Consumer repositories reference those model entities and explicitly bind the shared `EntityManagerFactory` and `TransactionManager`.

The domain starter excludes Spring Boot DataSource/JPA Repository auto-configuration to avoid creating unrelated
default EMFs or repositories. Business plugins bind these platform beans through `JpaConsumerBinding.shared("demo")`
and explicit `@EnableJpaRepositories` settings for repository packages, `entityManagerFactoryRef`, and
`transactionManagerRef`. The legacy `pf4boot.plugin.jpa.mode/domain-id` and
`pf4boot.plugin.jpa.plugins.{pluginId}.*` settings remain as compatibility fallbacks, but new plugins and complex
samples should use the plugin-owned API.

When one consumer plugin needs multiple shared domains, declare the primary domain with
`JpaConsumerBinding.shared("order")` and add others with `additionalDomain("audit")`. The starter registers local
EMF/TM BeanDefinitions and validates descriptor readiness for both the primary and additional domains. Repositories must
still be split by package and explicitly bound to their own EMF/TM. Cross-domain atomic transactions remain unsupported.

Note: Spring Data JPA recursively scans parent BeanFactories for `EntityManagerFactory` beans and then looks up matching BeanDefinitions. Therefore, when JPA domain beans are exported to root/platform/application shared contexts, dynamic shared beans cannot be singleton-only. `Pf4bootPluginManagerImpl` also registers a BeanDefinition pointing to the same instance so Spring Data JPA post-processors can recognize it.

## JPA Domain Restart-Based Refresh

`pf4boot-jpa` exposes `net.xdob.pf4boot.jpa.reload.*` models and SPI for shared JPA domain runtime refresh. `pf4boot-jpa-starter` provides the default in-memory implementation. V1 is restart-based refresh: it does not rebuild an existing `EntityManagerFactory` in place. Instead, it identifies the provider and exact shared consumers, stops consumers, stops the provider, verifies that old DataSource/EMF/TM/descriptor exports are gone, starts the provider, waits for the new descriptor to become ready, and then starts consumers again.

The capability is disabled by default and is configured as host governance:

```yaml
spring:
  pf4boot:
    jpa:
      reload:
        mode: DISABLED
```

Available modes:

- `DISABLED`: planning and execution both return a `RELOAD_DISABLED` blocker.
- `PLAN_ONLY`: produces an impact plan but never mutates plugin lifecycle.
- `STOP_CONSUMERS_AND_REBUILD`: enables explicit restart-based execution.

Consumers are detected from `JpaPluginBindingRegistry`, which is populated by `PluginJPAStarter` after a shared binding is validated. `JpaDomainReloadAutoConfiguration` exports the same `JpaPluginBindingRegistry` instance to the pf4boot root context, so the host reload service and plugin starters use one shared registry. Plugins inferred only from the PF4J dependency graph make the plan non-executable until their shared domain binding is explicit. After provider restart or `providerReplacementPath` replacement, the provider must re-export `domain.{domain-id}.descriptor`, EMF, TM, and DataSource. Cross-domain atomic transactions and zero-downtime production refresh remain unsupported.

Before stopping consumers/providers, execute mode calls `JpaDomainReloadDrainCoordinator` and reuses the common `PluginTrafficDrainer` contract:

- accepted drain continues into stop/start;
- drain timeout or rejection records `drainReport`, returns `DRAIN_TIMEOUT` or `DRAIN_REJECTED`, and does not stop any plugin;
- no drainer defaults to compatibility mode with a warning; `require-drainer=true` blocks execution;
- reload record queries return the full `drainReport`, while Actuator exposes only summary fields for the latest drain.

## DDL Defaults

The starter intentionally defaults plugin `ddl-auto` to `none` through `HibernateSettings.ddlAuto`. Automatic schema updates are considered risky in production and must be explicitly configured by the application or plugin.

## Dynamic Metadata Boundary

`DynamicMetadata` only records candidate entity classes. It does not synchronize added or removed classes into an already-created `EntityManagerFactory`. `sync()` must fail clearly so callers do not assume that Hibernate metamodel, repository proxies, or transaction context were refreshed at runtime.

The effective discovery boundary for plugin JPA entities is the startup scan used when `PluginJPAStarter` creates the `EntityManagerFactory`. Plugins that need additional entities should expose them before plugin startup through `@EntityScan`, auto-configuration packages, or the plugin main class package scan.

## Compatibility

JPA support depends on Spring Boot 2.7.x and Hibernate 5.6.x behavior. Changes to package scan order, default DDL behavior, transaction bean conditions, or the runtime dynamic sync boundary can affect plugin startup and schema management.

## Verification

For JPA changes, run:

- `.\gradlew.bat :pf4boot-jpa:compileJava`
- `.\gradlew.bat :pf4boot-jpa-starter:compileJava`
- `.\gradlew.bat :pf4boot-jpa-domain-starter:compileJava :pf4boot-jpa-domain-starter:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`
- `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke`

Manual checks should start `samples:cross-plugin-jpa:demo-host` and verify repository beans are created in the service/workflow plugin contexts.
