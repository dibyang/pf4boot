# JPA Plugin-Owned Configuration Remediation Plan

## Problem

The samples no longer require the host to list plugin JPA entities, but the framework still exposes plugin-owned JPA structure as host-bindable configuration:

- provider side: `pf4boot.plugin.jpa.domain.*`, including `id`, `entity-packages`, `datasource`, `ddl-auto`, and exported bean names.
- consumer side: `pf4boot.plugin.jpa.enabled`, `pf4boot.plugin.jpa.mode/domain-id`, `pf4boot.plugin.jpa.plugins.{pluginId}.*`, `additional-domains`, and related binding fields.

This violates the plugin autonomy boundary. A JPA domain's entity packages, datasource, DDL strategy, and shared-domain bindings are part of the plugin's own runtime contract. They should not be generic host application configuration. If the host can maintain those fields for a plugin, plugin upgrades, dependency boundaries, entity visibility, and transaction bindings become hidden host-side knowledge.

The host may still configure platform governance capabilities such as management endpoints, JPA reload policy, drain timeouts, record stores, and observability exposure. It must not describe which entities a plugin owns, which datasource a provider uses, or which JPA domain a consumer binds.

## Affected Modules

- `pf4boot-jpa-domain-starter`: remove environment binding for provider domain structure and read plugin-provided domain definitions instead.
- `pf4boot-jpa-starter`: remove environment binding for consumer JPA bindings and read plugin-provided consumer binding definitions instead.
- `pf4boot-jpa`: host reusable domain/binding models if needed, keeping Java 8 compatibility and minimal dependencies.
- `samples/cross-plugin-jpa`: migrate current `setProperty(...)` examples to the new plugin-owned API.
- `docs/design`: update JPA integration, developer guide, and migration documents so they no longer recommend environment configuration for plugin-owned structure.

## Target Boundary

### Plugin-Owned Items That Should No Longer Be Framework Configuration

The following keys should no longer be public framework configuration or recommended in host `application.yml`:

```text
pf4boot.plugin.jpa.domain.id
pf4boot.plugin.jpa.domain.entity-packages
pf4boot.plugin.jpa.domain.datasource.*
pf4boot.plugin.jpa.domain.ddl-auto
pf4boot.plugin.jpa.domain.data-source-name
pf4boot.plugin.jpa.domain.entity-manager-factory-name
pf4boot.plugin.jpa.domain.transaction-manager-name
pf4boot.plugin.jpa.domain.descriptor-name
pf4boot.plugin.jpa.enabled
pf4boot.plugin.jpa.mode
pf4boot.plugin.jpa.domain-id
pf4boot.plugin.jpa.entity-manager-factory-ref
pf4boot.plugin.jpa.transaction-manager-ref
pf4boot.plugin.jpa.descriptor-ref
pf4boot.plugin.jpa.additional-domains
pf4boot.plugin.jpa.plugins.*
```

These values describe plugin-owned contracts and should be provided by plugin code, plugin metadata, or plugin-local beans.

### Host-Owned Governance Items That May Remain

These keys describe platform governance and may remain host configuration:

```text
pf4boot.plugin.jpa.domain-reload.*
management.endpoints.web.exposure.include
spring.pf4boot.management.*
```

They decide how the platform manages plugin lifecycle and records operations. They do not decide plugin JPA structure.

## Design

### Provider Plugin Defines Its Domain

Introduce a provider-side definition model, for example:

```java
public interface JpaDomainDefinitionProvider {
  JpaDomainDefinition jpaDomainDefinition();
}
```

Or expose a bean from the plugin context:

```java
@Bean
public JpaDomainDefinition demoDomainDefinition() {
  return JpaDomainDefinition.builder("demo")
      .entityPackage("net.xdob.sample.model.userbook")
      .entityPackage("net.xdob.sample.model.audit")
      .dataSource(dataSourceDefinition())
      .ddlAuto("update")
      .build();
}
```

`Pf4bootJpaDomainStarter` should stop binding `Pf4bootJpaDomainProperties`. At startup it reads one explicit `JpaDomainDefinition` from the current plugin context and uses it to create the DataSource, EMF, TM, and descriptor. If the definition is missing, fail fast with a new error code, for example:

```text
PJF-009 Domain provider plugin did not provide JpaDomainDefinition
```

### Consumer Plugin Defines Its Shared Binding

Introduce a consumer-side binding definition, for example:

```java
public interface JpaConsumerBindingProvider {
  JpaConsumerBinding jpaConsumerBinding();
}
```

Or expose a bean:

```java
@Bean
public JpaConsumerBinding userBookJpaBinding() {
  return JpaConsumerBinding.shared("demo").build();
}
```

`PluginJPAStarter` should no longer depend on `pf4boot.plugin.jpa.enabled=true`. If a plugin includes `PluginJPAStarter` in `@PluginStarter`, it explicitly asks for JPA support. The starter reads `JpaConsumerBinding` from the plugin context:

- missing binding: use `LOCAL`, preserving simple local JPA behavior.
- `SHARED` binding: register local EMF/TM BeanDefinitions for the requested domain and additional domains, then validate descriptor readiness.

### Compatibility Window

