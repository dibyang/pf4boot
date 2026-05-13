# JPA Integration

## Problem

Some plugins need JPA repositories and entities, but non-JPA plugins should not pay for or accidentally initialize an `EntityManagerFactory`. Plugin JPA support is opt-in and plugin-local.

## Modules

- `pf4boot-jpa`: provides `Pf4bootJpaPersistenceProvider`, which merges managed class names and managed packages for Hibernate.
- `pf4boot-jpa-starter`: provides `PluginJPAStarter`, a plugin-side Spring Boot auto-configuration class.
- `pf4boot-api`: declares `DynamicMetadata` and `DynamicMetadataSupport`; it currently records candidate entity classes only and does not support runtime synchronization into an already-created JPA metamodel.

## Plugin Starter Behavior

`PluginJPAStarter` is annotated with `@SpringBootPlugin` and guarded by:

- `LocalContainerEntityManagerFactoryBean`;
- `EntityManager`;
- Hibernate `SessionImplementor`;
- `pf4boot.plugin.jpa.enabled=true`.

This means JPA is not enabled for every plugin by default. A plugin must include the starter in `@PluginStarter` and set the plugin-local property to enable JPA.

Example from `plugin1`:

```java
@PluginStarter({Plugin1Starter.class, PluginJPAStarter.class})
public class Plugin1Plugin extends Pf4bootPlugin {
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
- `.\gradlew.bat :plugin1:build`

Manual checks should start `plugin1` against the demo H2 database and verify repository beans are created in the plugin context.
