# 插件热替换部署验收记录

## 1. 验收范围

本记录用于追踪插件热替换部署改进从 M1 到 M7 的可验收结果。验收对象是第一阶段短暂停机式安全替换能力：

- 预检生成部署计划但不改变运行态。
- 根据依赖图计算影响范围。
- 对目标插件和 dependents 执行 drain、stop、unload/load、start、health check。
- 加载、启动、健康检查和清理验证失败时自动回滚。
- Web、定时任务、共享 Bean、JPA 资源参与 drain、清理验证和健康检查。
- 文档说明 lifecycle 原语与部署编排的边界。

不验收以下能力：

- 零中断强一致切换。
- JTA/XA 或跨数据源事务。
- 框架托管的 active/staged/backup/failed 原子目录切换。
- 管理面安全化。

## 2. 验收清单

| 编号 | 验收项 | 证据 | 状态 |
| --- | --- | --- | --- |
| AC-01 | 预检不改运行态 | `DefaultPluginDeploymentServiceTest.precheckDoesNotMutateRuntimeState` | 已通过 |
| AC-02 | 影响范围准确 | `DefaultPluginDeploymentServiceTest.planReplacementCalculatesImpactScopeAndOrders` | 已通过 |
| AC-03 | 停止顺序正确 | `DefaultPluginDeploymentServiceTest.replaceStopsUnloadsLoadsAndStartsInOrder` | 已通过 |
| AC-04 | 启动顺序正确 | `DefaultPluginDeploymentServiceTest.replaceStopsUnloadsLoadsAndStartsInOrder` | 已通过 |
| AC-05 | 新包失败可回滚 | `replaceRollsBackWhenNewPluginStartFails`、`replaceRollsBackWhenPackageActivationFails`、`replaceRollsBackWhenPluginHealthProbeFails` | 已通过 |
| AC-06 | 回滚失败可诊断 | `DefaultPluginDeploymentServiceTest.replaceMovesToManualInterventionWhenRollbackFails` | 已通过 |
| AC-07 | Web drain 生效 | `PluginRequestMappingHandlerMappingTest` 覆盖 draining 503 和 in-flight 计数 | 已通过 |
| AC-08 | 在途请求可等待 | `DefaultPluginDeploymentServiceTest.replaceRollsBackWhenDrainTimeouts` | 已通过 |
| AC-09 | 资源清理可验证 | `replaceRollsBackWhenCleanupVerificationFails`、Web/core/JPA cleanup verifier 测试 | 已通过 |
| AC-10 | JPA provider 顺序安全 | `replaceJpaProviderStopsConsumersBeforeProviderAndStartsProviderFirst`、`replaceJpaProviderRollsBackWhenHealthVerifierFails` | 已通过 |
| AC-11 | 健康检查可扩展 | `replaceRunsPluginHealthProbeAndSucceeds`、`replaceRollsBackWhenPluginHealthProbeFails` | 已通过 |
| AC-12 | 旧 API 兼容 | 热替换新增 `PluginDeploymentService`，未改变 `start/stop/reload/delete` 对外语义；通过现有 compile/test 验证 | 已通过 |

说明：

- 第一阶段的包激活以 staged 路径加载实现；物理目录移动和失败包归档由外部运维流程处理。

## 3. 推荐验证命令

核心生命周期、部署编排和 JPA 约束：

```powershell
.\gradlew.bat :pf4boot-api:compileJava :pf4boot-core:test :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-starter:compileJava
```

Web drain 和清理验证：

```powershell
.\gradlew.bat :pf4boot-web-starter:test :pf4boot-starter:compileJava
```

复杂示例插件打包：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

可运行分发包：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:sampleDistZip
```

## 4. 手工 smoke 脚本

第一阶段没有管理 API/CLI，因此手工 smoke 以 sample host 和测试入口为主：

1. 构建 sample 插件包：`.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`。
2. 启动 sample host：`.\gradlew.bat :samples:cross-plugin-jpa:demo-host:runSampleHost`。
3. 调用 sample workflow 接口，确认旧版本 provider、service、workflow 三插件正常工作。
4. 准备同 plugin id 的 staged 候选包。
5. 通过宿主或临时测试入口调用 `PluginDeploymentService.planReplacement(...)`，检查 stop/start 顺序。
6. 调用 `PluginDeploymentService.replace(...)`，观察部署记录中的 `DRAINING`、`STOPPING`、`CLEANUP_VERIFYING`、`STARTING`、`VERIFYING`、`SUCCEEDED`。
7. 使用故障候选包重复执行，确认进入 `ROLLING_BACK` 并恢复旧版本。

## 5. 生产使用约束

- 发布入口应使用 `PluginDeploymentService.replace(...)`，不能把 `reloadPlugin` 包装成安全热替换。
- 有数据库结构变更时必须执行 expand/contract；回滚窗口内不得做破坏旧版本兼容性的 Contract 操作。
- `spring.jpa.hibernate.ddl-auto=update` 不作为生产 schema 迁移方案。
- 多数据源插件需要按数据源拆分实体和 Repository 包，并显式配置各数据源扫描路径；跨数据源事务暂不支持。
- 如果运维流程需要 active/staged/backup/failed 目录归档，应在框架外保证 staged 包完整落盘和失败包留存。

## 6. 本阶段结论

第一阶段热替换部署能力已经形成可复查闭环：设计、实施规划、模块文档、验收清单、核心测试和复杂示例打包验证互相对应。剩余生产化增强主要集中在框架托管包目录激活、持久化部署记录和管理 API/CLI。
