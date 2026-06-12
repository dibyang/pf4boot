# Plugin Management Console UI Boundary Decision

## Background

pf4boot already has HTTP management APIs, security governance, idempotency, deployment records, Actuator read-only observation, and runtime smoke. A graphical console could help users inspect plugin status, run deployments, and view diagnostics. But UI can pull frontend assets, session handling, permission models, audit presentation, and remote access policy into the framework core.

This decision defines whether console UI is in framework scope and how UI relates to HTTP APIs, Actuator, core, and starters.

## Goals

- Decide whether core/starter modules include UI.
- Lock UI to HTTP management APIs and read-only Actuator endpoints.
- Prevent UI from affecting lifecycle, deployment orchestration, or security boundaries.
- Provide boundaries for future independent sample UI or external console.

## Non-Goals

- P6 does not implement UI.
- Do not package frontend console resources in `pf4boot-core`, `pf4boot-starter`, or `pf4boot-management-starter`.
- Do not add frontend framework, session management, or Spring Security hard dependencies.
- Do not change HTTP management authentication requirements.

## Current Flow

| Capability | Current State |
| --- | --- |
| Write API | `/pf4boot/admin/**` with local token, delegated auth SPI, and idempotency |
| Read-only observation | `/actuator/pf4bootplugins`, `/actuator/pf4bootgovernance`, metrics |
| Security | Local calls should still use tokens; remote mode delegates authorization |
| Smoke | Runtime smoke verifies token, idempotency, failure records, and actuator |
| Gap | No UI layer or API contract artifact |

## Core Constraints

- UI must not become a core/starter dependency.
- UI must not bypass HTTP APIs and call Java managers directly.
- Write operations must carry existing auth and idempotency headers.
- Actuator stays read-only.
- Deployment UI must show risk, precheck, rollback, and failure evidence.

## Alternatives

| Option | Description | Pros | Cons | Decision |
| --- | --- | --- | --- | --- |
| A. No built-in UI | Framework provides APIs and Actuator only | Cleanest boundary, low maintenance | Users build their own UI | Recommended default |
| B. Independent sample UI | A `samples/*` demo consumes HTTP APIs | Demonstrates full flow without polluting core | Still needs sample maintenance | Future optional |
| C. Static UI inside starter | Management starter packages static frontend assets | Out-of-box UI | Security/version/frontend dependencies enter starter | Rejected first stage |
| D. External console service | Independent app/platform manages multiple hosts | Production-friendly for fleets | Needs auth, tenancy, and network governance | Future independent project |

## Recommendation

Do not include management console UI in the framework. If a demo is needed, provide it later under `samples/plugin-management-console` or an independent repository. Production consoles should be external applications using HTTP management APIs and Actuator endpoints.

## API Boundary Draft

UI may call:

| Type | Path | Description |
| --- | --- | --- |
| Management read/write | `/pf4boot/admin/**` | Plugin lifecycle, deployment plan/replace/rollback, deployment records |
| Read-only observation | `/actuator/pf4bootplugins` | Plugin snapshots |
| Governance summary | `/actuator/pf4bootgovernance` | Trust/capability/deployment/cleanup summaries |
| Metrics | `/actuator/metrics/pf4boot.*` | Metrics |

UI must not call:

- `Pf4bootPluginManager` Java bean.
- `PluginDeploymentService` Java bean.
- Plugin internal repositories or services.
- Internal diagnostic objects outside Actuator.

## Configuration Draft

If a sample UI exists later, configuration belongs only in the sample:

```yaml
sample:
  pf4boot-console:
    api-base-url: http://127.0.0.1:7791
    token-header: X-PF4Boot-Admin-Token
```

Framework modules must not add `spring.pf4boot.console.enabled`, because that implies built-in UI is a framework capability.

## State Machine

UI must display backend `DeploymentState` semantics:

```text
PLANNED -> PRECHECKING -> DRAINING -> STOPPING -> REPLACING
  -> STARTING -> HEALTH_CHECKING -> SUCCEEDED
Any executing state -> ROLLING_BACK -> ROLLED_BACK / MANUAL_INTERVENTION
```

UI must not invent backend state semantics.

## Sequence

1. UI reads `/actuator/pf4bootgovernance` for overall risk.
2. UI calls `/pf4boot/admin/plugins` for plugin list.
3. After package selection, UI calls deployment plan first.
4. UI shows precheck result, impact scope, and rollback info.
5. User confirms, then UI calls replace with `X-Idempotency-Key`.
6. UI polls deployment record and displays success, rollback, or manual intervention.

## Error Handling

| Error | UI Behavior |
| --- | --- |
| 401/403 | Show auth failure; do not retry write operation blindly |
| 409 idempotency conflict | Show existing operation/deployment info |
| Precheck failure | Show error code, impact scope, and remediation |
| `MANUAL_INTERVENTION` | Show deployment record and log hints |

## Compatibility

- No built-in UI means no behavior change.
- HTTP API remains the only write entrypoint.
- Actuator remains read-only.
- Future sample UI does not affect published modules.

## Rollback

If a future sample UI fails, remove or disable the sample/external console. Backend APIs and deployment records remain usable through curl or scripts.

## Verification

Future sample UI needs:

- API contract tests.
- Auth failure and idempotency conflict presentation tests.
- Deployment plan/replace/rollback end-to-end smoke.
- Permission separation tests for read-only Actuator vs write APIs.

## Risks

| Risk | Mitigation |
| --- | --- |
| UI encourages exposing local management token | Mark sample local-only; production uses enterprise auth |
| UI bypasses backend precheck | Forbid direct Java bean access; HTTP API only |
| Frontend dependencies pollute starter | UI stays outside starter |
| UI state differs from backend | UI displays backend state enum and deployment records only |

## Entry Criteria

- HTTP management API contract is stable and has OpenAPI or equivalent docs.
- Actuator governance summary is stable.
- A concrete user scenario needs sample UI.
- UI is maintained as a sample or external service, not core/starter.

## Final Decision

Do not implement UI now. pf4boot remains API-first. Console UI does not enter core/starter/management starter. Future demo UI, if needed, must be an independent sample. Production consoles should be external applications consuming HTTP management APIs and Actuator.
