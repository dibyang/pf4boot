# Project Constraints

These constraints apply before design and implementation work.

## Platform

- Keep source compatible with Java 8 as configured by `jdkVersion=1.8`.
- Keep source encoding UTF-8.
- Preserve the Gradle multi-module structure declared in `settings.gradle`.
- Keep shared dependency versions centralized in `gradle.properties` unless a module-specific version is unavoidable and documented.

## Module Boundaries

- Put public extension points, annotations, shared model types, and lifecycle event contracts in `pf4boot-api`.
- Put PF4J runtime implementation, plugin manager behavior, repositories, loaders, class loading, lifecycle listeners, scheduling, and metadata implementation in `pf4boot-core`.
- Put Spring Boot auto-configuration in `pf4boot-starter`, `pf4boot-web-starter`, or `pf4boot-jpa-starter` according to the integration area.
- Keep servlet MVC and plugin resource handling in `pf4boot-web-*` modules.
- Keep JPA and Hibernate integration in `pf4boot-jpa*` modules.
- Keep demo-only behavior in `demo-app`, `demo-lib`, `plugin1`, or `plugin2`.
- Keep Linux package assembly in `app-run`.

## Plugin Packaging

- Use `compileOnlyApi` for APIs supplied by the host application.
- Use `bundle` for dependencies that must be included in a plugin zip.
- Use `plugin project(":...")` for plugin-to-plugin dependencies.
- Do not accidentally bundle host framework APIs into sample plugins.

## Runtime Behavior

- Preserve plugin start/stop lifecycle ordering unless the design document explicitly changes it.
- Clean up dynamically registered beans, request mappings, scheduled tasks, metadata, and shared services when a plugin stops.
- Treat class loader behavior as a compatibility-sensitive area.
- Treat public annotations and event types as API contracts.

## Verification

- Prefer targeted Gradle commands while iterating.
- Run a broad build for cross-module framework changes when dependency resolution allows it.
- Remember that the root build disables tasks whose names contain `test`; mention this limitation when reporting verification.
