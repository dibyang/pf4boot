# Cross-Plugin JPA Transaction Capability Implementation Plan (Tracking)

## 1. Scope

- Implement optional cross-plugin shared transactions by domain.
- Keep non-JPA plugins unaffected.
- Extend existing `pf4boot-jpa` / `pf4boot-jpa-starter` with `LOCAL/SHARED`.
- Current scope is phase 1 only: same-domain sharing with domain isolation; no cross-domain atomic transaction.

## 2. Milestones and Schedule

| Milestone | Planned Effort | Objective | Owner | Deliverable | Exit Criteria |
| --- | --- | --- | --- | --- | --- |
| Milestone 1: Design Freeze | 1 day | Freeze behavior and failure semantics | Platform owner | Config semantics, bean naming, error code version | Review approved and contract stable |
| Milestone 2: Starter Upgrade | 2 days | Implement `mode=LOCAL/SHARED` and domain validation | Framework engineering | `pf4boot-jpa-starter` changes | Local mode regression and shared-mode failures verified |
| Milestone 3: Domain Capability | 2 days | Introduce domain capability plugin | Framework engineering | `pf4boot-jpa-domain-starter` module | Single-domain shared startup and TM sharing |
| Milestone 4: Samples and Docs | 1 day | Runnable examples and migration guide | Sample engineering | `samples/cross-plugin-jpa` examples + migration docs | Single-domain and multi-domain paths runnable |
| Milestone 5: Stabilization | 1 day | Close verification + traceability | QA/engineering | Verification logs and risk register | All acceptance criteria satisfied |

## 3. Task Breakdown

### M1 Design Freeze

- [x] Fix naming and injection convention for `domain-id`.
- [x] Fix `PJF-*` error-code and logging text.
- [x] Fix `plugin.properties` dependency convention.
- [x] Fix shared-domain entity ownership and scan boundary.
- [x] Define fail-fast behavior when `SHARED` domain is missing.

### M2 Starter Upgrade

- [x] Add `mode` parsing (`LOCAL` / `SHARED`).
- [x] In `SHARED`, bind shared EMF/TM only and skip local EMF/TM creation.
- [x] Fail-fast for missing `domain-id` in `SHARED` mode.
- [x] Emit `PJF-*` errors when shared EMF/TM is missing.
- [x] Add minimal shared-mode sample configuration.

### M3 Capability Module

- [x] Add `pf4boot-jpa-domain-starter` module (dependencies and plugin wiring).
- [x] Export `DataSource/EntityManagerFactory/TransactionManager`.
- [x] Align with `PLATFORM` shared strategy.
- [x] Improve startup failure logs for domain context and dependency chain.

### M4 Samples and Docs

- [x] Add single-domain shared scenario in `samples/cross-plugin-jpa`.
- [x] Add sample with two domains in one plugin (`@EntityScan` + `@EnableJpaRepositories`).
- [x] Add migration guide (`LOCAL` -> `SHARED`).
- [x] Update design index and examples documentation.

### M5 Acceptance Closure

- [x] Execute command/manual checks from acceptance checklist.
- [x] Run failure injection tests (capability plugin unhealthy path).
- [x] Clear remaining risks and close action items.

## 4. Preconditions

- Existing code in `pf4boot-jpa` and `pf4boot-jpa-starter` supports incremental changes without breaking local behavior.
- Repo packaging and plugin loader path can include new module cleanly.
- Build/verification environment can run Gradle compile tasks and plugin builds.
- Verification environment can simulate degraded capability startup.

## 5. Risk Fallback

- If Milestone 2 hits compatibility issues, keep local mode unchanged and first land feature flags.
- If examples are unstable, pause docs and lock to minimum compile/boot sanity path.
- Adopt two-step rollout: baseline local mode unchanged + shared mode optional path, then sample completeness.

## 6. Tracking Fields

- Plan start date: 2026-06-10
- Actual start date: 2026-06-10
- Finish date: 2026-06-10
- Current status: M1-M5 completed.
- Open items: no blockers; a long-running HTTP smoke record can be added later in a real runtime environment.
- Owner: Codex

Update this document only with progress state. Keep implementation details in the design and PRs.
