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
- [plugin-loading-and-packaging.md](plugin-loading-and-packaging.md): repository, loader, Gradle plugin packaging, and app assembly.
- [code-quality-fixes.md](code-quality-fixes.md): issues found in this code quality review and the fix plan.
- [lifecycle-cleanup-fix.md](lifecycle-cleanup-fix.md): fix plan for plugin stop and context cleanup responsibility boundaries.
- [scheduler-sharingbeans-fix.md](scheduler-sharingbeans-fix.md): fix plan for auto-start scheduler idempotency and shared bean record keys.
- [autoexport-jpa-boundary.md](autoexport-jpa-boundary.md): AutoExport groups and JPA dynamic metadata capability boundary.
- [production-readiness-roadmap.md](production-readiness-roadmap.md): production readiness roadmap for verification, observability, JPA boundaries, plugin governance, and documentation.
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
