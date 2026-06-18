# Design Documents

This directory contains English translations of the current design documents. The Chinese documents in the parent directory are canonical.

Historical plans, acceptance records, and stage roadmaps have moved to [archive/](archive/) for traceability only. Long-lived architectural decisions live in [decisions/](decisions/).

## Reading Path

For new development work, read these first:

1. [architecture.md](architecture.md): module layout and runtime architecture.
2. [plugin-loading-and-packaging.md](plugin-loading-and-packaging.md): plugin repository, loader, Gradle packaging, and app assembly.
3. [plugin-lifecycle.md](plugin-lifecycle.md): plugin loading, start, stop, reload, and cleanup flow.
4. [context-and-bean-sharing.md](context-and-bean-sharing.md): Spring context hierarchy, exported beans, extension points, and events.
5. [plugin-developer-guide.md](plugin-developer-guide.md): plugin development, dependency scopes, package verification, observability, JPA, and rollback guidance.
6. [plugin-ecosystem-3.3-roadmap.md](plugin-ecosystem-3.3-roadmap.md): 3.3 plugin ecosystem roadmap.
7. [plugin-developer-experience-3.3-design.md](plugin-developer-experience-3.3-design.md): first three 3.3 plugin developer experience design topics.
8. [plugin-developer-experience-3.3-plan.md](plugin-developer-experience-3.3-plan.md): implementation plan and acceptance for the first three 3.3 topics.
9. [plugin-ecosystem-3.3-late-stage-design.md](plugin-ecosystem-3.3-late-stage-design.md): late-stage 3.3 plugin ecosystem design.
10. [plugin-ecosystem-3.3-late-stage-plan.md](plugin-ecosystem-3.3-late-stage-plan.md): implementation plan and acceptance for the last three 3.3 topics.
11. [plugin-app-quickstart-template.md](plugin-app-quickstart-template.md): quickstart host application and minimal plugin template.

## By Topic

### Core Framework

- [architecture.md](architecture.md): overall architecture.
- [plugin-lifecycle.md](plugin-lifecycle.md): lifecycle and cleanup boundaries.
- [context-and-bean-sharing.md](context-and-bean-sharing.md): context and bean sharing.
- [plugin-loading-and-packaging.md](plugin-loading-and-packaging.md): loading, repositories, and packaging.
- [plugin-compatibility-matrix.md](plugin-compatibility-matrix.md): plugin compatibility matrix and package verification report.
- [starter-boundary-split.md](starter-boundary-split.md): starter boundary split.

### Web And Management APIs

- [web-integration.md](web-integration.md): dynamic MVC mappings, interceptors, and plugin static resources.
- [plugin-http-management-api.md](plugin-http-management-api.md): plugin HTTP management APIs.
- [plugin-http-management-api-hardening.md](plugin-http-management-api-hardening.md): management API hardening.
- [decisions/plugin-management-console-boundary.md](decisions/plugin-management-console-boundary.md): management console and backend API boundary.

### JPA And Transactions

- [jpa-integration.md](jpa-integration.md): plugin JPA starter behavior and entity scan rules.
- [jpa-plugin-owned-configuration-plan.md](jpa-plugin-owned-configuration-plan.md): JPA plugin-owned configuration remediation plan.
- [autoexport-jpa-boundary.md](autoexport-jpa-boundary.md): AutoExport groups and JPA dynamic metadata boundary.
- [cross-plugin-jpa-transaction-capability.md](cross-plugin-jpa-transaction-capability.md): cross-plugin JPA transaction capability.
- [cross-plugin-jpa-transaction-improvement.md](cross-plugin-jpa-transaction-improvement.md): cross-plugin JPA transaction improvements.
- [cross-plugin-jpa-transaction-complex-sample.md](cross-plugin-jpa-transaction-complex-sample.md): complex sample split.
- [cross-plugin-jpa-transaction-migration.md](cross-plugin-jpa-transaction-migration.md): migration guide and configuration examples.
- [jpa-runtime-refresh.md](jpa-runtime-refresh.md): JPA runtime refresh.
- [jpa-runtime-refresh-drain-spi.md](jpa-runtime-refresh-drain-spi.md): drain SPI for JPA refresh.
- [jpa-management-starter-boundary.md](jpa-management-starter-boundary.md): optional JPA management starter boundary.
- [decisions/cross-datasource-transaction-decision.md](decisions/cross-datasource-transaction-decision.md): cross-datasource transaction boundary decision.
- [decisions/jpa-runtime-refresh-decision.md](decisions/jpa-runtime-refresh-decision.md): JPA runtime refresh decision.

### Production And Operations

- [next-version-production-goals.md](next-version-production-goals.md): next-version production goals.
- [next-version-production-design.md](next-version-production-design.md): next-version production implementation design.
- [plugin-hot-replacement-deployment-improvement.md](plugin-hot-replacement-deployment-improvement.md): plugin hot replacement deployment improvements.
- [runtime-safety-phase3.md](runtime-safety-phase3.md): runtime safety hardening.
- [verification-foundation.md](verification-foundation.md): verification foundation.
- [decisions/plugin-repository-governance-decision.md](decisions/plugin-repository-governance-decision.md): plugin repository governance decision.

### Plugin Ecosystem And Developer Experience

- [plugin-ecosystem-3.3-roadmap.md](plugin-ecosystem-3.3-roadmap.md): six-goal 3.3 roadmap.
- [plugin-developer-experience-3.3-design.md](plugin-developer-experience-3.3-design.md): design for the official developer guide, `pf4boot-plugin 1.7.0` baseline, and templates/samples.
- [plugin-developer-experience-3.3-plan.md](plugin-developer-experience-3.3-plan.md): implementation phases, acceptance, and tracking for the first three 3.3 topics.
- [plugin-ecosystem-3.3-late-stage-design.md](plugin-ecosystem-3.3-late-stage-design.md): design for compatibility matrix, package verification, plugin repository/distribution, and management console sample UI.
- [plugin-ecosystem-3.3-late-stage-plan.md](plugin-ecosystem-3.3-late-stage-plan.md): implementation phases, acceptance, and tracking for the last three 3.3 topics.
- [plugin-compatibility-matrix.md](plugin-compatibility-matrix.md): 3.3 compatibility matrix and sample plugin package verification rules.
- [plugin-app-quickstart-template.md](plugin-app-quickstart-template.md): quickstart application template, basic host/plugin, and JPA upgrade path.

## Governance

- [document-governance.md](document-governance.md): design document layering, archiving, and creation rules.
- [archive/README.md](archive/README.md): historical document index.
- [decisions/README.md](decisions/README.md): long-lived decision index.

## When To Add Or Update A Design Document

Create or update a design document when a change affects:

- public APIs or annotations in `pf4boot-api`;
- plugin lifecycle, class loading, repositories, loaders, or event behavior in `pf4boot-core`;
- Spring Boot auto-configuration in starter modules;
- web or JPA integration contracts;
- Gradle dependency scopes, plugin packaging, or sample host assembly;
- behavior shared by sample apps and sample plugins.

Small mechanical fixes can use a short design in the conversation, but the implementation must still follow `docs/constraints/README.md`.

## Template

```markdown
# Title

## Problem

What needs to change and why.

## Affected Modules

- `module-name`: expected role in the change.

## Proposed Design

The intended behavior, module boundary, and important alternatives considered.

## Compatibility

Source, binary, runtime, plugin packaging, and configuration compatibility notes.

## Verification

The smallest useful Gradle command and any manual checks.

## Open Questions

Known uncertainties or decisions that still need confirmation.
```
