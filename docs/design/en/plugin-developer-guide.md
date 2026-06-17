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
    plugin-compatibility-precheck-mode: WARN
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
  "pluginVersion": "3.0.0",
  "packageSha256": "lowercase-hex-sha256",
  "pf4bootVersionRange": "[3.0.0,4.0.0)",
  "springBootVersionRange": "[2.7.0,2.8.0)",
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

The first-stage version range matcher supports exact versions and common Maven-style ranges:

| Expression | Meaning |
| --- | --- |
| `1.2.3` | Exact match |
| `[1.0,2.0)` | `>= 1.0` and `< 2.0` |
| `(1.0,2.0]` | `> 1.0` and `<= 2.0` |
| `[1.0,)` | `>= 1.0` |
| `(,2.0]` | `<= 2.0` |

`plugin-compatibility-precheck-mode` controls `pf4bootVersionRange` and `springBootVersionRange`. `plugin-capability-precheck-mode` controls `requiredCapabilities[].versionRange`. Start with `WARN`, then move to `ENFORCE`.

## Offline Plugin Repository

Hosts can enable a local or mounted offline-index repository. The repository directory contains `repository-index.json` and plugin packages; the framework does not download packages from a remote central service:

```yaml
spring:
  pf4boot:
    plugin-repository-enabled: true
    plugin-repository-type: offline-index
    plugin-repository-location: /opt/pf4boot/repository
    plugin-repository-trust-mode: WARN
    plugin-repository-cache-directory: /opt/pf4boot/repository-cache
    plugin-repository-replace-enabled: false
```

`repository-index.json` uses relative paths, and resolved package paths must stay inside the repository root:

```json
{
  "schemaVersion": 1,
  "repositoryId": "local-prod",
  "generatedAt": 1781280000000,
  "releases": [
    {
      "pluginId": "sample-workflow",
      "version": "3.0.0",
      "packagePath": "plugins/plugin-workflow-3.0.0.zip",
      "packageSha256": "lowercase-sha256",
      "trustManifestPath": "plugins/plugin-workflow-3.0.0.zip.pf4boot-trust.json",
      "rolloutPolicy": "manual",
      "rollbackCandidate": true
    }
  ]
}
```

The management API can dry-run a plan using release fields:

```json
{
  "pluginId": "sample-workflow",
  "repositoryVersion": "3.0.0",
  "dryRun": true
}
```

By default, repository releases provide resolution, verification, and planning only. When
`plugin-repository-replace-enabled=true`, management requests with `dryRun=false` copy the release
package into the controlled cache and then reuse the existing replace/rollback orchestration. HTTP
responses and Actuator expose summaries only, not cache absolute paths.

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

Plugin JPA is enabled by explicitly including the starter; it no longer depends on
`pf4boot.plugin.jpa.enabled=true`. Shared-domain consumers should implement
`JpaConsumerBindingProvider` on the plugin main class:

```java
@PluginStarter({WorkflowStarter.class, PluginJPAStarter.class})
public class WorkflowPlugin extends Pf4bootPlugin implements JpaConsumerBindingProvider {
  @Override
  public JpaConsumerBinding jpaConsumerBinding() {
    return JpaConsumerBinding.shared("demo").build();
  }
}
```

The legacy `pf4boot.plugin.jpa.plugins.{pluginId}.mode/domain-id` and
`pf4boot.plugin.jpa.mode/domain-id` keys are only `3.x` compatibility fallbacks and emit deprecation warnings.
New plugins should not write these structural JPA bindings from `initiate()`.

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

Domain capability plugins declare the domain through `JpaDomainDefinitionProvider`. Entity packages, DataSource, and
DDL policy are provider-plugin contracts and should not be maintained by the host:

