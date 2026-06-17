# 插件管理 HTTP 接口文档（前端/CLI 对接版）

## 1. 文档范围

本文档定义 `pf4boot-management-starter` 对外暴露的插件管理 HTTP 接口，面向管理控制台、运维平台、自动化脚本和 CLI 开发。

- 默认 Base Path：`/pf4boot/admin`
- 统一响应体：`PluginAdminResponse<T>`
- 自动配置条件：`spring.pf4boot.management.http.enabled=true`
- 默认关闭：`enabled=false` 且 `mode=DISABLED`
- 所有接口均需要通过 `PluginManagementAuthorizer` 鉴权，包括查询接口
- 时间字段均为 Unix epoch milliseconds
- 本合同只覆盖基础插件管理与部署接口，不包含 JPA reload。JPA reload 仅在额外引入 `pf4boot-jpa-management-starter` 时可用。

> 启用 HTTP 管理接口时，`mode` 不能继续保持 `DISABLED`，否则启动校验会失败。

## 2. 配置

### 2.1 默认配置

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

### 2.2 本地 Token 模式示例

```yaml
spring:
  pf4boot:
    management:
      http:
        enabled: true
        mode: LOCAL_TOKEN
        token: ${PF4BOOT_ADMIN_TOKEN}
        allow-loopback-only: true
```

`LOCAL_TOKEN` 模式默认使用 `LocalTokenPluginManagementAuthorizer`，要求：

- `token` 非空，或者应用提供自定义 `PluginManagementAuthorizer`
- 请求头携带 `token-header` 指定的 token，默认 `X-PF4Boot-Admin-Token`
- `allow-loopback-only=true` 时，请求来源必须是本机回环地址

### 2.3 远程委托鉴权模式示例

```yaml
spring:
  pf4boot:
    management:
      http:
        enabled: true
        mode: REMOTE_DELEGATED
        csrf:
          enabled: auto
```

`REMOTE_DELEGATED` 模式要求应用至少提供一个自定义 `PluginManagementAuthorizer` Bean，由业务系统负责身份认证和权限判断。

## 3. 通用协议

### 3.1 通用请求头

| 请求头 | 是否必需 | 说明 |
| --- | --- | --- |
| `Content-Type: application/json` | 有 body 时必需 | `POST /deployments/*` 请求使用 JSON body |
| `X-PF4Boot-Admin-Token` | `LOCAL_TOKEN` 默认必需 | 实际名称由 `token-header` 配置决定 |
| `X-Request-Id` | 可选 | 不传时服务端生成 `req-{uuid}` |
| `X-Idempotency-Key` | 管理写接口默认必需 | 实际名称由 `idempotency-header` 配置决定；`require-idempotency-key=false` 时可不传 |
| `Origin` | 浏览器写请求建议携带 | `csrf.enabled=true` 时必需且必须同源；`auto` 时仅在请求带 Origin 时校验 |

### 3.2 统一响应体

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

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 业务是否成功 |
| `requestId` | string/null | 请求追踪 ID；全局异常处理路径可能为 `null` |
| `operationId` | string/null | 管理操作 ID；全局异常处理路径可能为 `null` |
| `code` | string | 成功为 `OK`，失败为 `PFM-xxx` |
| `message` | string | 可展示的简短消息，服务端会脱敏 |
| `data` | object/array/null | 具体接口数据 |
| `warnings` | array | 警告信息，未产生警告时为空数组 |

### 3.3 鉴权权限

| 操作枚举 | 权限字符串 | 典型接口 |
| --- | --- | --- |
| `PLUGIN_READ` | `pf4boot:plugin:read` | 查询插件 |
| `PLUGIN_START`/`PLUGIN_STOP`/`PLUGIN_RESTART`/`PLUGIN_ENABLE`/`PLUGIN_DISABLE` | `pf4boot:plugin:lifecycle` | 插件生命周期接口 |
| `PLUGIN_RELOAD` | `pf4boot:plugin:reload` | 插件 reload |
| `DEPLOYMENT_QUERY` | `pf4boot:deployment:query` | 查询部署记录 |
| `DEPLOYMENT_PLAN` | `pf4boot:deployment:plan` | 部署计划 |
| `DEPLOYMENT_REPLACE` | `pf4boot:deployment:replace` | 部署替换 |
| `DEPLOYMENT_CONFIRM` | `pf4boot:deployment:confirm` | 确认部署 |
| `DEPLOYMENT_ROLLBACK` | `pf4boot:deployment:rollback` | 回滚部署 |

