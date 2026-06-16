# Design Document Governance

## Problem

Historically, `docs/design` held current designs, implementation plans, acceptance records, one-off fix notes, and roadmaps at the same level. As the directory grew, later development work could no longer easily tell which documents were still authoritative.

## Scope

- `docs/design`: current design facts and development entry points.
- `docs/design/decisions`: long-lived architectural decisions.
- `docs/design/archive`: historical plans, acceptance records, and stage roadmaps.
- `docs/design/en`: English structure mirrors the Chinese structure, while the Chinese documents remain canonical.

## Layers

- Current design: describes the active architecture, module boundaries, runtime contracts, or development guidance. Keep it at the top level of `docs/design`.
- Long-lived decision: records settled trade-offs that future work must continue to respect. Put it under `docs/design/decisions`.
- Historical archive: completed, superseded, or traceability-only plans, acceptance records, roadmaps, and one-off fix notes. Put it under `docs/design/archive`.
- English translation: current designs and long-lived decisions should stay synchronized with Chinese documents. Historical archive translations are retained, but are not expected to be continuously maintained.

## New Document Rules

For non-trivial design work, prefer updating an existing current design document. Add a new file only when:

- the change introduces a new public API, lifecycle behavior, class loading rule, auto-configuration contract, Web/JPA contract, or plugin packaging boundary;
- a long-lived decision must be recorded because future work will repeatedly refer to it;
- extending an existing current design document would materially hurt readability.

Implementation plans and acceptance checklists should not be added to the top level of `docs/design`. If they must be recorded, place them under `docs/design/archive`, or keep them in the related issue, pull request, or commit message.

## Migration Rules

When cleaning up historical documents, do not delete content by default. Move it to the archive and keep a README entry. If a later change confirms that a historical document is fully superseded, delete it in a separate change and mention the replacement document in the commit message.

## Verification

Documentation governance changes do not require Gradle verification. Before finishing, check:

- `docs/design/README.md` points only to existing documents;
- Chinese documents remain valid UTF-8;
- the English README follows the same structure as the Chinese README;
- archived documents remain reachable through `archive/README.md`.