```java
@PluginStarter(Pf4bootJpaDomainStarter.class)
public class DemoJpaDomainPlugin extends Pf4bootPlugin implements JpaDomainDefinitionProvider {
  @Override
  public JpaDomainDefinition jpaDomainDefinition() {
    return JpaDomainDefinition.builder("demo")
        .entityPackage("net.xdob.demo.model")
        .dataSource(JpaDataSourceDefinition.builder("jdbc:h2:file:./work/demo")
            .username("sa")
            .driverClassName("org.h2.Driver")
            .build())
        .ddlAuto("update")
        .build();
  }
}
```

Missing definitions fail with `PJF-009`; empty entity packages fail with `PJF-005`; invisible packages or scan failures fail with `PJF-008`.

If one consumer plugin needs multiple shared domains, declare the primary domain with `shared("order")`, add others
with `additionalDomain("audit")`, and declare separate `@EnableJpaRepositories` blocks for each repository package.

Shared JPA domain runtime refresh is optional and disabled by default. The HTTP management endpoints and `pf4bootjpareload` Actuator endpoint are not part of the base management modules; a host must add `pf4boot-jpa-management-starter` explicitly before using them. A host or sample that wants the V1 restart-based flow must enable host governance explicitly:

```yaml
spring:
  pf4boot:
    jpa:
      reload:
        mode: STOP_CONSUMERS_AND_REBUILD
```

For production rehearsal, start with `PLAN_ONLY` first. The plan API reports provider, exact consumers, inferred consumers, unrelated plugins, stop/start order, blockers, and warnings without mutating lifecycle. Execute mode without `providerReplacementPath` stops exact consumers, stops the provider, verifies that old DataSource/EMF/TM/descriptor exports are gone, starts the provider, waits for the new descriptor, and then starts consumers. Execute mode with `providerReplacementPath` validates the staged provider package through `PluginDeploymentService`, delegates replacement and rollback to the deployment service, and records a provider replacement summary in the reload record.

Execute mode reuses the common `PluginTrafficDrainer` before stopping plugins. If the host has web, scheduled-task, or other drainers, JPA reload first rejects new entrypoints and waits for in-flight work. Drain timeout or rejection records `drainReport`, returns `DRAIN_TIMEOUT` or `DRAIN_REJECTED`, and does not stop consumers or providers. With no drainer, the default is compatibility mode with a warning; set `spring.pf4boot.jpa.reload.require-drainer=true` when a drainer must be present. The old `pf4boot.plugin.jpa.domain-reload.*` prefix remains readable during the compatibility window as a deprecated fallback.

## Upgrade Rollback

Hosts can use `upgradePlugin(pluginId, newPluginPath, rollbackPluginPath)` for upgrades with a rollback point. If upgrade fails, the framework attempts to reload the previous package from `rollbackPluginPath`; if the plugin was started before upgrade, rollback attempts to restore the started state.

## Minimal Verification Commands

```powershell
.\gradlew.bat :pf4boot-core:test
.\gradlew.bat :pf4boot-actuator:test
.\gradlew.bat :pf4boot-jpa-management-starter:test
.\gradlew.bat :pf4boot-jpa-starter:test
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

`runtimeSmoke` writes a machine-readable report:

```text
samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json
samples/cross-plugin-jpa/app-run/build/test-results/runtimeSmoke/TEST-runtime-smoke.xml
```

After P10, `runtimeSmoke` uses the sample Java runner by default for Windows and Linux CI. The PowerShell script remains as a Windows troubleshooting entry. Reports include `unrelatedPluginAlive` and `jpaProviderIsolation` to verify that a non-JPA plugin remains available after the JPA provider is stopped.

The JPA refresh checks include `jpaReloadDisabledNoMutation`, `jpaReloadPlanOnly`, `jpaReloadSuccess`, `jpaReloadIdempotency`, `jpaReloadRecord`, `jpaReloadDrainSuccess`, `jpaReloadDrainTimeoutNoMutation`, and `actuatorJpaReloadDrainSummary`. They prove that disabled mode does not mutate lifecycle, plan output is usable, execute mode completes, repeated idempotency keys replay the same record, the record query endpoint can read the result, drain timeout does not stop plugins, and Actuator exposes a drain summary.
