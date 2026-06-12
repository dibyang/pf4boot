# 插件 HTTP 管理接口小模型执行手册

## 目标

- 在不引入歧义的前提下，补齐插件 HTTP 管理能力。
- 保持与现有 `Pf4bootPluginManager` / `PluginDeploymentService` 契约兼容。
- 第一阶段先把最小安全边界落地（鉴权、路径、防重复、审计），把高级治理项留到后续。

## 当前分支现状

- 已新增 `pf4boot-management-starter` 模块并接入主线 `settings.gradle`。
- Controller 和基础装配已到位。
- 本手册用于“下一轮收口/文档同步/补齐验收”。

## 阶段0：前置确认

1. 本轮只动这几类文件：
   - `pf4boot-api`
   - `pf4boot-management-starter`
   - `docs/design/*`
2. 不改非必要的打包/依赖范围。
3. 全部代码保持 Java 8 兼容。

## 阶段1：基线校验

先运行：

```powershell
.\gradlew.bat :pf4boot-management-starter:compileJava
.\gradlew.bat :pf4boot-management-starter:compileTestJava
```

能过就继续；不过先修编译问题再继续。

## 阶段2：文件级落地清单

### 2.1 模块与自动配置骨架

- `settings.gradle`
  - 确认包含：`include 'pf4boot-management-starter'`
- `pf4boot-management-starter/build.gradle`
  - 依赖包含 `pf4boot-api`、`pf4boot-core`、`pf4boot-web-support`
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/Pf4bootManagementAutoConfiguration.java`
  - Controller/异常处理器仅在 `spring.pf4boot.management.http.enabled=true` 时注册。

### 2.2 管理 API 契约（`pf4boot-api`）

确认存在：

- `PluginAdminResponse.java`
- `PluginManagementMode.java`
- `PluginManagementOperation.java`
- `PluginManagementErrorCode.java`
- `PluginManagementRequest.java`
- `PluginManagementPrincipal.java`
- `PluginManagementAuthorizer.java`
- `PluginManagementAuditEvent.java`
- `PluginOperationRecord.java`
- `PluginOperationStore.java`

检查点：

- 操作枚举要映射到权限（`pf4boot:plugin:*`、`pf4boot:deployment:*`）。
- 错误码齐备（至少 9 个码位）。
- 统一响应默认 `warnings` 不为空列表。

### 2.3 配置与启动校验

- `Pf4bootManagementProperties.java`
  - 前缀：`spring.pf4boot.management.http`
  - 默认 `enabled=false`、`mode=DISABLED`、`allowLoopbackOnly=true`
- `PluginManagementStartupValidator.java`
  - `enabled=true` + `mode=DISABLED` => 启动失败；
  - `LOCAL_TOKEN` 缺 token 且未注入本地 token authorizer => 启动失败；
  - `REMOTE_DELEGATED` 无可用 `PluginManagementAuthorizer` => 启动失败。

### 2.4 请求上下文与工厂

- `PluginManagementRequestContext.java`
- `PluginManagementRequestFactory.java`

检查点：

- 每次请求生成 `requestId`。
- 记录 remoteAddress、path、method、idempotencyKey、token/header。
- 写操作按配置要求校验幂等键。

### 2.5 安全基线

- `LocalTokenPluginManagementAuthorizer.java`
  - 固定时长比较 token
  - `allowLoopbackOnly` 时要求回环地址
  - 有效返回 principal 与权限集合
- `DelegatingPluginManagementAuthorizer.java`
  - 远端模式透传注入的 SPI 鉴权器

### 2.6 Controller 流程

- `PluginManagementController.java`
  - 只读：
    - `GET /pf4boot/admin/plugins`
    - `GET /pf4boot/admin/plugins/{pluginId}`
    - `GET /pf4boot/admin/deployments/{deploymentId}`
    - `GET /pf4boot/admin/deployments`
  - 生命周期：
    - `POST /plugins/{pluginId}/start|stop|restart|reload|enable`
    - `DELETE /plugins/{pluginId}/enable`
  - 部署：
    - `POST /deployments/plan`
    - `POST /deployments/replace`
    - `POST /deployments/{deploymentId}/rollback`

要求：

- 变更接口必须走已有 manager/deployment service，不可重复绕过生命周期/替换编排。
- `reload` 仅保留低层兜底语义，不等同于安全替换。

### 2.7 幂等与审计

- `PluginManagementPathValidator.java`
  - 拒绝越权路径和 `..`、绝对路径逃逸。
- `PluginManagementIdempotencyService.java`
  - 同 key + 同参数 hash 复用结果。
- `InMemoryPluginOperationStore.java` / `InMemoryPluginDeploymentRecordStore.java`
  - 阶段一内存实现。
- `PluginManagementAuditRecorder` / `LoggingPluginManagementAuditRecorder`
  - 记录成功、失败、拒绝。
- `PluginManagementExceptionHandler.java`
  - 禁止返回 stack trace 和敏感明文路径。

## 阶段3：验收同步

执行：

```powershell
.\gradlew.bat :pf4boot-management-starter:compileJava
.\gradlew.bat :pf4boot-management-starter:compileTestJava
```

再人工确认这些“代码真值”：

- `reload` 仅是低层接口，不能作为 `PluginDeploymentService.replace(...)` 替代。
- `plan` 接口不可执行真实替换动作。
- `deployments/{id}/rollback` 有对应记录路径。
- 本阶段不实现 `rate-limit`、`csrf/origin` 强制策略；明确标注为后续。

## 阶段4：收口

更新并同步：

- `plugin-http-management-api-plan.md`
- `plugin-http-management-api-acceptance.md`

若功能已落地但未做端到端验证，请把状态写成 `In Progress`（而不是直接 `Not Done`）。
