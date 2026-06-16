# Architectural Decisions

This directory stores long-lived architectural decisions. Decision documents explain settled boundaries, trade-offs, and future constraints. Implementation details remain in the current design documents at the top level of `docs/design`.

## Decision Records

- [cross-datasource-transaction-decision.md](cross-datasource-transaction-decision.md): cross-datasource transaction boundaries, Saga/Outbox, and optional XA module decision.
- [jpa-runtime-refresh-decision.md](jpa-runtime-refresh-decision.md): JPA runtime refresh and EntityManagerFactory rebuild decision.
- [plugin-management-console-boundary.md](plugin-management-console-boundary.md): management console UI boundary against HTTP APIs and Actuator.
- [plugin-repository-governance-decision.md](plugin-repository-governance-decision.md): offline plugin repository, signed releases, rollout, and rollback governance decision.

## New Decision Rules

Add a decision document only when the decision will keep constraining public APIs, module boundaries, plugin lifecycle, class loading, auto-configuration, packaging, or operational governance. One-off implementation plans and acceptance checklists belong in `docs/design/archive` or in issues, pull requests, and commit messages.
