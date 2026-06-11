# Starter Boundary Split

## Problem

`pf4boot-starter` currently exposes `pf4boot-web-starter` and `pf4boot-jpa` through `api` dependencies, and the core auto-configuration directly creates the Web support component. A host application that only needs plugin lifecycle and shared beans therefore still sees Web MVC and JPA/Hibernate types. This conflicts with the architectural goal that Web and JPA are optional integration layers.

## Affected Modules

- `pf4boot-starter`: become the core starter responsible only for plugin manager creation, lifecycle listeners, and shared bean infrastructure.
- `pf4boot-web-starter`: independently register Web MVC patches, resource resolution, and `WebPf4BootPluginSupport`.
- `pf4boot-core`: remove unused JPA/Hibernate runtime dependencies so the core starter does not carry Hibernate indirectly through core.
- `pf4boot-jpa` / `pf4boot-jpa-starter`: remain independent JPA capabilities that plugins opt into explicitly.
- `samples/cross-plugin-jpa:demo-host`: declare the Web starter explicitly to keep the sample's dynamic controller/resource behavior.

## Proposed Design

Remove the `api` dependencies from `pf4boot-starter` to `pf4boot-web-starter` and `pf4boot-jpa`. Remove unused external Hibernate/JPA dependencies from `pf4boot-core`; core keeps only the dynamic metadata implementation of interfaces declared in `pf4boot-api`. Core auto-configuration no longer references `WebPf4BootPluginSupport` or Web resource resolvers. Instead, it receives available `Pf4bootPluginSupport` beans through `ObjectProvider<Pf4bootPluginSupport>`. If none exist, it uses a no-op implementation. If multiple exist, it invokes them in ascending `getPriority()` order.

`pf4boot-web-starter` gets its own `META-INF/spring.factories` entry for `Pf4bootMvcPatchAutoConfiguration`. That auto-configuration creates `WebPf4BootPluginSupport`, `PluginPathResourceResolver`, the `PluginRequestMappingHandlerMapping` patch, the plugin resource chain customizer, and the REST admin controller. These Web MVC integrations enter the host context only when the application depends on the Web starter.

JPA remains plugin-side opt-in. The core starter does not depend on `pf4boot-jpa` or `pf4boot-jpa-starter`. A plugin that needs JPA keeps enabling it through `@PluginStarter({..., PluginJPAStarter.class})` and plugin-local `pf4boot.plugin.jpa.enabled=true`, bundling the needed starter according to plugin packaging rules.

## Compatibility

Binary APIs do not change, and `Pf4bootPluginSupport` remains the extension interface. Runtime compatibility notes:

- Applications that depend only on `pf4boot-starter` no longer automatically get plugin Web MVC controllers, interceptors, static resource integration, or REST admin endpoints.
- Hosts that need Web integration must add `pf4boot-web-starter` explicitly. Migration: keep `pf4boot-starter` and add `pf4boot-web-starter`.
- JPA plugins continue to bundle `pf4boot-jpa-starter` explicitly; the core host is no longer forced to carry Hibernate support through starter transitive dependencies.
- Applications with custom `Pf4bootPluginSupport` beans can now coexist with Web support; the framework composes supports by priority.

## Verification

Minimum verification:

- `.\gradlew.bat :pf4boot-starter:compileJava`
- `.\gradlew.bat :pf4boot-web-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava`

Broad verification:

- `.\gradlew.bat build`

## Open Questions

This phase only tightens starter dependency boundaries. It does not change admin endpoint HTTP semantics or implement runtime JPA metadata synchronization. Which JPA support classes should be host-provided versus bundled by plugins still needs a later packaging contract design.
