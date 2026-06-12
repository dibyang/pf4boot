# 插件 HTTP 管理接口验收清单

## 验收范围

本文用于追踪 [plugin-http-management-api.md](plugin-http-management-api.md) 和 [plugin-http-management-api-plan.md](plugin-http-management-api-plan.md) 的落地结果。当前为规划态，所有验收项默认未完成。

实施时同时参考 [plugin-http-management-api-implementation-guide.md](plugin-http-management-api-implementation-guide.md)，验收证据中的类名、测试名和模块路径应尽量与实施指南保持一致。

## 功能验收

| 编号 | 验收项 | 状态 | 证据 |
| --- | --- | --- | --- |
| AC-01 | 默认不注册 HTTP 写管理接口 | 未完成 | 待补 |
| AC-02 | `enabled=true` 但安全模式非法时启动失败 | 未完成 | 待补 |
| AC-03 | 本地 token 模式要求 loopback + token | 未完成 | 待补 |
| AC-04 | 远程模式缺少 authorizer 时启动失败 | 未完成 | 待补 |
| AC-05 | 插件列表和详情接口只读，不改变运行态 | 未完成 | 待补 |
| AC-06 | start/stop/restart/enable/disable 使用语义化 HTTP 方法 | 未完成 | 待补 |
| AC-07 | reload 被标记为低层运维兜底，不作为安全热替换 | 未完成 | 待补 |
| AC-08 | 部署预检不改变运行态 | 未完成 | 待补 |
| AC-09 | 热替换接口调用 `PluginDeploymentService.replace` | 未完成 | 待补 |
| AC-10 | staged 包路径不能逃逸配置根目录 | 未完成 | 待补 |
| AC-11 | 写操作支持幂等 key，相同请求不重复执行 | 未完成 | 待补 |
| AC-12 | 相同幂等 key 但请求体不同返回冲突 | 未完成 | 待补 |
| AC-13 | 审计记录覆盖成功、失败和拒绝请求 | 未完成 | 待补 |
| AC-14 | 错误响应不泄漏 token、敏感路径和完整堆栈 | 未完成 | 待补 |
| AC-15 | `pf4boot-actuator` 仍然只读 | 未完成 | 待补 |

## 安全验收

| 编号 | 验收项 | 状态 | 证据 |
| --- | --- | --- | --- |
| SEC-01 | 无 token 的本地写请求返回 `401` | 未完成 | 待补 |
| SEC-02 | 非 loopback 的本地模式请求返回 `403` | 未完成 | 待补 |
| SEC-03 | 远程未认证请求返回 `401` | 未完成 | 待补 |
| SEC-04 | 远程权限不足请求返回 `403` | 未完成 | 待补 |
| SEC-05 | 浏览器写操作缺少 CSRF/来源约束时被拒绝 | 未完成 | 待补 |
| SEC-06 | 写操作限流生效并返回 `429` | 未完成 | 待补 |
| SEC-07 | 启动时检测到公网绑定且无远程授权时失败 | 未完成 | 待补 |

## 兼容性验收

| 编号 | 验收项 | 状态 | 证据 |
| --- | --- | --- | --- |
| COMP-01 | 未引入管理 starter 的应用行为不变 | 未完成 | 待补 |
| COMP-02 | 现有 `Pf4bootPluginManager` API 不变 | 未完成 | 待补 |
| COMP-03 | 非 Web 应用不被迫引入 servlet 或管理依赖 | 未完成 | 待补 |
| COMP-04 | Java 8 编译通过 | 未完成 | 待补 |

## 建议验证命令

```powershell
.\gradlew.bat :pf4boot-api:test
.\gradlew.bat :pf4boot-core:test
.\gradlew.bat :pf4boot-web-starter:test
.\gradlew.bat :pf4boot-management-starter:test
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

如果管理接口短期落在 `pf4boot-web-starter`，则跳过 `:pf4boot-management-starter:test`，并在 `:pf4boot-web-starter:test` 中覆盖管理接口场景。

## 手工 smoke

待实现后补充具体命令，至少覆盖：

- 无 token 请求被拒绝；
- 正确 token 查询插件列表；
- 正确 token start/stop 插件；
- 预检 staged 插件包；
- 执行热替换并查询部署记录；
- 错误 token、错误权限、路径穿越和幂等冲突。
