# 插件 HTTP 管理接口语义加固设计

## 背景

上一轮实现已经补齐 HTTP 管理接口主体能力，但复查发现几个会影响生产语义的风险：

- `/deployments/replace` 计算了 `dryRun`，但实际始终执行真实替换。
- 幂等记录使用先查再写，两个并发同 key 请求可能同时进入真实操作。
- 认证、授权、CSRF、限流、参数校验等前置拒绝路径没有统一审计。
- 错误响应直接透出底层异常消息，可能包含绝对路径、token 或环境信息。
- `confirm` 复用 `replace` 权限，后续人工确认治理粒度不足。

## 影响模块

- `pf4boot-api`
  - 扩展 `PluginOperationStore`，提供原子幂等占位能力。
  - 为 `PluginManagementOperation.DEPLOYMENT_CONFIRM` 使用独立权限。
- `pf4boot-management-starter`
  - 修正部署 dry-run 语义。
  - 修正内存幂等实现的并发原子性。
  - 增加拒绝路径审计和安全响应消息。
  - 补充覆盖上述行为的单元测试。

## 方案

### dry-run 语义

`POST /deployments/replace` 在 `dryRun=true` 时只调用 `PluginDeploymentService.planReplacement(...)`，不修改运行态；只有显式 `dryRun=false` 才调用 `replace(...)`。`POST /deployments/plan` 始终视为 dry-run。

### 原子幂等

在 `PluginOperationStore` 增加 `saveIfIdempotencyKeyAbsent(PluginOperationRecord record)`。内存实现使用 `ConcurrentHashMap.putIfAbsent` 先占位，再保存 operation record，保证同一 `principal + operation + pluginId + idempotencyKey` 只有一个请求能成为执行者。

### 前置拒绝审计

Controller 在创建 `PluginManagementRequest` 后，对写操作使用统一 helper 包裹安全校验、认证、授权、幂等和参数校验等前置阶段。发生 `PluginManagementException` 时记录失败审计事件，并返回安全错误响应。

### 响应脱敏

对外响应不直接使用底层异常消息；错误码映射到稳定安全文案。内部消息仍可作为审计 message，但不包含 token，且尽量只记录动作、插件或部署 id。

### confirm 权限

新增权限点 `pf4boot:deployment:confirm`。本地 token 默认授予该权限；远程模式可以把直接替换和人工确认分配给不同主体。

## 兼容性

- `PluginOperationStore` 是管理接口新增 SPI；本仓库内存实现同步更新。
- 远程 authorizer 需要在使用 confirm 接口时授予 `pf4boot:deployment:confirm`，否则会返回 `403`。
- `replace` 默认 dry-run 行为会变得与配置一致，可能改变此前误执行真实替换的行为，这是修复而非兼容破坏。

## 验证计划

- `.\gradlew.bat :pf4boot-api:test`
- `.\gradlew.bat :pf4boot-management-starter:test`
- 补充测试：
  - replace 默认 dry-run 不调用 `replace`
  - replace 显式 `dryRun=false` 调用 `replace`
  - 同 idempotency key 并发只允许一个执行者
  - 前置拒绝路径记录审计
  - 异常处理器不透出 token 和绝对路径
  - confirm 使用独立权限
