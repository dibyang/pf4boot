# 上下文与 Bean 共享

## 问题

插件需要隔离本地 Bean 和类加载，但部分服务又必须对宿主应用或其他插件可见。当前设计提供显式共享作用域，并记录每个插件实际导出的内容，使停止清理不依赖重新扫描正在关闭的插件上下文。

## 上下文作用域

`SharingScope` 决定导出 Bean 注册到哪里：

- `ROOT`：注册到插件管理器 root 上下文，通过父 BeanFactory 全局可见。
- `APPLICATION`：注册到宿主应用上下文。
- `PLATFORM`：注册到默认 platform 上下文或分组 platform 上下文。

`Pf4bootPlugin.getGroup()` 从 `@PluginStarter` 读取分组；默认分组是 `PluginStarter.DEFAULT`。

## 导出机制

共享系统由 `DefaultShareBeanMgr` 实现。

支持的导出声明：

- Bean 类或 Bean 方法上的 `@Export`。
- `@ShareComponent`，组合了 Spring `@Component` 和 `@Export`。
- `@ExportBeans`，按 Bean 名称或类型导出。
- `@PluginStarter`，它本身以 `@ExportBeans` 作为元注解。
- `@AutoExports`，为匹配类型的 Bean 注册自动导出规则。

宿主应用在插件管理器初始化时会扫描一次，因此宿主侧共享服务也可以被导出。

## 启动注册顺序

插件启动时，`DefaultShareBeanMgr.startedPlugin` 会：

1. 注册插件声明的自动导出规则。
2. 查找并注册共享 Bean 到 root、application 或 platform 作用域。
3. 按插件 id 记录实际注册的 `SharingBeans`。
4. 将 PF4J extension 类注册为 platform 上下文中的 Spring Bean。
5. 注册插件 `@Scheduled` 定时任务。

这个顺序保证定时任务开始运行前，共享 Bean 和 extension Bean 已准备好。

## 停止清理顺序

插件停止时，`DefaultShareBeanMgr.stopPlugin` 会：

1. 取消插件定时任务。
2. 从 platform 上下文注销 extension Bean。
3. 按反向作用域顺序注销记录过的共享 Bean：application、platform、root。
4. 移除插件的自动导出规则。

设计上刻意使用启动时记录的 `SharingBeans` 注销，而不是在关闭阶段重新扫描插件上下文。这样可以避免 Bean 已销毁或上下文已部分变化时产生清理错误。

## 事件发布

`Pf4bootPluginManagerImpl.publishEvent` 会将事件发布到：

1. root 上下文；
2. application 上下文；
3. 默认 platform 上下文；
4. 分组 platform 上下文；
5. 插件上下文；如果提供了来源插件上下文，则先发布给来源上下文。

插件上下文图不依赖 Spring 父子事件传播，事件发布是显式完成的。

## ApplicationContextProvider

`ApplicationContextProvider` 将插件上下文 classloader 映射到插件 Spring 上下文。管理器在 `PreStartPluginEvent` 后注册，在停止期间注销。这让只有 class 或 classloader 的代码也能找到自己的插件本地上下文。

## 兼容性

导出注解、共享作用域、Bean 名称、注销顺序和事件发布规则的变更，都可能破坏插件间集成。优先做增量变更，并记录任何可见性规则变化。

## 验证

共享机制变更运行：

- `.\gradlew.bat :pf4boot-api:compileJava`
- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`

手动检查应确认导出 Bean 在插件停止后消失，并在插件重启后重新出现。
