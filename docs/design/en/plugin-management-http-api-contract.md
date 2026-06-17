# Plugin Management HTTP API Contract (Frontend/CLI)

This document is the English companion of `docs/design/plugin-management-http-api-contract.md`. The Chinese document is canonical.

## 1. Scope

`pf4boot-management-starter` exposes HTTP management APIs for management consoles, operation platforms, automation scripts, and CLIs.

- Default base path: `/pf4boot/admin`
- Unified response body: `PluginAdminResponse<T>`
- Auto-configuration condition: `spring.pf4boot.management.http.enabled=true`
- Disabled by default: `enabled=false` and `mode=DISABLED`
- All endpoints require `PluginManagementAuthorizer`, including read endpoints
- Time fields are Unix epoch milliseconds

When HTTP management is enabled, `mode=DISABLED` is invalid and startup validation fails.

## 2. Configuration

```yaml
spring:
  pf4boot:
    management:
      http:
        enabled: false
        base-path: /pf4boot/admin
        mode: DISABLED # DISABLED | LOCAL_TOKEN | REMOTE_DELEGATED
        allow-loopback-only: true
        token: ${PF4BOOT_ADMIN_TOKEN:}
        token-header: X-PF4Boot-Admin-Token
        require-idempotency-key: true
        idempotency-header: X-Idempotency-Key
        dry-run-default: true
        audit-enabled: true
        staging-root: plugins/staged
        max-recent-operations: 20
        operation-store:
          type: memory # memory | file
          directory: work/pf4boot/operations
          fail-closed: true
        rate-limit:
          enabled: true
          writes-per-minute: 30
        csrf:
          enabled: auto # true | false | auto
        allowed-operations: []
```

`LOCAL_TOKEN` uses `LocalTokenPluginManagementAuthorizer` by default. It requires a non-empty token unless a custom authorizer is supplied. With `allow-loopback-only=true`, requests must come from a loopback address.

`REMOTE_DELEGATED` requires at least one custom `PluginManagementAuthorizer` bean.

## 3. Common Protocol

### 3.1 Headers

| Header | Required | Description |
| --- | --- | --- |
| `Content-Type: application/json` | Required with body | JSON request body |
| `X-PF4Boot-Admin-Token` | Required in default `LOCAL_TOKEN` mode | Name is configurable by `token-header` |
| `X-Request-Id` | Optional | Server generates `req-{uuid}` if missing |
| `X-Idempotency-Key` | Required by default for management writes | Name is configurable by `idempotency-header` |
| `Origin` | Recommended for browser writes | Required and same-origin when `csrf.enabled=true`; checked in `auto` mode only when present |

### 3.2 Response Envelope

```json
{
  "success": true,
  "requestId": "req-8f4c1b65",
  "operationId": "op-21d3a979",
  "code": "OK",
  "message": "plugin list fetched",
  "data": {},
  "warnings": []
}
```

Failures use the same envelope. Global exception-handler responses usually have `requestId=null` and `operationId=null`.

## 4. Endpoint Summary

| Method | Path | Permission | Idempotency | Description |
| --- | --- | --- | --- | --- |
| `GET` | `/plugins` | `pf4boot:plugin:read` | No | List plugins |
| `GET` | `/plugins/{pluginId}` | `pf4boot:plugin:read` | No | Get one plugin |
| `POST` | `/plugins/{pluginId}/start` | `pf4boot:plugin:lifecycle` | Yes | Start plugin |
| `POST` | `/plugins/{pluginId}/stop` | `pf4boot:plugin:lifecycle` | Yes | Stop plugin |
| `POST` | `/plugins/{pluginId}/restart` | `pf4boot:plugin:lifecycle` | Yes | Restart plugin |
| `POST` | `/plugins/{pluginId}/reload` | `pf4boot:plugin:reload` | Yes | Low-level reload |
| `POST` | `/plugins/{pluginId}/enable` | `pf4boot:plugin:lifecycle` | Yes | Enable plugin |
| `DELETE` | `/plugins/{pluginId}/enable` | `pf4boot:plugin:lifecycle` | Yes | Disable plugin |
| `GET` | `/deployments` | `pf4boot:deployment:query` | No | Recent deployment records |
| `GET` | `/deployments/{deploymentId}` | `pf4boot:deployment:query` | No | One deployment record |
| `POST` | `/deployments/plan` | `pf4boot:deployment:plan` | Yes | Plan replacement |
| `POST` | `/deployments/replace` | `pf4boot:deployment:replace` | Yes | Replace or dry-run replace |
| `POST` | `/deployments/{deploymentId}/confirm` | `pf4boot:deployment:confirm` | Yes | Execute prechecked plan |
| `POST` | `/deployments/{deploymentId}/rollback` | `pf4boot:deployment:rollback` | Yes | Roll back by deployment plan |
| `POST` | `/jpa/domains/{domainId}/reload/plan` | `pf4boot:plugin:read` | No | Plan JPA domain reload |
| `POST` | `/jpa/domains/{domainId}/reload` | `pf4boot:plugin:reload` | Recommended | Execute JPA domain reload |
| `GET` | `/jpa/reloads/{reloadId}` | `pf4boot:plugin:read` | No | Get JPA reload record |
| `GET` | `/jpa/domains/{domainId}/reload/current` | `pf4boot:plugin:read` | No | Get current JPA reload |

