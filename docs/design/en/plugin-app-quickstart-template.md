# Plugin Application Quickstart Template

## Background

3.3 already covers the plugin developer guide, complex samples, management APIs, offline repository, compatibility precheck, and console sample. The remaining adoption problem is that first-time users still need to assemble host dependencies, plugin directories, management settings, and the smallest plugin structure from multiple samples. The quickstart template provides a copyable, runnable, and reducible starting point.

## Goals

1. Provide a minimal host application template that enables pf4boot, Web plugins, management APIs, and Actuator.
2. Provide a minimal plugin template that demonstrates the descriptor, `Pf4bootPlugin`, `@SpringBootPlugin`, and a Web Controller.
3. Give JPA users a clear upgrade path by reusing `samples/cross-plugin-jpa` instead of duplicating a second complex JPA sample.
4. Create a stable boundary for future scaffolding or external template repositories.

## Non-Goals

- No command-line generator.
- No new published module.
- No starter-embedded sample UI.
- No duplicated full cross-plugin JPA sample.

## Module Design

| Module | Responsibility | Copyability |
| --- | --- | --- |
| `samples:app-template-basic:host` | Minimal Spring Boot host with pf4boot/web/management/actuator | copyable as an application host |
| `samples:app-template-basic:plugin-hello` | Minimal Web plugin exposing `/api/template/hello` | copyable as an application plugin |
| `samples:cross-plugin-jpa` | JPA template source | selectively copied through README guidance |

## Host Defaults

- `pf4boot-starter`
- `pf4boot-web-starter`
- `pf4boot-management-starter`
- `pf4boot-actuator`
- `spring-boot-starter-web`
- `plugins-root` points to the plugin directory produced by the template build.
- management APIs use a local token from `PF4BOOT_ADMIN_TOKEN`, with `sample-token` as the sample default.

## Quick Path

1. Build plugin packages:

```powershell
.\gradlew.bat :samples:app-template-basic:host:assembleTemplatePlugins
```

2. Run the host:

```powershell
.\gradlew.bat :samples:app-template-basic:host:runTemplateHost
```

3. Verify plugin endpoints:

```text
GET http://127.0.0.1:7788/api/template/hello
GET http://127.0.0.1:7788/actuator/pf4bootplugins
```

## JPA Upgrade Path

For JPA needs, do not pile features into the basic template. Use `samples/cross-plugin-jpa` as the template source:

1. Copy JPA starter and management starter settings from `demo-host`.
2. Copy one model module for entities and value objects.
3. Copy one domain provider plugin for DataSource, EMF, transaction manager, and JPA domain descriptor.
4. Copy one consumer plugin for repositories and business services.
5. Keep entities and repositories grouped by package, especially when multiple data sources exist.

## Compatibility

All new modules live under `samples/*` and do not change published module APIs. The `settings.gradle` change only affects repository-local builds.

## Verification Plan

```powershell
.\gradlew.bat :samples:app-template-basic:host:compileJava
.\gradlew.bat :samples:app-template-basic:plugin-hello:pf4boot
.\gradlew.bat :samples:app-template-basic:host:assembleTemplatePlugins
```

Optional runtime verification:

```powershell
.\gradlew.bat :samples:app-template-basic:host:runTemplateHost
```

