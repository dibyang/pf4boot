# pf4boot 3.3 Plugin Ecosystem Roadmap

## Background

`pf4boot` 3.2 closed the production-readiness loop for JPA runtime reload, hot replacement deployment, HTTP management governance, cleanup diagnostics, and runtime smoke verification. The next most valuable step is not another runtime feature. It is reducing the cost of developing, packaging, verifying, upgrading, and operating third-party plugins.

The root build already uses `net.xdob.pf4boot:pf4boot-plugin:1.7.0`. The user confirmed that the previous `pf4boot-plugin-next-requirements-zh.md` requirements have been implemented, so 3.3 treats `pf4boot-plugin 1.7.0` as the baseline for the helper Gradle plugin.

## Version Goals

3.3 includes the following six goals:

| ID | Goal | 3.3 Scope |
| --- | --- | --- |
| E1 | Rewrite the official plugin developer guide | First batch; provide type-oriented development paths and migration guidance |
| E2 | Align with the `pf4boot-plugin 1.7.0` baseline | First batch; define how docs, samples, and build scripts consume 1.7.0 |
| E3 | Organize official templates and complex samples | First batch; define the template matrix, sample layering, and verification commands |
| E4 | Compatibility matrix and package verification | Late-stage design expanded in `plugin-ecosystem-3.3-late-stage-design.md` |
| E5 | Plugin repository and distribution design | Late-stage design expanded in `plugin-ecosystem-3.3-late-stage-design.md` |
| E6 | Management console sample UI | Late-stage design expanded in `plugin-ecosystem-3.3-late-stage-design.md` |

## Non-Goals

- Do not modify the external `pf4boot-plugin` repository.
- Do not embed the management console UI into core, starter, or management starter modules.
- Do not implement cross-datasource atomic transactions, cluster rollout, or a remote plugin marketplace in the first 3.3 batch.
- Do not publish sample modules to Maven repositories.
- Do not change the 3.2 runtime semantics for JPA reload, hot replacement deployment, or management APIs.

## Constraints

- Chinese documents are canonical; English translations live under `docs/design/en/`.
- Keep Java 8, UTF-8, and the existing Gradle multi-module structure.
- The first three 3.3 goals prioritize docs, templates, and sample structure before core runtime changes.
- Packaging, plugin dependency scopes, or sample structure changes must update `plugin-loading-and-packaging.md` and `plugin-developer-guide.md`.
- Every new template and sample must include a minimal executable verification command.

## E1 Developer Guide Rewrite

The goal is to rewrite `plugin-developer-guide.md` from a capability list into a developer task path:

| Section | Content |
| --- | --- |
| Quick start | Minimal service plugin from creation to host loading |
| Plugin types | Service, Web, JPA domain, JPA consumer, workflow, management client |
| Gradle scopes | Rules for `compileOnlyApi`, `bundle`, `plugin`, and `platformApi` |
| Plugin descriptor | Rules for plugin id, version, provider, dependencies, and requires |
| Class loading boundary | Visibility rules for host APIs, plugin dependencies, and plugin-to-plugin APIs |
| JPA path | Model/provider/consumer layering, package scanning, transaction manager binding |
| Operations | Package verification, trust manifest, deployment, rollback, JPA reload |
| Troubleshooting | Startup failures, missing dependencies, class conflicts, JPA binding failures, packaging pollution |

## E2 `pf4boot-plugin 1.7.0` Baseline Alignment

The goal is to make build scripts, official docs, and samples express plugin projects using the 1.7.0 baseline instead of mixed legacy or manual conventions.

| Capability Area | Alignment Requirement |
| --- | --- |
| Plugin project declaration | All official plugin samples use `net.xdob.pf4boot-plugin` |
| Dependency scopes | Docs explain `compileOnlyApi`, `bundle`, and `plugin` with examples and counterexamples |
| Plugin descriptor | Define whether Gradle DSL or `plugin.properties` is preferred and how coexistence works |
| Package output | Freeze expected zip/jar, `lib/`, descriptor, nested dependencies, checksum and trust sidecar files |
| Template generation | Prefer 1.7.0 template/check tasks if available; record gaps under E4/E5 |
| Sample verification | Samples must not rely on unpublished private tasks or external repositories |

