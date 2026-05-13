# 生命周期清理职责修复

## 问题

当前插件停止流程存在职责重叠：

- `Pf4bootPluginManagerImpl.doStopPlugin()` 已执行 `pluginSupport.stopPlugin`、`pluginSupport.stoppedPlugin`、release hooks、`pluginSupport.releasePlugin`，随后调用 `plugin.closePluginContext()`。
- `Pf4bootPlugin.closePluginContext()` 又调用 `pluginManager.releasePlugin(this)`。
- `Pf4bootPluginManagerImpl.releasePlugin()` 再次执行 `pluginSupport.stopPlugin`、`shareBeanMgr.stopPlugin`、`pluginSupport.stoppedPlugin`、`pluginSupport.releasePlugin`。

这会导致 Web controller/interceptor 注销、共享 Bean 注销、定时任务注销和 release hook 重复执行。Web mapping 注销也在遍历 `getHandlerMethods()` 时直接调用 `unregisterMapping()`，存在边遍历边修改映射表的风险。

## 影响模块

- `pf4boot-api`：调整 `Pf4bootPlugin.closePluginContext()` 的职责。
- `pf4boot-core`：调整 `Pf4bootPluginManagerImpl.releasePlugin()` 和 `doStopPlugin()` 的职责边界。
- `pf4boot-web-starter`：调整插件 controller mapping 注销方式。

## 设计方案

停止流程的职责边界调整为：

- `doStopPlugin()` 负责插件生命周期语义：发布停止事件、调用 `pluginSupport.stopPlugin/stoppedPlugin/releasePlugin`、执行插件 `stop()`、执行 release hooks、注销 `ApplicationContextProvider`、关闭插件上下文、调用 `plugin.closed()`、更新插件状态。
- `releasePlugin()` 只负责释放插件注册到框架中的共享资源：调用 `shareBeanMgr.stopPlugin(plugin)` 并清理 Spring metadata cache。它不再调用 `pluginSupport.stopPlugin/stoppedPlugin/releasePlugin`。
- `closePluginContext()` 只负责关闭插件 Spring 上下文、销毁插件上下文内的 `pf4j.plugin` singleton、关闭委托 classloader、清理 Spring factories cache 和插件设置的系统属性。它不再调用 `pluginManager.releasePlugin(this)`。
- 启动失败时，`doStartPlugin()` 仍调用 `plugin.closePluginContext()` 做上下文级清理；因为此时插件尚未完成 `shareBeanMgr.startedPlugin()`，不需要运行完整停止 hook。

Web mapping 注销调整为：

- 先从 `getHandlerMethods()` 复制出需要删除的 `RequestMappingInfo` 列表；
- 再遍历快照调用 `unregisterMapping()`；
- 避免在 handler method map 的 `forEach` 中修改 map。

## 兼容性

公共 API 不变。停止流程中各 hook 的调用次数从可能重复调整为一次，符合生命周期文档的原始契约。若外部插件依赖重复调用 hook 的副作用，需要改为在单次 hook 中显式处理。

## 验证

运行：

- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :pf4boot-web-starter:compileJava`
- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`

手动验证建议：

- 启动 demo 应用，启动插件后访问插件 controller；
- 停止插件后确认 controller 路由消失；
- 重启或 reload 插件后确认 controller 路由重新出现；
- 观察日志中 stop/release hook 不再重复执行。

## 未决问题

本次不处理自动启动调度幂等、`SharingBeans` key 过窄、AutoExport group 丢失和 JPA 动态元数据空实现问题，这些放到后续批次。