Existing configuration cannot be removed silently. Use two phases:

1. `3.1.x` compatibility phase:
   - Add the plugin-owned API.
   - Keep old configuration binding, but emit deprecation warnings.
   - If old configuration comes from the host environment, the warning should say that plugin JPA structure must not be maintained by the host.
   - Migrate all samples to the new API.

2. `4.0` removal phase:
   - Remove provider structure binding from `Pf4bootJpaDomainProperties`.
   - Remove consumer structure fields from `Pf4bootJpaProperties`, or split reload properties into an independent class.
   - Remove old configuration tests and documentation.

### Configuration Naming

To avoid ambiguity, move reload governance configuration from `pf4boot.plugin.jpa.domain-reload.*` to a host-governance prefix:

```text
spring.pf4boot.jpa.reload.*
```

During the compatibility phase, keep reading the old prefix and emit a migration warning. Naming should communicate ownership:

- `spring.pf4boot.*`: host/platform governance configuration.
- plugin JPA structure: declared by plugin code or plugin-local metadata, not host configuration.

## Change Plan

1. Design API: add immutable models such as `JpaDomainDefinition`, `JpaDataSourceDefinition`, and `JpaConsumerBinding`, preferably in `pf4boot-jpa`.
2. Refactor provider starter: make `Pf4bootJpaDomainStarter` read a `JpaDomainDefinition` bean or provider SPI; keep old configuration as fallback with warnings.
3. Refactor consumer starter: let `PluginJPAStarter` activate through explicit `@PluginStarter` inclusion and `JpaConsumerBinding`; use old `pf4boot.plugin.jpa.*` only as fallback.
4. Split reload configuration: add `spring.pf4boot.jpa.reload.*`; keep `pf4boot.plugin.jpa.domain-reload.*` as fallback.
5. Migrate samples: make `samples/cross-plugin-jpa` provider expose `JpaDomainDefinition`, consumer plugins expose `JpaConsumerBinding`, and remove structural `setProperty("pf4boot.plugin.jpa...")` calls.
6. Migrate documentation: update `jpa-integration.md`, `plugin-developer-guide.md`, `cross-plugin-jpa-transaction-*.md`, and English translations.

## Implementation Status

- Added `JpaDomainDefinition`, `JpaDataSourceDefinition`, `JpaDomainDefinitionProvider`,
  `JpaConsumerBinding`, `JpaConsumerDomainBinding`, `JpaBindingMode`, and `JpaConsumerBindingProvider`.
- `Pf4bootJpaDomainStarter` now prefers plugin-provided `JpaDomainDefinition`; old
  `pf4boot.plugin.jpa.domain.*` remains as a compatibility fallback and emits a deprecation warning.
- `PluginJPAStarter` no longer requires `pf4boot.plugin.jpa.enabled=true`; explicit `@PluginStarter` inclusion is the opt-in.
  Shared binding prefers `JpaConsumerBinding`; old `mode/domain-id` and `plugins.*` remain as compatibility fallbacks.
- `spring.pf4boot.jpa.reload.*` is now supported as the host-governance prefix for JPA reload, with
  `pf4boot.plugin.jpa.domain-reload.*` retained as a compatibility fallback.
- `samples/cross-plugin-jpa` has moved to plugin-main-class SPI and no longer uses structural
  `setProperty("pf4boot.plugin.jpa...")` calls for domain or consumer binding.
- Current design and migration entry points have been updated in Chinese and English; historical archive documents keep
  their original record semantics.

## Compatibility

- Source compatibility: new APIs do not break existing plugin source; old configuration remains usable during the compatibility phase.
- Runtime compatibility: old plugins continue to start, with migration warnings.
- Behavior compatibility: `LOCAL/SHARED`, domain descriptors, reload plans, and drain behavior remain unchanged.
- Configuration compatibility: old configuration is deprecated first and removed in `4.0`.
- Plugin packaging compatibility: plugins carry their own JPA definitions through code or plugin-local resources; the host no longer carries entity package lists.

## Verification

Minimum verification:

```powershell
.\gradlew.bat :pf4boot-jpa:compileJava
.\gradlew.bat :pf4boot-jpa-starter:test
.\gradlew.bat :pf4boot-jpa-domain-starter:test
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

New tests:

- provider fails fast when `JpaDomainDefinition` is missing.
- provider creates and exports a domain from the new definition.
- old `pf4boot.plugin.jpa.domain.*` configuration still works during compatibility and emits a warning.
- consumer binds a shared domain from the new binding.
- old `pf4boot.plugin.jpa.mode/domain-id` and `plugins.*` settings still work during compatibility and emit warnings.
- sample starts without relying on host-provided structural JPA configuration.

## Open Questions

- Whether plugin-owned definitions should be exposed through SPI methods or Bean models. Bean models fit Spring starters better; SPI is easier for non-Spring plugins.
- Whether datasource definitions may directly provide a `DataSource` bean or only datasource construction parameters.
- Whether `spring.pf4boot.jpa.reload.*` should be introduced in 3.x, or whether 3.x should only warn on old ownership boundaries.
- Whether management endpoints should expose a warning when a plugin still uses deprecated JPA configuration.
