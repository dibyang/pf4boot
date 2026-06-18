# Plugin Developer Experience 3.3 Design

## Background

After 3.2, the framework has a production-ready runtime foundation, but plugin developers still need to jump across multiple documents to understand the Gradle plugin, dependency scopes, descriptors, JPA layering, sample structure, and management APIs. The first 3.3 batch focuses on developer experience: the official developer guide, the `pf4boot-plugin 1.7.0` baseline, and official templates/samples.

## Goals

1. Rewrite the official plugin developer guide around tasks and plugin types.
2. Align with `pf4boot-plugin 1.7.0` and define how this repository consumes the helper Gradle plugin.
3. Organize official templates and complex samples so samples are both demonstrations and template sources.
4. Leave stable seams for the later compatibility matrix, package verification, plugin repository, and management console UI work.

## Non-Goals

- Do not modify the external `pf4boot-plugin` repository.
- Do not implement new Gradle plugin capabilities in this phase.
- Do not change core runtime, JPA reload, or deployment transaction semantics.
- Do not publish sample modules.
- Do not put the management console UI into framework starters.

## Current State

| Area | Current State |
| --- | --- |
| Helper Gradle plugin | Root `build.gradle` uses `net.xdob.pf4boot:pf4boot-plugin:1.7.0` |
| Plugin samples | `samples/cross-plugin-jpa` covers JPA domain, consumer, workflow, unrelated service, runtime smoke |
| Compensation sample | `samples/saga-outbox` demonstrates Saga/Outbox instead of cross-datasource atomic transactions |
| Management sample | `samples/plugin-management-console` is a management API consumer sample |
| Developer guide | `plugin-developer-guide.md` covers scopes, package verification, observability, JPA, and rollback, but is capability-oriented |
| Packaging docs | `plugin-loading-and-packaging.md` covers repositories, loaders, descriptors, and Gradle scopes |

## Constraints

- Plugin examples in docs must work with the current repository or the `pf4boot-plugin 1.7.0` baseline.
- Every template must state its use case, required dependencies, forbidden dependencies, and verification command.
- JPA domain providers stay single-purpose and do not define entities, repositories, controllers, or business services.
- Consumer plugins must group entities and repositories by package and bind them explicitly.
- Cross-plugin business collaboration uses exported service APIs, not another plugin's internal repositories.
- Build outputs and historical jars under samples must not be treated as template source.

## Design

### Document Layers

| Document | Responsibility |
| --- | --- |
| `plugin-ecosystem-3.3-roadmap.md` | Six-goal 3.3 roadmap |
| `plugin-developer-experience-3.3-design.md` | E1-E3 design constraints and approach |
| `plugin-developer-experience-3.3-plan.md` | E1-E3 implementation plan and acceptance tracking |
| `plugin-developer-guide.md` | Final developer-facing guide rewritten in later implementation |
| `plugin-loading-and-packaging.md` | Mechanism-level packaging, loading, descriptor, and runtime layout details |
| Sample READMEs | Running, verification, and template notes for each sample |

### Target Developer Guide Structure

1. Quick start: from empty plugin to loadable package.
2. Plugin project structure: source layout, descriptor/DSL, resources, tests.
3. Dependency scope decision table: `compileOnlyApi`, `bundle`, `plugin`, `platformApi`.
4. Plugin type guides: service, web, JPA domain, JPA consumer, workflow, management client.
5. JPA topic: model/provider/consumer layering, multi-domain package scanning, transaction boundary, reload limits.
6. Packaging and verification: zip, lib, descriptor, checksum, trust manifest.
7. Operations: management API, hot replacement, rollback, JPA reload, Actuator.
8. Troubleshooting: startup failures, missing dependencies, class conflicts, JPA error codes, management rejections.
9. Migration: from old samples, old JPA configuration, and old packaging style.

### `pf4boot-plugin 1.7.0` Baseline

This repository only declares and consumes `pf4boot-plugin 1.7.0`; it does not modify the external plugin repository.

| Type | Description |
| --- | --- |
| Confirmed facts | Root `build.gradle` uses `pf4boot-plugin:1.7.0`; official plugin samples apply `net.xdob.pf4boot-plugin` |
| Capabilities to verify | Template generation, manifest generation, package verification, task names, descriptor DSL semantics |
| Repository actions | Align docs, samples, and verification commands with 1.7.0; record gaps under E4/E5 |

Later implementation must produce a baseline table:

| Capability | 1.7.0 Support | Repository Usage | Doc Entry | Verification |
| --- | --- | --- | --- | --- |
| Plugin project application | To verify | `samples/*/plugin-*` | Developer guide | `rg "apply plugin: 'net.xdob.pf4boot-plugin'" samples` |
| Dependency scopes | To verify | sample `build.gradle` | Developer guide, packaging docs | sample compile/package |
| Descriptor generation | To verify | sample `plugin.properties` or DSL | Developer guide | inspect packaged descriptor |
| Plugin package tasks | To verify | sample Gradle tasks | sample README | `assembleSamplePlugins` or plugin package task |
| Verification/manifest | To verify | repository/trust examples | E4/E5 | future verification task |

### Official Template Matrix

| Template | Required Modules | Forbidden | Minimal Verification |
| --- | --- | --- | --- |
| service plugin | `pf4boot-api`, exported APIs if needed | No JPA starter | `compileJava`, plugin package task |
| web plugin | `pf4boot-web-support`, Web starter runtime | No direct host MVC internal mutation | web-starter test, sample host smoke |
| JPA domain plugin | `pf4boot-jpa-domain-starter`, model module | No repositories/services/controllers | domain plugin compile, runtime smoke |
| JPA consumer plugin | `pf4boot-jpa-starter`, model module, domain plugin dependency | No local EMF/TM | jpa-starter test, runtime smoke |
| workflow plugin | service APIs, required JPA consumer/domain dependencies | No injection of internal repositories from other plugins | workflow smoke |
| management client | HTTP client or sample UI | No dependency on core internals | management-starter contract test |

## Interfaces

This phase adds no Java public API. It freezes developer-facing contracts in docs and samples:

| Interface Type | Contract |
| --- | --- |
| Gradle plugin | Official plugin projects apply `net.xdob.pf4boot-plugin` |
| Dependency scopes | Host-provided APIs use `compileOnlyApi`; plugin-owned runtime dependencies use `bundle`; plugin dependencies use `plugin` |
| Plugin descriptor | Docs must state the recommendation and compatibility rules for DSL versus `plugin.properties` |
| Sample commands | README commands must be copy-paste executable |
| HTTP management | Management clients use only `/pf4boot/admin/**` and read-only Actuator endpoints |

## Testing

| Type | Command / Check |
| --- | --- |
| Document links | Check `docs/design/README.md` and English README links |
| Encoding | `git diff --check`, no U+FFFD |
| Sample compile | `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins` |
| Runtime smoke | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke` |
| Management console | `.\gradlew.bat :samples:plugin-management-console:test` |

## Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Docs reference a 1.7.0 feature that is not actually available | Users fail when following docs | Baseline table must distinguish confirmed facts from capabilities to verify |
| Samples are too complex as templates | Users copy demo-only code | READMEs mark template source and forbidden copy targets |
| Developer guide and packaging docs conflict | Maintenance drift | Developer guide explains paths; packaging docs explain mechanisms |
| English translation drifts | Later agents implement incorrectly | Sync English docs with every Chinese design change |

## Phased Plan

See [plugin-developer-experience-3.3-plan.md](plugin-developer-experience-3.3-plan.md).