## E3 Official Templates And Complex Samples

The goal is to make samples both capability demonstrations and template sources.

| Template | Source Sample | Main Demonstration |
| --- | --- | --- |
| `service-plugin` | `samples/cross-plugin-jpa:plugin-unrelated-service` | Plain service plugin without JPA |
| `web-plugin` | Future Web sample or extracted Web support sample | Controller, static resources, dynamic mapping cleanup |
| `jpa-domain-plugin` | `plugin-demo-jpa-domain` | Single domain provider exporting DataSource/EMF/TM/descriptor only |
| `jpa-consumer-plugin` | `plugin-user-book-service` | Repository package scanning and shared transaction manager |
| `workflow-plugin` | `plugin-workflow` | Cross-plugin service composition, rollback, audit |
| `management-client` | `samples/plugin-management-console` | Consuming `/pf4boot/admin/**` and Actuator only |

## E4 Compatibility Matrix And Package Verification

Included in 3.3. Detailed design: [plugin-ecosystem-3.3-late-stage-design.md](plugin-ecosystem-3.3-late-stage-design.md). Implementation tracking: [plugin-ecosystem-3.3-late-stage-plan.md](plugin-ecosystem-3.3-late-stage-plan.md).

Initial direction:

- Define the compatibility matrix across `pf4boot`, `pf4boot-plugin`, Spring Boot, PF4J, JDK, and plugin package format.
- Add package verification for official samples to catch host API bundling mistakes.
- Provide WARN-mode compatibility checks and clear rejection reasons for old packages.

## E5 Plugin Repository And Distribution

Included in 3.3. Detailed design: [plugin-ecosystem-3.3-late-stage-design.md](plugin-ecosystem-3.3-late-stage-design.md). Implementation tracking: [plugin-ecosystem-3.3-late-stage-plan.md](plugin-ecosystem-3.3-late-stage-plan.md).

Initial direction:

- Freeze the offline-index format, checksum/trust manifest, release request, and cache boundary.
- Make repository release reuse `PluginDeploymentService` plan/replace/rollback.
- Do not introduce a centralized remote marketplace in the first version.

## E6 Management Console Sample UI

Included in 3.3. Detailed design: [plugin-ecosystem-3.3-late-stage-design.md](plugin-ecosystem-3.3-late-stage-design.md). Implementation tracking: [plugin-ecosystem-3.3-late-stage-plan.md](plugin-ecosystem-3.3-late-stage-plan.md).

Initial direction:

- Keep UI as a sample or independent multi-module project.
- Call only `/pf4boot/admin/**` and read-only Actuator endpoints.
- Cover plugin list, deployment plan, replace, rollback, JPA reload, audit, and error display.

## Phased Plan

| Phase | Scope | Output | Acceptance |
| --- | --- | --- | --- |
| P1 | E1-E3 design freeze | Roadmap, developer experience design, plan, English translations | Links added to `docs/design/README.md` |
| P2 | E1 developer guide rewrite | Rewritten `plugin-developer-guide.md` and translation | Six plugin paths and troubleshooting covered |
| P3 | E2 1.7.0 baseline alignment | Build script, sample style, and terminology alignment | No misleading legacy terminology; sample commands pass |
| P4 | E3 template/sample organization | Sample READMEs, template matrix, verification commands | cross-plugin-jpa, saga-outbox, and console docs close the loop |
| P5 | E4 detailed design | Detailed design and plan completed | Check tasks, error codes, and matrix format defined |
| P6 | E5 detailed design | Detailed design and plan completed | offline-index, release request, and cache rules defined |
| P7 | E6 detailed design | Detailed design and plan completed | UI boundary, API mapping, and acceptance defined |

## Definition Of Done

The first 3.3 batch is complete when:

1. E1-E3 design and planning docs exist with English translations.
2. The developer guide rewrite scope, target structure, and acceptance are clear.
3. The `pf4boot-plugin 1.7.0` consumption boundary is clear.
4. The official template matrix and sample responsibilities are clear.
5. E4-E6 are included in the 3.3 roadmap but do not block E1-E3 implementation.
