---
name: pf4boot-java
description: Work on the pf4boot Java repository, a Gradle multi-module Java 8 PF4J and Spring Boot plugin framework. Use when modifying, reviewing, debugging, or explaining this project, especially module boundaries, Gradle tasks, plugin packaging, class loading, lifecycle events, Spring auto-configuration, web/JPA support, or demo plugin behavior.
---

# PF4Boot Java

Use this skill for work inside the `pf4boot` repository.

## First Pass

1. Read `AGENTS.md`, `docs/constraints/README.md`, `settings.gradle`, `gradle.properties`, and the relevant module `build.gradle` before editing.
2. Check `docs/design` for related Chinese design decisions or prior plans; use `docs/design/en` only as the English translation.
3. Locate code with `rg` or `rg --files`; prefer narrow searches by class, annotation, or package name.
4. Identify whether the change is API, runtime, starter auto-configuration, integration support, demo, plugin packaging, or Linux packaging.

## Design Before Coding

- Prepare a short design before changing Java or Gradle files for non-trivial work.
- Add or update a file under `docs/design/` when the task affects public APIs, plugin lifecycle, class loading, Spring auto-configuration, dependency scopes, packaging, or cross-module contracts.
- Write the canonical design document in Chinese and keep the matching English translation under `docs/design/en/` in sync.
- Keep the design compact: problem, affected modules, proposed approach, compatibility impact, verification plan, and open questions.
- For small mechanical fixes, capture the design in the conversation and still follow `docs/constraints/README.md`.
- Do not implement until the intended module boundary and verification command are clear.

## Module Map

- `pf4boot-api`: public annotations, API types, plugin abstractions, lifecycle events, dynamic bean/share bean contracts, and patched Spring helper classes.
- `pf4boot-core`: PF4J runtime integration, plugin manager implementation, plugin repositories/loaders, class loading, event listeners, scheduling, and dynamic metadata implementation.
- `pf4boot-starter`: Spring Boot auto-configuration and default starter resources.
- `pf4boot-web-support`: shared web support APIs used by plugins.
- `pf4boot-web-starter`: servlet MVC integration and plugin request/resource mapping.
- `pf4boot-jpa`: JPA and Hibernate dynamic metadata support.
- `pf4boot-jpa-starter`: JPA starter auto-configuration.
- `demo-app` and `demo-lib`: host demo application and shared demo domain code.
- `plugin1` and `plugin2`: sample plugins; use them to understand plugin authoring and dependency behavior.
- `app-run`: runtime assembly and Linux package configuration.

## Gradle Workflow

- Use `.\gradlew.bat` on Windows and `./gradlew` on Unix-like systems.
- Prefer targeted verification:
  - `.\gradlew.bat :pf4boot-api:compileJava`
  - `.\gradlew.bat :pf4boot-core:compileJava`
  - `.\gradlew.bat :plugin1:build`
  - `.\gradlew.bat :app-run:buildOSPacks` only when packaging behavior is relevant.
- Run `.\gradlew.bat build` for broad framework or dependency changes.
- Remember that the root build disables tasks whose names contain `test`; if testing matters, inspect and adjust the Gradle configuration instead of assuming tests ran.
- Treat dependency resolution failures as environment issues until confirmed otherwise; this project uses `mavenLocal()`, Maven Central, and the Aliyun mirror.

## Implementation Rules

- Follow the constraints in `docs/constraints/README.md`.
- Keep Java 8 compatibility; do not use APIs or language features newer than Java 8.
- Preserve existing dependency scopes:
  - Framework/starter modules use Gradle `api` and `implementation`.
  - Plugin modules use `compileOnlyApi` for host-provided APIs and `bundle` for dependencies packaged into plugin zips.
  - Plugin-to-plugin dependencies use `plugin project(":...")`.
- Put new public extension points in `pf4boot-api`, not in implementation modules.
- Put runtime PF4J behavior in `pf4boot-core`.
- Put Spring Boot auto-configuration in starter modules and register it consistently with the existing `META-INF/spring.factories` pattern.
- Keep web integration in `pf4boot-web-*` modules and JPA integration in `pf4boot-jpa*` modules.
- Avoid broad refactors around class loaders, bean factories, lifecycle events, and request mappings unless the task explicitly requires them.

## Review Checklist

- Does the change preserve plugin start/stop lifecycle behavior?
- Are dynamically registered beans, mappings, scheduled tasks, or metadata cleaned up on plugin stop?
- Are host-provided APIs kept out of plugin bundles?
- Does the affected module still compile independently?
- Did any public API change require updates in demos or sample plugins?

## Reporting

When finishing work, mention the design document touched or explain why no design document was needed. Also mention the exact Gradle command run. If verification was skipped or blocked, state the reason and the smallest command that should be run next.
