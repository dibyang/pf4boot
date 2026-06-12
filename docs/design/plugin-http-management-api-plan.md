# Plugin HTTP Management API Execution Plan

## Objective

- Deliver a complete HTTP management surface with explicit security, idempotency, audit, and deployment lifecycle behavior.
- Provide implementation + acceptance checkpoints so each stage can be independently verified before local commit.

This implementation is aligned with:
- `docs/design/plugin-http-management-api.md`
- `docs/design/plugin-http-management-api-acceptance.md`
- `docs/design/plugin-http-management-api-implementation-guide.md`

## Stage Progress

Current status summary:

1. `M1` module scaffolding: **DONE**
2. `M2` API contracts: **DONE**
3. `M3` local-token security basics: **DONE**
4. `M4` lifecycle read/write APIs: **DONE**
5. `M5` deployment endpoints: **DONE**
6. `M6` security policy + authz: **DONE**
7. `M7` idempotency + audit: **DONE**
8. `M8` sample + usage docs: **DONE** (interface design and usage docs; runtime smoke depends on environment)

## M1 — Module scaffold

Deliverables:
- Add `pf4boot-management-starter` in `settings.gradle` and module path.
- Add starter wiring for properties, auto-configuration, beans, and exception handler.

Validation:
- `.\gradlew.bat :pf4boot-api:compileJava`
- `.\gradlew.bat :pf4boot-management-starter:compileJava`

## M2 — Management contracts

Deliverables:
- API types in `pf4boot-api`:
  - `PluginAdminResponse`
  - `PluginManagementOperation`
  - `PluginManagementMode`
  - `PluginManagementRequest`
  - `PluginManagementPrincipal`
  - `PluginManagementAuthorizer`
  - `PluginManagementAuditEvent`
  - `PluginManagementErrorCode`
  - `PluginOperationStore` + request/operation records

Validation:
- `.\gradlew.bat :pf4boot-api:test`

## M3 — Local security model

Deliverables:
- `LocalTokenPluginManagementAuthorizer` with fixed-time compare, loopback guard, token checks.
- Startup validation for invalid mode combinations.

Validation:
- `.\gradlew.bat :pf4boot-management-starter:compileTestJava`
- `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementStartupValidatorTest"`

## M4 — Lifecycle read/write APIs

Deliverables:
- Read-only plugin list/detail APIs.
- Start/stop/restart/enable/disable/reload lifecycle mutation endpoints.
- Centralized auth + request context + audit + idempotency + error path handling.

Validation:
- `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementControllerTest"`

## M5 — Deployment APIs

Deliverables:
- `POST /deployments/plan` for pre-check only.
- `POST /deployments/replace` for real replacement.
- `POST /deployments/{deploymentId}/rollback` for rollback execution.
- `POST /deployments/{deploymentId}/confirm` executes a confirmed replacement from a `PRECHECKED` record.

Validation:
- `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementControllerTest"`
- `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementControllerSecurityTest"`

## M6 — Security policy

Deliverables:
- `PluginManagementWriteSecurityPolicy`:
  - CSRF/origin enforcement
  - rate limiting for write paths
- Authorizer SPI and permissions matrix.

Validation:
- `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementWriteSecurityPolicyTest"`
- `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementControllerSecurityTest"`
- `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementRateLimiterTest"`

## M7 — Idempotency + audit + path safety

Deliverables:
- `PluginManagementIdempotencyService` and in-memory stores.
- deployment/operation records.
- request hash and replay behavior.
- path guard against staging traversal and absolute escapes.

Validation:
- `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementIdempotencyServiceTest"`
- `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementPathValidatorTest"`

## M8 — Docs and smoke support

Deliverables:
- Updated acceptance/checklist docs with explicit completed/blocked items.
- Updated small-model execution guide for deterministic implementation order.
- Updated sample readme and curl recipes where applicable.

Validation:
- Documentation review and `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins` (environment dependent).

## Acceptance Handoff

The following docs are the handoff set:
- `plugin-http-management-api-acceptance.md` (status and evidence)
- `docs/design/en/plugin-http-management-api-small-model-guide.md` (execution order for low-context execution)

Any stage where runtime checks are not executed locally must remain `In Progress` in smoke/test evidence.