`LOCAL_TOKEN` 默认 principal 拥有上述权限和 `pf4boot:admin:all`。

### 3.4 幂等规则

插件生命周期接口和部署写接口会进入管理幂等服务：

- 缺少 `X-Idempotency-Key`：返回 `400` + `PFM-001`
- 同一 `principal + operation + pluginId + idempotencyKey` 下请求 hash 一致：返回第一次操作的结果摘要
- 同一 key 被不同请求复用：返回 `409` + `PFM-005`

### 3.5 写请求安全策略

插件生命周期接口和部署写接口会执行：

- 固定窗口限流：按 `remoteAddr` 统计，默认 30 次/分钟
- CSRF/Origin 校验：由 `csrf.enabled` 控制
- 审计记录：成功和失败均记录

## 4. 接口一览

| 方法 | 路径 | 权限 | 幂等头 | 说明 |
| --- | --- | --- | --- | --- |
| `GET` | `/plugins` | `pf4boot:plugin:read` | 否 | 查询插件列表 |
| `GET` | `/plugins/{pluginId}` | `pf4boot:plugin:read` | 否 | 查询单插件 |
| `POST` | `/plugins/{pluginId}/start` | `pf4boot:plugin:lifecycle` | 是 | 启动插件 |
| `POST` | `/plugins/{pluginId}/stop` | `pf4boot:plugin:lifecycle` | 是 | 停止插件 |
| `POST` | `/plugins/{pluginId}/restart` | `pf4boot:plugin:lifecycle` | 是 | 重启插件 |
| `POST` | `/plugins/{pluginId}/reload` | `pf4boot:plugin:reload` | 是 | 低层 reload 插件 |
| `POST` | `/plugins/{pluginId}/enable` | `pf4boot:plugin:lifecycle` | 是 | 启用插件 |
| `DELETE` | `/plugins/{pluginId}/enable` | `pf4boot:plugin:lifecycle` | 是 | 禁用插件 |
| `GET` | `/deployments` | `pf4boot:deployment:query` | 否 | 查询最近部署记录 |
| `GET` | `/deployments/{deploymentId}` | `pf4boot:deployment:query` | 否 | 查询单条部署记录 |
| `POST` | `/deployments/plan` | `pf4boot:deployment:plan` | 是 | 生成部署计划 |
| `POST` | `/deployments/replace` | `pf4boot:deployment:replace` | 是 | 执行或 dry-run 替换 |
| `POST` | `/deployments/{deploymentId}/confirm` | `pf4boot:deployment:confirm` | 是 | 执行已预检计划 |
| `POST` | `/deployments/{deploymentId}/rollback` | `pf4boot:deployment:rollback` | 是 | 按部署计划回滚 |

## 5. 数据模型

### 5.1 `PluginRuntimeSnapshot`

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

### 5.2 `PluginDeploymentRequest`

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

请求规则：

- `pluginId` 必填
- 当 `repositoryVersion`、`repositoryVersionRange`、`repositoryRollback=true` 都未提供时，必须提供 `stagedPluginPath`
- `stagedPluginPath` 会按 `staging-root` 解析，不允许越界到 staging 目录外
- `/deployments/plan` 默认 dry-run
- `/deployments/replace` 未传 `dryRun` 时使用 `dry-run-default`
- 仓库来源请求使用 `repositoryVersion`、`repositoryVersionRange` 或 `repositoryRollback=true`，此时不要求 `stagedPluginPath`

### 5.3 `DeploymentRecord`

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

部署状态来自 `DeploymentState`，常见值包括：

- `PLANNED`
- `PRECHECKED`
- `APPLYING`
- `SUCCEEDED`
- `FAILED`
- `ROLLING_BACK`
- `ROLLED_BACK`
- `MANUAL_INTERVENTION`

### 5.4 `DeploymentPlan`

```json
{
  "deploymentId": "deploy-001",
  "targetPluginId": "sample-workflow",
  "stagedPluginPath": "/app/plugins/staged/sample-workflow-1.2.3.zip",
  "currentPluginPath": "/app/plugins/sample-workflow-1.1.0.zip",
  "currentVersion": "1.1.0",
  "currentState": "STOPPED",
  "stagedVersion": "1.2.3",
  "stagedRequires": null,
  "affectedPluginIds": ["sample-workflow"],
  "stopOrder": ["sample-workflow"],
  "startOrder": ["sample-workflow"],
  "checkResults": [],
  "rollbackSnapshot": null,
  "executable": true
}
```

