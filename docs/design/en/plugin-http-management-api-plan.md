# Plugin HTTP Management API Implementation Plan

## Tracking Rules

- Design: [plugin-http-management-api.md](plugin-http-management-api.md).
- Implementation guide: [plugin-http-management-api-implementation-guide.md](plugin-http-management-api-implementation-guide.md).
- This document tracks implementation tasks only.
- Status values: `Not Started`, `In Progress`, `Done`, `Blocked`.
- Update [plugin-http-management-api-acceptance.md](plugin-http-management-api-acceptance.md) when a milestone is completed.

## Milestones

| Milestone | Status | Goal | Verification |
| --- | --- | --- | --- |
| M1 Module and configuration skeleton | Done | Add `pf4boot-management-starter`, disabled by default | `:pf4boot-management-starter:compileJava` |
| M2 Public models and error codes | Done | DTOs, operation types, errors, SPI in `pf4boot-api` | `:pf4boot-api:compileJava` |
| M3 Local token mode | Done | loopback, token, startup validation | `:pf4boot-management-starter:compileJava` |
| M4 Read and lifecycle APIs | Done | list/detail/start/stop/restart/enable/disable | Controller code complete |
| M5 Deployment APIs | Done | plan/replace/rollback queries integrated with `PluginDeploymentService` | Controller wiring complete |
| M6 Remote authorization mode | Done | authorizer SPI, permissions, CSRF/origin, and rate-limit enforcement implemented | `PluginManagementControllerSecurityTest` plus `PluginManagementWriteSecurityPolicyTest` |
| M7 Idempotency and audit | Done | idempotency keys, response replay, audit events | `:pf4boot-management-starter:compileJava` |
| M8 Samples and docs | Done | local token and remote authorizer examples, migration notes | sample smoke/docs sync updated |

## M1 Module And Configuration Skeleton

Tasks:

- Add `pf4boot-management-starter` and include it in `settings.gradle`.
- Add `Pf4bootManagementProperties`.
- Keep auto-configuration disabled by default.
- Fail startup for invalid combinations:
  - `enabled=true` and `mode=DISABLED`;
  - `LOCAL_TOKEN` without token;
  - `REMOTE_DELEGATED` without authorizer.

Verification:

```powershell
.\gradlew.bat :pf4boot-api:compileJava
.\gradlew.bat :pf4boot-management-starter:compileJava
```

## M2 Public Models And Error Codes

Tasks:

- Add to `pf4boot-api`:
  - `PluginAdminResponse<T>`
  - `PluginManagementOperation`
  - `PluginManagementRequest`
  - `PluginManagementPrincipal`
  - `PluginManagementAuthorizer`
  - `PluginManagementAuditEvent`
  - `PluginManagementErrorCode`
- Keep DTOs free of mutable internals and sensitive paths.
- Preserve Java 8 compatibility.

Verification:

```powershell
.\gradlew.bat :pf4boot-api:test
```

## M3 Local Token Mode

Tasks:

- Implement `LocalTokenPluginManagementAuthorizer`.
- Check IPv4 and IPv6 loopback addresses.
- Compare tokens in fixed time.
- Require token by default.
- Return unified errors and audit records for rejected requests.

## M4 Read And Lifecycle APIs

Tasks:

- Implement list/detail APIs using `PluginRuntimeInspector` or `Pf4bootPluginManager`.
- Implement start/stop/restart/enable/disable.
- Keep reload as low-level fallback with `pf4boot:plugin:reload`.
- Ensure writes enter existing lifecycle locking or manager operations.
- Return the unified response model.

## M5 Deployment APIs

Tasks:

- Implement `POST /deployments/plan` via `PluginDeploymentService.planReplacement`.
- Implement `POST /deployments/replace` via `PluginDeploymentService.replace`.
- Implement deployment record query and rollback entrypoint.
- Restrict staged package paths to configured staging roots.

## M6 Remote Authorization Mode

Tasks:

- Integrate `PluginManagementAuthorizer`.
- Add permission checks for read, lifecycle, reload, plan, replace, rollback, and admin.
- Add CSRF/origin strategy and same-origin checks.
- Add per-subject write rate limits (memory fixed-window).

## M7 Idempotency And Audit

Tasks:

- Parse `X-Idempotency-Key`.
- Store key, request hash, state, and response summary in memory.
- Replay same-key same-body responses.
- Return `409` for same-key different-body requests.
- Audit success, failure, rejection, rate limit, and idempotency hit events.

## M8 Samples And Docs

Tasks:

- Add local token configuration to the sample.
- Document curl flows for list, start/stop, plan, replace, and deployment record query.
- Add a remote authorizer integration example and migration notes.
- Keep Chinese and English indexes in sync.

Verification:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

## Recommended Order

1. M1-M3 for safe default behavior and local minimum protection.
2. M4 for standard HTTP lifecycle entrypoints.
3. M5 for release-grade deployment integration.
4. M6-M8 for remote security, idempotency, audit, and samples.

## Decisions To Track

| Question | Recommendation | Status |
| --- | --- | --- |
| Dedicated module? | Add `pf4boot-management-starter` | Decided |
| Strong Spring Security dependency? | No, SPI plus optional adapter | Decided |
| Persist idempotency/audit in phase one? | Memory first, SPI later | Decided |

## Follow-ups

- `POST /deployments/{deploymentId}/confirm` is intentionally deferred and tracked outside this release scope.
- Add dedicated runtime smoke for rollback happy-path/replay/confirm transition and rollback failure diagnostics in the next phase.