Management lifecycle and deployment write endpoints use the management idempotency service, rate limiter, origin policy, and audit recorder. JPA management endpoints reuse authorization and audit; JPA reload execution relies on `JpaDomainReloadService` for idempotency.

## 5. Core Models

### `PluginRuntimeSnapshot`

```json
{
  "pluginId": "sample-workflow",
  "version": "1.2.3",
  "state": "STARTED",
  "pluginPath": "/app/plugins/sample-workflow.jar",
  "lastStartDurationMillis": -1,
  "dependencies": ["plugin-user-book-service"],
  "resourceCounts": {},
  "errorMessage": null,
  "errorDetail": null
}
```

### `PluginDeploymentRequest`

```json
{
  "pluginId": "sample-workflow",
  "stagedPluginPath": "sample-workflow-1.2.3.zip",
  "repositoryVersion": null,
  "repositoryVersionRange": null,
  "repositoryRollback": false,
  "dryRun": true
}
```

`pluginId` is required. If `repositoryVersion`, `repositoryVersionRange`, and `repositoryRollback=true` are all absent, `stagedPluginPath` is required and is resolved under `staging-root`.

### `DeploymentRecord`

```json
{
  "deploymentId": "deploy-001",
  "targetPluginId": "sample-workflow",
  "state": "PRECHECKED",
  "createdAt": 1718570000000,
  "updatedAt": 1718570000032,
  "message": "plan ok",
  "plan": {},
  "stateHistory": ["PLANNED", "PRECHECKED"],
  "durationMillis": 32,
  "errorCode": null
}
```

### `JpaDomainReloadRequest`

```json
{
  "domainId": "demo",
  "mode": "STOP_CONSUMERS_AND_REBUILD",
  "dryRun": false,
  "idempotencyKey": "jpa-reload-001",
  "requestedBy": "operator",
  "reason": "release",
  "allowInferredConsumers": false,
  "drainTimeoutMillis": 5000,
  "healthCheckTimeoutMillis": 5000,
  "providerReplacementPath": null
}
```

`mode` values are `DISABLED`, `PLAN_ONLY`, and `STOP_CONSUMERS_AND_REBUILD`. For `POST /jpa/domains/{domainId}/reload`, the controller fills `idempotencyKey` from `X-Idempotency-Key` when the body does not contain one.

## 6. Error Codes

| Code | Meaning | HTTP | Common scenario |
| --- | --- | --- | --- |
| `PFM-001` | `INVALID_REQUEST` | 400 | Missing parameter, invalid enum, missing idempotency key |
| `PFM-002` | `UNAUTHENTICATED` | 401 | Missing or invalid token, non-loopback local token request |
| `PFM-003` | `FORBIDDEN` | 403 | Permission denied, CSRF/origin rejection |
| `PFM-004` | `NOT_FOUND` | 404 | Plugin, deployment record, or JPA reload record not found |
| `PFM-005` | `CONFLICT` | 409 | Idempotency key reused with a different request |
| `PFM-006` | `RATE_LIMITED` | 429 | Write rate limit exceeded |
| `PFM-007` | `PRECHECK_FAILED` | 409/422 | Precheck failed, missing rollback snapshot, invalid confirm state |
| `PFM-008` | `OPERATION_FAILED` | 500 or response-body failure | Lifecycle, deployment, or rollback failure |
| `PFM-009` | `UNAVAILABLE` | 200 | JPA reload service is not available; response has `success=false` |

## 7. Request Examples

### List Plugins

```bash
curl -X GET 'http://localhost:8080/pf4boot/admin/plugins' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

### Start Plugin

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/plugins/sample-workflow/start' \
  -H 'X-Request-Id: req-start-001' \
  -H 'X-Idempotency-Key: plugin-start-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

### Plan Deployment

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/deployments/plan' \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req-plan-001' \
  -H 'X-Idempotency-Key: deploy-plan-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}' \
  -d '{"pluginId":"sample-workflow","stagedPluginPath":"sample-workflow-1.2.3.zip","dryRun":true}'
```

### Execute Deployment

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/deployments/replace' \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req-replace-001' \
  -H 'X-Idempotency-Key: deploy-replace-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}' \
  -d '{"pluginId":"sample-workflow","stagedPluginPath":"sample-workflow-1.2.3.zip","dryRun":false}'
```

### Plan JPA Reload

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/jpa/domains/demo/reload/plan' \
  -H 'Content-Type: application/json' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}' \
  -d '{"mode":"PLAN_ONLY","allowInferredConsumers":false}'
```

### Execute JPA Reload

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/jpa/domains/demo/reload' \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req-jpa-reload-001' \
  -H 'X-Idempotency-Key: jpa-reload-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}' \
  -d '{"mode":"STOP_CONSUMERS_AND_REBUILD","dryRun":false,"reason":"release"}'
```

## 8. CLI Notes

Frontend and CLI clients should check both HTTP status and response `success/code`. HTTP 200 can still represent a business failure, for example `PFM-009`.

For retryable writes, reuse the same `X-Idempotency-Key` only with the exact same logical request. If the body or target changes, create a new key.

Real deployment replacement requires either `dryRun=false` in the request or `dry-run-default=false` in configuration.