`executable` 是 Java getter `isExecutable()` 的 JSON 属性，表示 `checkResults` 中没有 error 级别结果。

## 6. 错误码

| code | 含义 | HTTP | 常见场景 |
| --- | --- | --- | --- |
| `PFM-001` | `INVALID_REQUEST` | 400 | 参数缺失、非法枚举、缺少幂等键 |
| `PFM-002` | `UNAUTHENTICATED` | 401 | token 缺失/无效、非 loopback 请求 |
| `PFM-003` | `FORBIDDEN` | 403 | 权限不足、CSRF/Origin 校验失败 |
| `PFM-004` | `NOT_FOUND` | 404 | 插件或部署记录不存在 |
| `PFM-005` | `CONFLICT` | 409 | 同一幂等 key 被不同请求复用 |
| `PFM-006` | `RATE_LIMITED` | 429 | 写请求超过限流阈值 |
| `PFM-007` | `PRECHECK_FAILED` | 409/422 | 部署预检失败、回滚快照缺失、确认状态不正确 |
| `PFM-008` | `OPERATION_FAILED` | 500 或响应体失败 | 生命周期、部署或回滚执行失败 |
| `PFM-009` | `UNAVAILABLE` | 200 | JPA reload 执行服务未注入；响应体 `success=false` |

异常处理器会返回统一响应体。由异常处理器生成的失败响应通常没有 `requestId` 和 `operationId`。

失败响应示例：

```json
{
  "success": false,
  "requestId": null,
  "operationId": null,
  "code": "PFM-001",
  "message": "Invalid management request",
  "data": null,
  "warnings": []
}
```

## 7. 插件查询接口

### 7.1 查询插件列表

`GET /plugins`

**Request**

```bash
curl -X GET 'http://localhost:8080/pf4boot/admin/plugins' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

**Response**

```json
{
  "success": true,
  "requestId": "req-list-001",
  "operationId": "op-list-001",
  "code": "OK",
  "message": "plugin list fetched",
  "data": [
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
  ],
  "warnings": []
}
```

### 7.2 查询单插件

`GET /plugins/{pluginId}`

**Request**

```bash
curl -X GET 'http://localhost:8080/pf4boot/admin/plugins/sample-workflow' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

**Response**

```json
{
  "success": true,
  "requestId": "req-plugin-001",
  "operationId": "op-plugin-001",
  "code": "OK",
  "message": "plugin found",
  "data": {
    "pluginId": "sample-workflow",
    "version": "1.2.3",
    "state": "STARTED",
    "pluginPath": "/app/plugins/sample-workflow.jar",
    "lastStartDurationMillis": -1,
    "dependencies": [],
    "resourceCounts": {},
    "errorMessage": null,
    "errorDetail": null
  },
  "warnings": []
}
```

## 8. 插件生命周期接口

以下接口均无请求体，默认需要 `X-Idempotency-Key`。

### 8.1 启动插件

`POST /plugins/{pluginId}/start`

**Request**

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/plugins/sample-workflow/start' \
  -H 'X-Request-Id: req-start-001' \
  -H 'X-Idempotency-Key: plugin-start-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

**Response**

```json
{
  "success": true,
  "requestId": "req-start-001",
  "operationId": "op-start-001",
  "code": "OK",
  "message": "plugin started",
  "data": {
    "pluginId": "sample-workflow",
    "version": "1.2.3",
    "state": "STARTED",
    "pluginPath": "/app/plugins/sample-workflow.jar",
    "lastStartDurationMillis": -1,
    "dependencies": [],
    "resourceCounts": {},
    "errorMessage": null,
    "errorDetail": null
  },
  "warnings": []
}
```

### 8.2 其他生命周期操作

只需替换路径末尾动作：

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/plugins/sample-workflow/stop' \
  -H 'X-Request-Id: req-stop-001' \
  -H 'X-Idempotency-Key: plugin-stop-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

| 动作 | 方法和路径 | 成功 message |
| --- | --- | --- |
| 停止 | `POST /plugins/{pluginId}/stop` | `plugin stopped` |
| 重启 | `POST /plugins/{pluginId}/restart` | `plugin restarted` |
| Reload | `POST /plugins/{pluginId}/reload` | `plugin reloaded` |
| 启用 | `POST /plugins/{pluginId}/enable` | `plugin enabled` |
| 禁用 | `DELETE /plugins/{pluginId}/enable` | `plugin disabled` |

