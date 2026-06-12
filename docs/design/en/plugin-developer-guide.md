# Plugin Developer Guide

## Goal

This guide gives plugin developers a minimal end-to-end path for dependency scopes, packaging, package verification, read-only observability, JPA usage, and troubleshooting.

## Dependency Scopes

- Use `compileOnlyApi` for APIs provided by the host, such as `pf4boot-api`, Spring Boot APIs, and shared business interfaces.
- Use `bundle` for dependencies that the plugin must carry at runtime.
- Use plugin dependency declarations such as `plugin project(":samples:cross-plugin-jpa:plugin-user-book-service")` when one plugin depends on another plugin, so PF4J dependency resolution controls startup order.
- Do not bundle host framework APIs into plugin packages, because that can create class loading conflicts.

## Plugin Package Verification

Hosts can enable pre-load plugin package governance. Checksums cover package integrity, while the trust manifest carries package digest, signature metadata, and capability declarations:

```yaml
spring:
  pf4boot:
    plugin-package-verification-mode: WARN
    plugin-package-trust-mode: WARN
    plugin-compatibility-verification-mode: WARN
    plugin-package-checksum-extension: .sha256
    plugin-package-trust-manifest-extension: .pf4boot-trust.json
    plugin-capability-precheck-mode: WARN
    system-version: 1.0.0
```

- `DISABLED`: default compatibility mode, does not block historical plugins.
- `WARN`: logs missing checksums, checksum mismatches, or incompatible system versions without blocking load.
- `ENFORCE`: blocks plugin loading when verification fails.

The default checksum file lives next to the plugin package, for example `sample-workflow.zip.sha256`. The first-stage trust manifest is also a sidecar file, for example `sample-workflow.zip.pf4boot-trust.json`:

```json
{
  "formatVersion": 1,
  "pluginId": "sample-workflow",
  "pluginVersion": "3.0.0-SNAPSHOT",
  "packageSha256": "lowercase-hex-sha256",
  "signature": {
    "algorithm": "SHA256withRSA",
    "keyId": "local-dev-key",
    "value": "base64-signature"
  },
  "capabilities": {
    "provides": [
      {
        "name": "jpa.consumer",
        "version": "1",
        "scope": "PLUGIN",
        "attributes": {
          "datasource": "orderDs"
        }
      }
    ],
    "requires": [
      {
        "name": "jpa.datasource",
        "versionRange": "[1,2)",
        "required": true,
        "attributes": {
          "datasource": "orderDs",
          "entityPackages": "net.xdob.sample.model.userbook",
          "repositoryPackages": "net.xdob.sample.userbook.repository"
        }
      }
    ]
  }
}
```

Recommended production migration:

1. `DISABLED`: deploy historical plugins without manifests first and confirm behavior stays unchanged.
2. `WARN`: generate `.sha256` and `.pf4boot-trust.json` for new packages and observe missing manifest, digest mismatch, signature metadata, and missing capability warnings.
3. `ENFORCE`: enable only after every deployed plugin has manifests, checksums, and required capability declarations. Run deployment prechecks in staging first.

Note: `signature.value`, tokens, private key paths, and full stacks must not be written into HTTP responses, audit records, or the operation store. The framework performs baseline sanitization, but the plugin release flow should also avoid placing private keys or raw credentials in manifests.

## Read-Only Observability

After the host explicitly adds `pf4boot-actuator`, the read-only actuator endpoints `pf4bootplugins` and `pf4bootgovernance` are registered.

These endpoints only return plugin snapshots and governance summaries. They do not expose start, stop, reload, or delete operations. `pf4bootplugins` snapshots include plugin ID, version, state, path, recent error, dependencies, start duration, and a resource-count placeholder. `pf4bootgovernance` summarizes trust/capability configuration, deployment summaries, cleanup diagnostics, and warnings.

Expose the endpoints using standard Spring Boot Actuator configuration, for example:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,pf4bootplugins,pf4bootgovernance
```

Adding `pf4boot-actuator` also conditionally registers Micrometer metrics:

- `pf4boot.plugins`
- `pf4boot.plugins.started`
- `pf4boot.plugins.failed`
- `pf4boot.management.request.total`
- `pf4boot.management.rejected.total`
- `pf4boot.management.idempotency.hit.total`

If `/actuator/pf4bootgovernance` returns 404, first confirm the host includes `pf4boot-actuator`, the endpoint is exposed, the runtime classpath contains `spring-boot-actuator-autoconfigure`, and `Pf4bootActuatorAutoConfiguration` runs after `Pf4bootAutoConfiguration`.

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
