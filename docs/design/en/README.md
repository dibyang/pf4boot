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
- [plugin-loading-and-packaging.md](plugin-loading-and-packaging.md): repository, loader, Gradle plugin packaging, and app assembly.
- [code-quality-fixes.md](code-quality-fixes.md): issues found in this code quality review and the fix plan.

Create or update a design document when a change affects:

- public APIs or annotations in `pf4boot-api`;
- plugin lifecycle, class loading, repositories, loaders, or event behavior in `pf4boot-core`;
- Spring Boot auto-configuration in starter modules;
- web or JPA integration contracts;
- Gradle dependency scopes, plugin packaging, or `app-run` packaging;
- behavior shared by demo apps and sample plugins.

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