## 9. 部署管理接口

### 9.1 查询最近部署记录

`GET /deployments`

**Request**

```bash
curl -X GET 'http://localhost:8080/pf4boot/admin/deployments' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

**Response**

```json
{
  "success": true,
  "requestId": "req-deployments-001",
  "operationId": "op-deployments-001",
  "code": "OK",
  "message": "deployment records fetched",
  "data": [],
  "warnings": []
}
```

### 9.2 查询单条部署记录

`GET /deployments/{deploymentId}`

**Request**

```bash
curl -X GET 'http://localhost:8080/pf4boot/admin/deployments/deploy-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

**Response**

```json
{
  "success": true,
  "requestId": "req-deployment-001",
  "operationId": "op-deployment-001",
  "code": "OK",
  "message": "deployment record found",
  "data": {
    "deploymentId": "deploy-001",
    "targetPluginId": "sample-workflow",
    "state": "PRECHECKED",
    "createdAt": 1718570000000,
    "updatedAt": 1718570000032,
    "message": "plan ok",
    "plan": null,
    "stateHistory": ["PLANNED", "PRECHECKED"],
    "durationMillis": 32,
    "errorCode": null
  },
  "warnings": []
}
```

### 9.3 生成部署计划

`POST /deployments/plan`

**Request**

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/deployments/plan' \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req-plan-001' \
  -H 'X-Idempotency-Key: deploy-plan-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}' \
  -d '{"pluginId":"sample-workflow","stagedPluginPath":"sample-workflow-1.2.3.zip","dryRun":true}'
```

**Response**

```json
{
  "success": true,
  "requestId": "req-plan-001",
  "operationId": "op-plan-001",
  "code": "OK",
  "message": "plan ok",
  "data": {
    "deploymentId": "deploy-001",
    "targetPluginId": "sample-workflow",
    "state": "PRECHECKED",
    "createdAt": 1718570000000,
    "updatedAt": 1718570000032,
    "message": "plan ok",
    "plan": {
      "deploymentId": "deploy-001",
      "targetPluginId": "sample-workflow",
      "stagedPluginPath": "/app/plugins/staged/sample-workflow-1.2.3.zip",
      "currentPluginPath": "/app/plugins/sample-workflow-1.1.0.zip",
      "currentVersion": "1.1.0",
      "currentState": "STOPPED",
      "stagedVersion": "1.2.3",
      "stagedRequires": null,
      "affectedPluginIds": ["sample-workflow"],
      "stopOrder": ["sample-workflow"],
      "startOrder": ["sample-workflow"],
      "checkResults": [],
      "rollbackSnapshot": null,
      "executable": true
    },
    "stateHistory": ["PLANNED", "PRECHECKED"],
    "durationMillis": 32,
    "errorCode": null
  },
  "warnings": []
}
```

### 9.4 执行部署替换

`POST /deployments/replace`

**Request**

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/deployments/replace' \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req-replace-001' \
  -H 'X-Idempotency-Key: deploy-replace-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}' \
  -d '{"pluginId":"sample-workflow","stagedPluginPath":"sample-workflow-1.2.3.zip","dryRun":false}'
```

**Response**

```json
{
  "success": true,
  "requestId": "req-replace-001",
  "operationId": "op-replace-001",
  "code": "OK",
  "message": "deployment succeeded",
  "data": {
    "deploymentId": "deploy-002",
    "targetPluginId": "sample-workflow",
    "state": "SUCCEEDED",
    "createdAt": 1718570100000,
    "updatedAt": 1718570101800,
    "message": "deployment succeeded",
    "plan": null,
    "stateHistory": ["PLANNED", "PRECHECKED", "APPLYING", "SUCCEEDED"],
    "durationMillis": 1800,
    "errorCode": null
  },
  "warnings": []
}
```

### 9.5 仓库来源部署请求

当插件包来源于仓库解析时，可以不传 `stagedPluginPath`。

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/deployments/plan' \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req-repo-plan-001' \
  -H 'X-Idempotency-Key: deploy-repo-plan-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}' \
  -d '{"pluginId":"sample-workflow","repositoryVersion":"1.2.3","dryRun":true}'
