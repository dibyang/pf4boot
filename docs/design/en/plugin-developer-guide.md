# Plugin Developer Guide

## Goal

This guide gives plugin developers a minimal end-to-end path for dependency scopes, packaging, package verification, read-only observability, JPA usage, and troubleshooting.

## Dependency Scopes

- Use `compileOnlyApi` for APIs provided by the host, such as `pf4boot-api`, Spring Boot APIs, and shared business interfaces.
- Use `bundle` for dependencies that the plugin must carry at runtime.
- Use plugin dependency declarations such as `plugin project(":samples:cross-plugin-jpa:plugin-user-book-service")` when one plugin depends on another plugin, so PF4J dependency resolution controls startup order.
- Do not bundle host framework APIs into plugin packages, because that can create class loading conflicts.

## Plugin Package Verification

Hosts can enable pre-load plugin package governance:

```yaml
spring:
  pf4boot:
    plugin-package-verification-mode: WARN
    plugin-compatibility-verification-mode: WARN
    plugin-package-checksum-extension: .sha256
    system-version: 1.0.0
```

- `DISABLED`: default compatibility mode, does not block historical plugins.
- `WARN`: logs missing checksums, checksum mismatches, or incompatible system versions without blocking load.
- `ENFORCE`: blocks plugin loading when verification fails.

The default checksum file lives next to the plugin package, for example `sample-workflow.zip.sha256`. Signature verification remains a later topic and does not block checksum/verifier usage.

## Read-Only Observability

After the host explicitly adds `pf4boot-actuator`, the read-only actuator endpoint `pf4bootplugins` is registered.

This endpoint only returns plugin snapshots. It does not expose start, stop, reload, or delete operations. Snapshots include plugin ID, version, state, path, recent error, dependencies, start duration, and a resource-count placeholder.

Adding `pf4boot-actuator` also conditionally registers Micrometer metrics:

- `pf4boot.plugins`
- `pf4boot.plugins.started`
- `pf4boot.plugins.failed`

## JPA Plugins

Plugin JPA must be enabled explicitly:

```yaml
pf4boot:
  plugin:
    jpa:
      enabled: true
```

Plugin-side `ddl-auto` defaults to `none`. Schema migration is explicitly owned by the host or plugin through migration tooling. The framework does not force Flyway or Liquibase.

Entity package discovery order:

1. `@EntityScan` / `EntityScanPackages`
2. Spring Boot auto-configuration packages
3. The plugin main class package

Runtime entity additions do not refresh an existing `EntityManagerFactory`. `DynamicMetadata.sync()` fails explicitly so callers do not assume the Hibernate metamodel was hot-refreshed.

Shared JPA transaction domains should be split into three module roles:

- model modules contain only entities and value objects, and are either bundled by the domain capability plugin or provided by the host.
- domain capability plugins only create and export the DataSource, EntityManagerFactory, TransactionManager, and descriptor; they do not define business repositories, controllers, or services.
- consumer plugins define their own repositories and business services; repository entities come from model modules and repositories explicitly bind the shared EMF/TM with `@EnableJpaRepositories`.

The domain capability plugin's `pf4boot.plugin.jpa.domain.entity-packages` must point to model packages visible to the provider. Empty configuration fails with `PJF-005`; invisible packages or scan failures fail with `PJF-008`; visible packages with no current `@Entity` emit a first-phase `PJF-008` warning.

If one consumer plugin needs multiple shared domains, configure the primary domain with `domain-id`, put other domains in `additional-domains`, and declare separate `@EnableJpaRepositories` blocks for each repository package:

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

## Upgrade Rollback

Hosts can use `upgradePlugin(pluginId, newPluginPath, rollbackPluginPath)` for upgrades with a rollback point. If upgrade fails, the framework attempts to reload the previous package from `rollbackPluginPath`; if the plugin was started before upgrade, rollback attempts to restore the started state.

## Minimal Verification Commands

```powershell
.\gradlew.bat :pf4boot-core:test
.\gradlew.bat :pf4boot-actuator:test
.\gradlew.bat :pf4boot-jpa-starter:test
```
