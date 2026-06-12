# 插件 HTTP 管理接口实施规划

## 追踪原则

- 设计文档：[plugin-http-management-api.md](plugin-http-management-api.md)。
- 实施指南：[plugin-http-management-api-implementation-guide.md](plugin-http-management-api-implementation-guide.md)。
- 本文只追踪实施任务，不替代设计决策。
- 状态取值：`未开始`、`进行中`、`已完成`、`阻塞`。
- 每个里程碑完成时同步更新验收清单：[plugin-http-management-api-acceptance.md](plugin-http-management-api-acceptance.md)。

## 里程碑

| 里程碑 | 状态 | 目标 | 验证 |
| --- | --- | --- | --- |
| M1 模块与配置骨架 | 未开始 | 新增 `pf4boot-management-starter`，默认关闭 | compileJava |
| M2 公共模型和错误码 | 未开始 | DTO、操作类型、错误码、SPI 落入 `pf4boot-api` | api compile/test |
| M3 本地 token 模式 | 未开始 | loopback、token、配置启动校验和审计 | 管理 starter test |
| M4 只读与生命周期接口 | 未开始 | 插件列表、详情、start/stop/restart/enable/disable | HTTP 集成测试 |
| M5 部署接口 | 未开始 | plan/replace/rollback 查询和 `PluginDeploymentService` 对接 | core + HTTP 测试 |
| M6 远程授权模式 | 未开始 | authorizer SPI、权限点、CSRF/来源约束、限流 | 授权失败/成功测试 |
| M7 幂等与审计记录 | 未开始 | 幂等 key、重复请求复用结果、审计事件 | 幂等冲突测试 |
| M8 sample 与文档 | 未开始 | 本地 token 和远程授权示例，迁移说明 | sample smoke |

## M1 模块与配置骨架

任务：

- 创建 `pf4boot-management-starter`，并在 `settings.gradle` 中 include。
- 新增 `Pf4bootManagementProperties`，覆盖 `enabled`、`basePath`、`mode`、`tokenHeader`、`idempotencyHeader`、限流、CSRF、审计配置。
- 自动配置默认关闭；`enabled=false` 时不注册 Controller、filter/interceptor 或 authorizer。
- 启动校验：
  - `enabled=true` 且 `mode=DISABLED` 失败；
  - `LOCAL_TOKEN` 未配置 token 失败；
  - `REMOTE_DELEGATED` 缺少 authorizer 失败。

建议验证：

```powershell
.\gradlew.bat :pf4boot-api:compileJava
.\gradlew.bat :pf4boot-management-starter:compileJava
```

## M2 公共模型和错误码

任务：

- 在 `pf4boot-api` 新增：
  - `PluginAdminResponse<T>`
  - `PluginManagementOperation`
  - `PluginManagementRequest`
  - `PluginManagementPrincipal`
  - `PluginManagementAuthorizer`
  - `PluginManagementAuditEvent`
  - `PluginManagementErrorCode`
- 明确 DTO 字段不泄漏内部对象和敏感路径。
- 保持 Java 8，不使用 record、var 或 Java 9+ API。

建议验证：

```powershell
.\gradlew.bat :pf4boot-api:test
```

## M3 本地 token 模式

任务：

- 实现默认 `LocalTokenPluginManagementAuthorizer`。
- 实现 loopback 检查，支持 IPv4/IPv6 loopback。
- token 使用固定时间比较，避免简单时序泄漏。
- 本地模式默认要求 token；允许测试环境通过显式配置关闭 token 的选项暂不提供。
- 所有拒绝请求输出统一错误响应和审计记录。

验收重点：

- 无 token 返回 `401`。
- 非 loopback 返回 `403`。
- token 正确但权限不足的路径不能发生。

## M4 只读与生命周期接口

任务：

- 实现插件列表和详情接口，复用 `PluginRuntimeInspector` 或 `Pf4bootPluginManager`。
- 实现 start/stop/restart/enable/disable。
- `reload` 作为低层运维兜底接口，单独权限 `pf4boot:plugin:reload`，文档标注不等同安全热替换。
- 所有写操作必须进入生命周期锁或底层 manager 已有锁，不在 Controller 中自行拆散依赖链。
- 返回统一响应模型。

验收重点：

- GET 不改变运行态。
- POST/DELETE 方法语义正确。
- 重复 start/stop 保持幂等。

## M5 部署接口

任务：

- 实现 `POST /deployments/plan`，只调用 `PluginDeploymentService.planReplacement`，不改变运行态。
- 实现 `POST /deployments/replace`，调用 `PluginDeploymentService.replace`。
- 实现部署记录查询和回滚入口；若底层尚未支持持久回滚，HTTP 层返回明确 `422/501` 风格错误，不假装成功。
- staged 包路径限制在配置的 staging 根目录内。

验收重点：

- 预检失败不改变插件状态。
- replace 失败能返回 deployment record。
- 任意路径穿越被拒绝。

## M6 远程授权模式

任务：

- 接入 `PluginManagementAuthorizer` SPI。
- 定义权限点：
  - `pf4boot:plugin:read`
  - `pf4boot:plugin:lifecycle`
  - `pf4boot:plugin:reload`
  - `pf4boot:deployment:plan`
  - `pf4boot:deployment:replace`
  - `pf4boot:deployment:rollback`
  - `pf4boot:admin:all`
- 增加 CSRF/来源约束策略。浏览器场景启用 CSRF；非浏览器 CLI 可以通过 bearer/token + origin policy。
- 增加写操作限流，默认 per principal 和 per remote address。

验收重点：

- 未认证 `401`。
- 已认证但无权限 `403`。
- 缺少 CSRF/来源不可信时拒绝浏览器写操作。

## M7 幂等与审计记录

任务：

- 实现 `X-Idempotency-Key` 解析。
- 内存记录 key、request hash、状态和响应摘要。
- 相同 key + 相同请求返回原响应。
- 相同 key + 不同请求返回 `409`。
- 审计事件覆盖成功、失败、拒绝、限流、幂等命中。

验收重点：

- 幂等记录不会重复触发生命周期动作。
- 审计不记录 token、密码、完整堆栈。
- 进程重启后内存幂等记录丢失的行为在文档中明确。

## M8 sample 与文档

任务：

- 在 `samples/cross-plugin-jpa` 或新增管理 sample 中增加本地 token 模式配置。
- 增加远程 authorizer fake 示例。
- README 给出 curl 示例：
  - 查询插件；
  - start/stop；
  - plan replacement；
  - replace；
  - 查询部署记录。
- 更新中文和英文设计索引。

建议验证：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

## 实施顺序建议

1. 先落 M1-M3，确保默认不暴露和本地最小保护可靠。
2. 再落 M4，使现有低层管理能力有标准 HTTP 入口。
3. 再落 M5，把发布级热替换接入管理面。
4. 最后落 M6-M8，补齐远程安全、幂等、审计和示例。

## 阻塞与决策

| 问题 | 建议 | 状态 |
| --- | --- | --- |
| 是否新增独立模块 | 新增 `pf4boot-management-starter` | 已决策 |
| 远程模式是否强依赖 Spring Security | 不强依赖，只提供 SPI 和可选 adapter | 已决策 |
| 幂等和审计是否首期持久化 | 首期内存，后续 SPI 持久化 | 已决策 |
