# 插件生命周期

## 问题

插件可以在运行时加载、启动、停止、重启、重载、卸载和删除。管理器必须保持依赖顺序，暴露生命周期事件，并可靠释放插件持有的资源。

## 主要类

- `Pf4bootPluginManagerImpl`：生命周期编排和 PF4J manager 定制。
- `Pf4bootPlugin`：插件本地 Spring 上下文创建与清理。
- `Pf4bootPluginSupport`：框架集成的有序扩展 hook。
- `DefaultShareBeanMgr`：共享 Bean、扩展、自动导出和定时任务生命周期。
- `Pf4bootPluginWrapper`：启动失败次数、手动停止标记和需要人工介入的依赖检查。
- `MainAppStartedListener`：宿主应用启动后启动插件。

## 启动链路

1. Spring Boot 加载 `Pf4bootAutoConfiguration`。
2. `Pf4bootPluginManagerImpl` 创建 root 和 platform 上下文，并初始化 PF4J。
3. `@PostConstruct init()` 将管理器以 `pluginManager` 注册到 root 上下文，清空插件缓存目录，并调用 `loadPlugins()`。
4. 宿主应用触发 `ApplicationStartedEvent` 后，`MainAppStartedListener` 在 `autoStartPlugin` 启用时启动插件。
5. `setApplicationStarted(true)` 还会安排周期性自动启动任务：初始延迟 10 秒，之后每 20 秒尝试一次。失败插件最多重试 `MAX_FAILED_NUM` 次。

## 单插件启动流程

`doStartPlugin` 的关键顺序：

1. 校验 wrapper 已解析且尚未启动。
2. 先启动非可选依赖。
3. 自动启动时，如果必需依赖已手动停止或标记为需要人工介入，则拒绝启动。
4. 临时将 Spring metadata 读取指向插件 classloader。
5. 执行 `pluginSupport.initiatePlugin(plugin)`。
6. 创建或获取插件所属分组的 platform 上下文。
7. 执行 `plugin.initiate()`。
8. 执行 `pluginSupport.initiatedPlugin(plugin)`。
9. 根据 `@PluginStarter` 类创建插件 Spring 上下文。
10. refresh 插件上下文。
11. 发布 `PreStartPluginEvent`。
12. 将插件上下文注册到 `ApplicationContextProvider`。
13. 对插件实例执行 autowire。
14. 执行 `pluginSupport.startPlugin(plugin)` 并发布 `StartingPluginEvent`。
15. 调用 `plugin.start()`。
16. 通过 `ShareBeanMgr.startedPlugin` 注册共享 Bean、扩展、自动导出和定时任务。
17. 执行 `pluginSupport.startedPlugin(plugin)`。
18. 将 wrapper 标记为 `STARTED`，触发 PF4J state event，发布 `StartedPluginEvent` 和 `AppCacheFreeEvent`。

如果上下文创建后启动失败，会先调用 `plugin.closePluginContext()` 再继续抛出异常。

## 单插件停止流程

`doStopPlugin` 的停止顺序：

1. 需要时先停止依赖当前插件的插件。
2. 发布 `PreStopPluginEvent`。
3. 执行 `pluginSupport.stopPlugin(plugin)`。
4. 发布 `StoppingPluginEvent`。
5. 调用 `plugin.stop()`。
6. 执行 `pluginSupport.stoppedPlugin(plugin)`。
7. 将状态标记为 `STOPPED`；如果由依赖导致停止，则标记为 `FAILED`。
8. 从 `startedPlugins` 移除 wrapper。
9. 触发 PF4J state event 并发布 `StoppedPluginEvent`。
10. 执行插件 release hooks。
11. 执行 `pluginSupport.releasePlugin(plugin)`。
12. 从 `ApplicationContextProvider` 注销插件上下文。
13. 关闭插件上下文。
14. 调用 `plugin.closed()`。

`Pf4bootPlugin.closePluginContext()` 会调用 `pluginManager.releasePlugin(this)`，销毁插件 Bean，关闭 Spring 上下文，必要时关闭 classloader，清理 Spring factory 缓存，并清除插件设置的系统属性。

## 重载与删除

- `restartPlugin` 会停止已启动插件，然后重新启动。
- `reloadPlugin` 会停止、卸载、从原路径重新加载，然后启动插件。
- `reloadPlugins(restartStartedOnly)` 会卸载所有已加载插件，从插件根目录重新加载，并按参数决定只重启原先已启动插件或启动所有可启动插件。
- `deletePlugin` 会停止并卸载插件，执行插件删除 hook，然后委托当前插件仓库删除路径。

## 生命周期操作与部署编排

`reloadPlugin`、`restartPlugin` 和 `upgradePlugin` 是底层生命周期原语。它们适合开发、运维兜底或局部管理操作，但不等同于安全热替换部署。

安全热替换由 `PluginDeploymentService` 承载，入口包括：

- `planReplacement(pluginId, stagedPluginPath)`：只生成部署计划、影响范围、预检结果和回滚快照，不改变运行态。
- `replace(pluginId, stagedPluginPath)`：执行预检、drain、停止影响链、清理验证、加载 staged 包、启动影响链、健康检查和失败回滚。

部署编排层复用现有生命周期顺序，不改变 `Pf4bootPluginManagerImpl` 的 start/stop/reload/delete 契约。区别在于部署层会把一次替换当成可审计状态机处理：

1. 根据 PF4J 依赖图计算影响链。
2. 先 drain 影响链，拒绝新请求和新定时任务，等待在途工作归零。
3. 按 dependents -> target 停止插件，并执行模块级清理验证。
4. 加载 staged 目标包，必要时重新加载受影响 dependents。
5. 按 target -> dependents 启动插件。
6. 执行模块级 health verifier 和插件本地 `PluginHealthProbe`。
7. 任一阶段失败时按 `RollbackSnapshot` 恢复旧包和原启动状态；回滚失败进入 `MANUAL_INTERVENTION`。

因此，面向发布的热替换应使用 `PluginDeploymentService.replace(...)`，不要直接把 `reloadPlugin` 暴露为“安全热替换”能力。

## 兼容性

生命周期顺序本身是契约。顺序变化可能影响 Web 映射、导出 Bean、定时任务、JPA 资源以及监听生命周期事件的外部插件代码。

## 验证

生命周期变更至少运行：

- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :pf4boot-starter:compileJava`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

可行时通过 `pf4boot-management-starter` 提供的 `/pf4boot/admin/plugins/{pluginId}/start`、`/stop`、`/restart` 和 `/reload` 手动验证生命周期操作。旧的 `pf4boot-web-starter` 管理控制器不再保留。
