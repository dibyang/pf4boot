# Plugin Developer Guide

## Goal

This guide gives plugin developers an official path from creating plugins, declaring dependencies, packaging and verification, using JPA/Web/management capabilities, and troubleshooting failures. Starting with 3.3, it is also the official template index and the `pf4boot-plugin 1.7.0` baseline guide.

## Quick Start

Minimal plugin development flow:

1. Create a plugin module and apply `net.xdob.pf4boot-plugin`.
2. Use `compileOnlyApi` for APIs provided by the host.
3. Use `bundle` for runtime dependencies the plugin must carry.
4. Use `plugin project(":...")` when depending on another plugin.
5. Define a stable plugin descriptor with plugin id, version, provider, and dependencies.
6. Run the plugin package task or sample assembly task.
7. Load the plugin with a sample host or target host and run smoke checks.

For complex JPA samples:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

## `pf4boot-plugin 1.7.0` Baseline

The root build currently uses:

```groovy
classpath "net.xdob.pf4boot:pf4boot-plugin:1.7.0"
```

This repository only consumes that helper Gradle plugin and does not modify the external `pf4boot-plugin` repository.

| Capability | Current State | Repository Usage | Verification |
| --- | --- | --- | --- |
| Plugin project application | Confirmed | `samples/cross-plugin-jpa:plugin-*` | `rg "apply plugin: 'net.xdob.pf4boot-plugin'" samples` |
| Dependency scopes | Used | sample plugin `build.gradle` | compile and package tasks |
| Plugin dependencies | Used | workflow depends on user-book service/domain | runtime smoke |
| Plugin descriptors | Current samples use `plugin.properties` | `samples/cross-plugin-jpa:plugin-*` | inspect packaged descriptor |
| Template generation | 1.7.0 public task still to verify | later E4/E5 decision | do not document unverified capability as required |
| Package manifest/check | Framework-side checksum/trust design exists | packaging docs and repository example | later compatibility matrix/package verification |

Rules:

- If 1.7.0 supports both Gradle DSL and `plugin.properties`, new official templates should prefer the more verifiable/generated style; legacy samples may keep `plugin.properties` with compatibility notes.
- Unverified 1.7.0 capabilities must be marked as to verify, not required developer steps.
- Releasing, tagging, or modifying `pf4boot-plugin` itself must happen explicitly in that external repository.

## Dependency Scope Decision Table

| Scenario | Use | Do Not Use |
| --- | --- | --- |
| Framework APIs provided by the host | `compileOnlyApi` | `bundle` |
| Runtime library owned by the plugin | `bundle` | accidental host classpath |
| Plugin A depends on plugin B lifecycle | `plugin project(":...")` | plain project dependency only |
| Plugin A compiles against plugin B API | `compileOnlyApi project(":plugin-b")` + `plugin project(":plugin-b")` | bundling plugin B into plugin A |
| Host platform library | `platformApi` or host dependency | duplicate different versions inside plugin |

Counterexamples:

- Do not bundle host-shared APIs such as `pf4boot-api`, `pf4boot-jpa`, or `pf4boot-web-support`.
- Do not package another plugin's implementation classes as ordinary jars; use plugin dependencies and exported service APIs.
- Do not rely on accidental business jars on the host classpath without an explicit host-provided or plugin-provided boundary.

## Official Template Matrix

| Template | Source Sample | Required Dependencies | Forbidden | Verification |
| --- | --- | --- | --- | --- |
| service plugin | `plugin-unrelated-service` | `pf4boot-api`, exported APIs | No JPA starter | `.\gradlew.bat :samples:cross-plugin-jpa:plugin-unrelated-service:compileJava` |
| web plugin | Future Web sample or Web support extension | `pf4boot-web-support` | No direct host MVC internal mutation | `.\gradlew.bat :pf4boot-web-starter:test` |
| JPA domain plugin | `plugin-demo-jpa-domain` | `pf4boot-jpa-domain-starter`, model module | No entities, repositories, controllers, or services | `.\gradlew.bat :samples:cross-plugin-jpa:plugin-demo-jpa-domain:compileJava` |
| JPA consumer plugin | `plugin-user-book-service` | `pf4boot-jpa-starter`, model module, domain plugin dependency | No local EMF/TM | `.\gradlew.bat :samples:cross-plugin-jpa:plugin-user-book-service:compileJava` |
| workflow plugin | `plugin-workflow` | service APIs, domain/consumer plugin dependencies | No injection of another plugin's internal repositories | `.\gradlew.bat :samples:cross-plugin-jpa:plugin-workflow:compileJava` |
| management client | `samples/plugin-management-console` | HTTP management API | No dependency on core internals | `.\gradlew.bat :samples:plugin-management-console:test` |

## Plugin Type Guides

### Service Plugin

- Use `compileOnlyApi` for host APIs.
- Put only plugin-private runtime libraries into `bundle`.
- Export stable service APIs if other plugins need to call the service.
- See `samples/cross-plugin-jpa/plugin-unrelated-service`.

### Web Plugin

- Depend on `pf4boot-web-support` as compile API.
- The host provides the Web starter; a plugin should not carry its own Spring MVC stack.
- Dynamic mappings, interceptors, and static resources must be cleaned when the plugin stops.
- Verification should cover endpoint available after start, unavailable after stop, and restored after restart.

### JPA Domain Plugin

- Use `pf4boot-jpa-domain-starter`.
- Implement `JpaDomainDefinitionProvider` to declare domain id, entity packages, DataSource, and DDL policy.
- Export only `domain.{domain-id}.dataSource`, `entityManagerFactory`, `transactionManager`, and descriptor.
- Do not define business repositories, controllers, or services.

