# Design Documents English Translation

This directory contains English translations of the canonical Chinese design documents in `docs/design`.
When design content changes, update the Chinese document first and keep this translation in sync.

Use the parent directory for design notes before implementing non-trivial changes.

## Existing Design

- [architecture.md](architecture.md): module layout and runtime architecture.
- [plugin-lifecycle.md](plugin-lifecycle.md): plugin loading, start, stop, reload, and cleanup flow.
- [context-and-bean-sharing.md](context-and-bean-sharing.md): Spring context hierarchy, exported beans, extensions, and events.
- [web-integration.md](web-integration.md): dynamic MVC mappings, interceptors, and plugin static resources.
- [jpa-integration.md](jpa-integration.md): plugin JPA starter behavior and entity scan rules.
- [cross-plugin-jpa-transaction-capability.md](cross-plugin-jpa-transaction-capability.md): cross-plugin JPA transaction capability design and rollout plan.
- [cross-plugin-jpa-transaction-capability-plan.md](cross-plugin-jpa-transaction-capability-plan.md): execution plan and tracking for this capability.
- [cross-plugin-jpa-transaction-capability-acceptance.md](cross-plugin-jpa-transaction-capability-acceptance.md): acceptance checklist and verification tracking for this capability.
- [cross-plugin-jpa-transaction-migration.md](cross-plugin-jpa-transaction-migration.md): migration guide and configuration examples for cross-plugin JPA transactions.
- [cross-plugin-jpa-transaction-complex-sample.md](cross-plugin-jpa-transaction-complex-sample.md): complex cross-plugin JPA sample split.
- [cross-plugin-jpa-transaction-complex-sample-plan.md](cross-plugin-jpa-transaction-complex-sample-plan.md): implementation plan for the complex cross-plugin JPA sample.
- [cross-plugin-jpa-transaction-complex-sample-acceptance.md](cross-plugin-jpa-transaction-complex-sample-acceptance.md): acceptance record for the complex cross-plugin JPA sample.
- [cross-plugin-jpa-transaction-improvement.md](cross-plugin-jpa-transaction-improvement.md): current-state improvement design for cross-plugin JPA transactions.
- [cross-plugin-jpa-transaction-improvement-plan.md](cross-plugin-jpa-transaction-improvement-plan.md): implementation plan for cross-plugin JPA transaction improvements.
- [plugin-hot-replacement-deployment-improvement.md](plugin-hot-replacement-deployment-improvement.md): current-state improvement design for plugin hot replacement deployment.
- [plugin-hot-replacement-deployment-improvement-plan.md](plugin-hot-replacement-deployment-improvement-plan.md): implementation plan for plugin hot replacement deployment improvements.
- [plugin-hot-replacement-deployment-acceptance.md](plugin-hot-replacement-deployment-acceptance.md): acceptance record for plugin hot replacement deployment.
- [plugin-http-management-api.md](plugin-http-management-api.md): complete plugin HTTP management API design.
- [plugin-http-management-api-plan.md](plugin-http-management-api-plan.md): implementation plan for plugin HTTP management APIs.
- [plugin-http-management-api-implementation-guide.md](plugin-http-management-api-implementation-guide.md): implementation guide for plugin HTTP management APIs.
- [plugin-http-management-api-acceptance.md](plugin-http-management-api-acceptance.md): acceptance checklist for plugin HTTP management APIs.
- [plugin-http-management-api-small-model-guide.md](plugin-http-management-api-small-model-guide.md): deterministic execution guide for smaller implementation models.
- [plugin-loading-and-packaging.md](plugin-loading-and-packaging.md): repository, loader, Gradle plugin packaging, and app assembly.
- [code-quality-fixes.md](code-quality-fixes.md): issues found in this code quality review and the fix plan.
- [lifecycle-cleanup-fix.md](lifecycle-cleanup-fix.md): fix plan for plugin stop and context cleanup responsibility boundaries.
- [scheduler-sharingbeans-fix.md](scheduler-sharingbeans-fix.md): fix plan for auto-start scheduler idempotency and shared bean record keys.
- [autoexport-jpa-boundary.md](autoexport-jpa-boundary.md): AutoExport groups and JPA dynamic metadata capability boundary.
- [production-readiness-roadmap.md](production-readiness-roadmap.md): production readiness roadmap for verification, observability, JPA boundaries, plugin governance, and documentation.
- [plugin-framework-production-hardening.md](plugin-framework-production-hardening.md): production hardening design for package trust, persistent records, lifecycle verification, capability manifests, and observability closure.
- [plugin-framework-production-hardening-plan.md](plugin-framework-production-hardening-plan.md): implementation plan for plugin framework production hardening.
- [plugin-framework-production-hardening-acceptance.md](plugin-framework-production-hardening-acceptance.md): acceptance tracking for plugin framework production hardening.
- [plugin-framework-next-stage-hardening.md](plugin-framework-next-stage-hardening.md): next-stage production hardening design for offline repositories, strict version-range prechecks, and Gradle/CI runtime smoke.
- [plugin-framework-next-stage-hardening-plan.md](plugin-framework-next-stage-hardening-plan.md): implementation plan for next-stage plugin framework hardening.
- [plugin-framework-next-stage-hardening-acceptance.md](plugin-framework-next-stage-hardening-acceptance.md): acceptance tracking for next-stage plugin framework hardening.
- [plugin-framework-follow-up-hardening.md](plugin-framework-follow-up-hardening.md): follow-up hardening design for repository real replace, cross-platform smoke, and no-jpa isolation samples.
- [plugin-framework-follow-up-hardening-plan.md](plugin-framework-follow-up-hardening-plan.md): implementation plan for follow-up plugin framework hardening.
- [plugin-framework-follow-up-hardening-acceptance.md](plugin-framework-follow-up-hardening-acceptance.md): acceptance tracking for follow-up plugin framework hardening.
- [jpa-runtime-refresh-decision.md](jpa-runtime-refresh-decision.md): decision for JPA runtime refresh and EntityManagerFactory rebuild.
- [jpa-runtime-refresh.md](jpa-runtime-refresh.md): JPA runtime refresh design for PLAN_ONLY, impact analysis, provider restart based refresh, and management APIs.
- [jpa-runtime-refresh-plan.md](jpa-runtime-refresh-plan.md): implementation plan for JPA runtime refresh.
- [jpa-runtime-refresh-acceptance.md](jpa-runtime-refresh-acceptance.md): acceptance checklist for JPA runtime refresh.
- [cross-datasource-transaction-decision.md](cross-datasource-transaction-decision.md): decision for cross-datasource transaction boundaries, Saga/Outbox, and optional XA.
- [plugin-repository-governance-decision.md](plugin-repository-governance-decision.md): decision for offline plugin repository, signed releases, rollout, and rollback governance.
- [plugin-management-console-boundary.md](plugin-management-console-boundary.md): decision for management console UI boundaries against HTTP APIs and Actuator.
- [plugin-developer-guide.md](plugin-developer-guide.md): plugin development, dependency scopes, package verification, read-only observability, JPA, and upgrade rollback guide.

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
