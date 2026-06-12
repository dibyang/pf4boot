# 插件 HTTP 管理接口验收清单

## 验收范围

本文用于追踪 [plugin-http-management-api.md](plugin-http-management-api.md) 和 [plugin-http-management-api-plan.md](plugin-http-management-api-plan.md) 的落地结果。
实施时同时参考 [plugin-http-management-api-implementation-guide.md](plugin-http-management-api-implementation-guide.md)，验收证据中的类名、测试名和模块路径应尽量与实施指南保持一致。

## 功能验收

| 编号 | 验收项 | 状态 | 证据 |
| --- | --- | --- | --- |
| AC-01 | 默认不注册 HTTP 写管理接口 | 已完成 | `Pf4bootManagementProperties.enabled` 与 `Pf4bootManagementAutoConfiguration` 条件 |
| AC-02 | `enabled=true` 但安全模式非法时启动失败 | 已完成 | `PluginManagementStartupValidator.validate()` |
| AC-03 | 本地 token 模式要求 loopback + token | 已完成 | `LocalTokenPluginManagementAuthorizer.authenticate()` |
| AC-04 | 远程模式缺少 authorizer 时启动失败 | 已完成 | `PluginManagementStartupValidator.hasCustomAuthorizer()` |
| AC-05 | 插件列表和详情接口只读，不改变运行态 | 已完成 | `PluginManagementController.plugins`、`PluginManagementController.plugin` |
| AC-06 | start/stop/restart/enable/disable 使用语义化 HTTP 方法 | 已完成 | 控制器签名中为 `@PostMapping`/`@DeleteMapping` |
| AC-07 | reload 被标记为低层运维兜底，不作为热替换通路 | 已完成 | `reload` 与 `replace` 的分离；`replace` 使用 `PluginDeploymentService.replace` |
| AC-08 | 部署预检不改变运行态 | 已完成 | `PluginManagementController.plan` 仅走 `PluginDeploymentService.planReplacement` |
| AC-09 | 热替换接口调用 `PluginDeploymentService.replace` | 已完成 | `PluginManagementController.replace` |
| AC-10 | staged 包路径不能逃逸配置根目录 | 已完成 | `PluginManagementPathValidator.resolveStagedPath(...)` |
| AC-11 | 写操作支持幂等 key，相同请求不重复执行 | 已完成 | `PluginManagementIdempotencyService.begin(...)` |
| AC-12 | 相同幂等 key 但请求体不同返回冲突 | 已完成 | `PluginManagementIdempotencyService.begin` 冲突分支 |
| AC-13 | 审计记录覆盖成功、失败和拒绝请求 | 已完成 | `PluginManagementController`、`PluginManagementAuditRecorder` 与日志实现 |
| AC-14 | 错误响应不泄漏 token、敏感路径和完整堆栈 | 已完成 | `PluginManagementExceptionHandler` |
| AC-15 | `pf4boot-actuator` 仍然只读 | 已完成 | 见 `plugin-http-management-api.md` 边界说明及无 actuator 变更端点 |

## 安全验收

| 编号 | 验收项 | 状态 | 证据 |
| --- | --- | --- | --- |
| SEC-01 | 无 token 的本地写请求返回 `401` | 已完成 | `LocalTokenPluginManagementAuthorizer.isSameToken` + 错误码映射 |
| SEC-02 | 非 loopback 的本地模式请求返回 `401`/`403` | 已完成 | `LocalTokenPluginManagementAuthorizer.authenticate` 的 loopback 分支 |
| SEC-03 | 远程未认证请求返回 `401` | 已完成 | `PluginManagementControllerSecurityTest.remoteUnauthenticatedDelegatedRequestRejectedWith401` |
| SEC-04 | 远程权限不足请求返回 `403` | 已完成 | `PluginManagementControllerSecurityTest.remoteUnauthorizedDelegatedRequestRejectedWith403` |
| SEC-05 | 浏览器写操作缺少 CSRF/来源约束时被拒绝 | 已完成 | `PluginManagementWriteSecurityPolicyTest`、`PluginManagementControllerSecurityTest.csrfEnabledRequiresOriginForWriteRequests` |
| SEC-06 | 写操作限流生效并返回 `429` | 已完成 | `PluginManagementRateLimiterTest`、`PluginManagementControllerSecurityTest.rateLimitAppliedBeforeSecondWrite` |
| SEC-07 | 启动时检测到公网绑定且无远程授权时失败 | 已完成 | `PluginManagementStartupValidator` |

## 兼容性验收

| 编号 | 验收项 | 状态 | 证据 |
| --- | --- | --- | --- |
| COMP-01 | 未引入管理 starter 的应用行为不变 | 已完成 | `pf4boot-starter/src/test/java/net/xdob/pf4boot/Pf4bootStarterCompatibilityTest.java` 验证管理自动配置类不存在 |
| COMP-02 | 现有 `Pf4bootPluginManager` API 不变 | 已完成 | API 面无变更；控制器复用现有 `Pf4bootPluginManager` 接口 |
| COMP-03 | 非 Web 应用不被迫引入 servlet 或管理依赖 | 已完成 | 同上兼容性测试中的 `nonWebStarterDoesNotExposeServletApi`；`pf4boot-starter/build.gradle` 无 servlet/management 依赖 |
| COMP-04 | Java 8 编译通过 | 已完成 | `:pf4boot-management-starter:compileJava` |

## 建议验收命令

```powershell
.\gradlew.bat :pf4boot-starter:test
.\gradlew.bat :pf4boot-api:test
.\gradlew.bat :pf4boot-core:test
.\gradlew.bat :pf4boot-web-starter:test
.\gradlew.bat :pf4boot-management-starter:test
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

如管理接口短期落在 `pf4boot-web-starter`，则跳过 `:pf4boot-management-starter:test`，并在 `:pf4boot-web-starter:test` 中覆盖管理接口场景。

## 手工 smoke

- `curl -I -H "X-PF4Boot-Admin-Token: sample-token" http://127.0.0.1:7791/pf4boot/admin/plugins`（鉴权路径）
- `curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" http://127.0.0.1:7791/pf4boot/admin/plugins/sample-workflow/start`（写操作）
- `curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" -H "Content-Type: application/json" -d '{"pluginId":"sample-workflow","stagedPluginPath":"build/sample-plugins/plugin-workflow-3.0.0-SNAPSHOT.zip","dryRun":true}' http://127.0.0.1:7791/pf4boot/admin/deployments/plan`（预检）
- `curl -X GET -H "X-PF4Boot-Admin-Token: sample-token" http://127.0.0.1:7791/pf4boot/admin/deployments`（查询）
- 同一幂等键复放：同体请求应返回缓存结果，不同体请求应返回冲突
- 测试本地 token 缺失、远程未授权、路径穿越与幂等冲突场景

样例参考：

- `samples/cross-plugin-jpa/README.md`