### JPA Consumer Plugin

- Use `pf4boot-jpa-starter`.
- Implement `JpaConsumerBindingProvider`, for example `JpaConsumerBinding.shared("demo")`.
- Group repositories by package and explicitly bind the shared EMF/TM with `@EnableJpaRepositories`.
- Prefer explicit `transactionManager = "domain.demo.transactionManager"` on business transactions.

### Workflow Plugin

- Compose capabilities through exported service APIs.
- Do not inject another plugin's internal repositories.
- Cross-plugin transactions require all participants to use the same JPA domain.
- Cross-datasource atomic transactions are unsupported; use Saga/Outbox style compensation.

### Management Client

- Call only `/pf4boot/admin/**` and read-only Actuator endpoints.
- Send tokens and `X-Idempotency-Key` for write operations.
- Do not depend on `pf4boot-core` internals.
- See `samples/plugin-management-console`.

## Plugin Descriptor

Current official samples still use `plugin.properties`. Future templates may migrate after the `pf4boot-plugin 1.7.0` DSL semantics are confirmed.

Rules:

- Plugin id must be stable across versions.
- Version must match the package and dependency declarations.
- Dependencies describe plugin runtime dependencies, not ordinary Java compile dependencies.
- Descriptor changes are plugin compatibility changes and should be discoverable by package verification or deployment precheck.

## Package Verification

Hosts can enable pre-load package governance. Checksums cover integrity, and trust manifests carry digest, signature metadata, and capability declarations:

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

- `DISABLED`: default compatibility mode.
- `WARN`: log issues without blocking load.
- `ENFORCE`: block plugin loading when verification fails.

The default checksum and trust manifest are sidecar files next to the package, for example `sample-workflow.zip.sha256` and `sample-workflow.zip.pf4boot-trust.json`.

## Offline Plugin Repository

Hosts can enable a local or mounted offline-index repository. The repository contains `repository-index.json` and plugin packages; the framework does not download from a remote central service:

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

Repository releases resolve, verify, and plan by default. When `plugin-repository-replace-enabled=true`, management requests with `dryRun=false` copy the release package into the controlled cache and reuse replace/rollback orchestration.

## JPA Development Path

Shared JPA transaction domains should use three module roles:

- model modules contain entities and value objects only.
- domain capability plugins create and export DataSource, EntityManagerFactory, TransactionManager, and descriptor only.
- consumer plugins define repositories and business services; repositories use entities from model modules and explicitly bind the shared EMF/TM.

Provider example:

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

Consumer example:

```java
@PluginStarter({WorkflowStarter.class, PluginJPAStarter.class})
public class WorkflowPlugin extends Pf4bootPlugin implements JpaConsumerBindingProvider {
  @Override
  public JpaConsumerBinding jpaConsumerBinding() {
    return JpaConsumerBinding.shared("demo")
        .additionalDomain("audit")
        .build();
  }
}
```

Repository configuration:

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.sample.userbook.repository",
    entityManagerFactoryRef = "domain.demo.entityManagerFactory",
    transactionManagerRef = "domain.demo.transactionManager"
)
public class UserBookJpaConfig {
}
```

Common failures:

| Error Code | Meaning | Action |
| --- | --- | --- |
| `PJF-005` | empty entity package | add entity packages in provider |
| `PJF-008` | entity package invisible or scan failed | check model dependency and class loading boundary |
| `PJF-009` | domain definition missing | implement `JpaDomainDefinitionProvider` |

JPA runtime refresh is a maintenance-window capability, not zero-downtime refresh. It defaults to `spring.pf4boot.jpa.reload.mode=DISABLED`; hosts must explicitly add `pf4boot-jpa-management-starter` to use JPA management endpoints.

## Operations

### Management API

- Base management comes from `pf4boot-management-starter`.
- JPA reload management comes from `pf4boot-jpa-management-starter`.
- Write APIs must use authentication, authorization, rate limits, idempotency, and audit.
- Management clients consume HTTP responses only.

### Read-Only Observability

After explicitly adding `pf4boot-actuator`, read-only Actuator endpoints such as `pf4bootplugins`, `pf4bootgovernance`, and optionally `pf4bootjpareload` are registered.

These endpoints return snapshots and governance summaries only. They do not expose start, stop, reload, or delete operations.

### Hot Replacement And Rollback

Release-grade safe replacement should use `PluginDeploymentService.replace(...)` or the management deployment replace API. Do not wrap low-level `reloadPlugin` as a hot replacement flow.

## Troubleshooting

| Symptom | Check First |
| --- | --- |
| Class not found | dependency mistakenly marked `compileOnlyApi`; runtime dependency missing from `bundle` |
| Type injection fails | host API bundled into plugin causing duplicate classes |
| Plugin dependency not started | missing `plugin project(":...")` or descriptor dependency |
| JPA repository cannot find EMF/TM | `@EnableJpaRepositories` missing explicit `domain.{id}.*` binding |
| Provider stop breaks consumer | expected if consumer depends on the same domain |
| Unrelated plugin affected by JPA failure | check accidental JPA starter or provider dependency |
| Management write returns 401/403 | token, permissions, loopback policy, CSRF/origin settings |
| Idempotency replay misses | `X-Idempotency-Key`, operation, and target must match |

## Minimal Verification Commands

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat :samples:plugin-management-console:test
```

`runtimeSmoke` writes:

```text
samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json
samples/cross-plugin-jpa/app-run/build/test-results/runtimeSmoke/TEST-runtime-smoke.xml
```