```

也可以使用版本范围或回滚：

```json
{
  "pluginId": "sample-workflow",
  "repositoryVersionRange": "[1.2.0,2.0.0)",
  "repositoryRollback": false,
  "dryRun": true
}
```

### 9.6 确认预检计划

`POST /deployments/{deploymentId}/confirm`

要求源部署记录状态为 `PRECHECKED`，且记录中存在可执行 `plan`。

**Request**

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/deployments/deploy-001/confirm' \
  -H 'X-Request-Id: req-confirm-001' \
  -H 'X-Idempotency-Key: deploy-confirm-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

**Response**

```json
{
  "success": true,
  "requestId": "req-confirm-001",
  "operationId": "op-confirm-001",
  "code": "OK",
  "message": "deployment succeeded",
  "data": {
    "deploymentId": "deploy-003",
    "targetPluginId": "sample-workflow",
    "state": "SUCCEEDED",
    "createdAt": 1718570300000,
    "updatedAt": 1718570301800,
    "message": "deployment succeeded",
    "plan": null,
    "stateHistory": ["PLANNED", "PRECHECKED", "APPLYING", "SUCCEEDED"],
    "durationMillis": 1800,
    "errorCode": null
  },
  "warnings": []
}
```

### 9.7 回滚部署

`POST /deployments/{deploymentId}/rollback`

要求源部署记录的 `plan.rollbackSnapshot` 可用。

**Request**

```bash
curl -X POST 'http://localhost:8080/pf4boot/admin/deployments/deploy-002/rollback' \
  -H 'X-Request-Id: req-rollback-001' \
  -H 'X-Idempotency-Key: deploy-rollback-001' \
  -H 'X-PF4Boot-Admin-Token: ${TOKEN}'
```

**Response**

```json
{
  "success": true,
  "requestId": "req-rollback-001",
  "operationId": "op-rollback-001",
  "code": "OK",
  "message": "rollback succeeded",
  "data": {
    "deploymentId": "deploy-002",
    "targetPluginId": "sample-workflow",
    "state": "SUCCEEDED",
    "createdAt": 1718570400000,
    "updatedAt": 1718570400600,
    "message": "rollback succeeded",
    "plan": null,
    "stateHistory": ["PLANNED", "ROLLING_BACK", "SUCCEEDED"],
    "durationMillis": 600,
    "errorCode": null
  },
  "warnings": []
}
```

## 10. CLI 开发建议

### 10.1 Bash

```bash
BASE='http://localhost:8080/pf4boot/admin'
TOKEN="${PF4BOOT_ADMIN_TOKEN}"

curl -sS -X POST "$BASE/deployments/plan" \
  -H 'Content-Type: application/json' \
  -H "X-PF4Boot-Admin-Token: $TOKEN" \
  -H 'X-Request-Id: cli-plan-001' \
  -H 'X-Idempotency-Key: cli-plan-001' \
  -d '{"pluginId":"sample-workflow","stagedPluginPath":"sample-workflow.zip","dryRun":true}'
```

### 10.2 Python

```python
import requests

base = "http://localhost:8080/pf4boot/admin"
headers = {
    "X-PF4Boot-Admin-Token": "***",
    "X-Request-Id": "cli-deploy-001",
    "X-Idempotency-Key": "cli-deploy-001",
    "Content-Type": "application/json",
}

resp = requests.post(
    base + "/deployments/plan",
    headers=headers,
    json={
        "pluginId": "sample-workflow",
        "stagedPluginPath": "sample-workflow.zip",
        "dryRun": True,
    },
    timeout=10,
)

payload = resp.json()
if resp.status_code >= 400 or not payload.get("success"):
    raise RuntimeError("%s %s" % (payload.get("code"), payload.get("message")))
print(payload["data"])
```

## 11. 对接注意事项

- 前端应以 `success` 和 `code` 判断业务结果，不应只看 HTTP 2xx。
- CLI 对写接口应固定生成可重放的 `X-Idempotency-Key`，重试时保持请求体一致。
- 浏览器端写请求建议携带 `Origin`，并确保与服务端看到的 `scheme + host + port` 同源。
- 部署替换默认可能是 dry-run，真实执行必须明确传 `dryRun=false` 或调整 `dry-run-default=false`。
- `stagedPluginPath` 是相对 `staging-root` 的路径更稳妥；绝对路径也会被解析并校验不能越界。
- JPA reload 的 `reason` 最长 512 字符。
- 示例中的路径、ID 和时间为演示值；实际返回以运行时状态和部署服务实现为准。
