# Plugin Framework Follow-Up Hardening Acceptance Tracking

## Usage

This file tracks acceptance status for P10-A/P10-B/P10-C from [plugin-framework-follow-up-hardening-plan.md](plugin-framework-follow-up-hardening-plan.md).

Statuses:

- `Planned`: planned but not implemented.
- `In Progress`: implementation in progress.
- `Done`: completed with evidence.
- `Blocked`: blocked by external conditions.

Only mark rows `Done` after implementation, documentation, and verification are complete.

## P10-A Repository Real Replace

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P10-A-AC1: configuration separates repository dry-run and real replace | Planned | Pending implementation |
| P10-A-AC2: release package enters controlled staging cache and sha256 is rechecked | Planned | Pending implementation |
| P10-A-AC3: verification failure does not enter replace | Planned | Pending implementation |
| P10-A-AC4: real replace reuses existing rollback orchestration | Planned | Pending implementation |
| P10-A-AC5: idempotency replay does not execute replace again | Planned | Pending implementation |
| P10-A-AC6: records and Actuator expose repository execution summaries without absolute paths | Planned | Pending implementation |

## P10-B Cross-Platform Runtime Smoke

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P10-B-AC1: `runtimeSmoke` task remains discoverable and keeps the same command | Planned | Pending implementation |
| P10-B-AC2: Java or cross-platform runner executes the full smoke | Planned | Pending implementation |
| P10-B-AC3: success and failure both generate `result.json` | Planned | Pending implementation |
| P10-B-AC4: JUnit XML is generated and CI-collectable | Planned | Pending implementation |
| P10-B-AC5: PowerShell script remains available as the Windows entry | Planned | Pending implementation |
| P10-B-AC6: reports contain no tokens, private keys, full stacks, or sensitive absolute paths | Planned | Pending implementation |

## P10-C no-jpa/unrelated Isolation Sample

| Acceptance Item | Status | Evidence |
| --- | --- | --- |
| P10-C-AC1: unrelated plugin does not depend on JPA starter or datasource provider | Planned | Pending implementation |
| P10-C-AC2: unrelated plugin starts and responds in the normal scenario | Planned | Pending implementation |
| P10-C-AC3: when JPA provider is missing, JPA consumer fails and unrelated plugin still works | Planned | Pending implementation |
| P10-C-AC4: when JPA provider startup fails, unrelated plugin still works | Planned | Pending implementation |
| P10-C-AC5: runtime smoke report includes `unrelatedPluginAlive` | Planned | Pending implementation |
| P10-C-AC6: README and developer guide document isolation semantics | Planned | Pending implementation |

## Current Recommendation

Implement P10-C first, then P10-B, and P10-A last. This establishes runtime isolation first, makes verification cross-platform, then enables the real repository deployment chain.
