# 调度与共享 Bean 记录修复

## 问题

当前实现存在两个稳定性问题：

- `Pf4bootPluginManagerImpl.setApplicationStarted(true)` 每次被调用都会注册一个新的自动启动周期任务，没有保存 `ScheduledFuture`，也没有幂等保护。重复调用会导致多个后台任务同时尝试自动启动插件。
- `SharingBeans` 使用 `beanName` 作为唯一 key。若同一个插件把同名 Bean 导出到不同 `SharingScope` 或不同 platform group，后加入的记录会覆盖先前记录，插件停止时无法完整注销所有导出记录。

## 影响模块

- `pf4boot-core`：自动启动调度任务的注册和关闭。
- `pf4boot-api`：共享 Bean 记录结构。

## 设计方案

### 自动启动调度

- 在 `Pf4bootPluginManagerImpl` 中新增 `ScheduledFuture<?> autoStartFuture` 字段。
- `setApplicationStarted(true)` 只在 `autoStartFuture` 不存在或已取消/已完成时注册周期任务。
- `setApplicationStarted(false)` 取消已有自动启动任务并清空引用。
- `close()` 中关闭调度线程池前先取消自动启动任务。
- 自动启动任务内部继续调用 `doStartPlugins(true)`，保持原有重试策略不变。

### 共享 Bean 记录

- `SharingBeans` 的内部 key 改为 `scope + group + beanName`。
- `ROOT` 和 `APPLICATION` 使用空 group，`PLATFORM` 使用实际 group。
- `SharingBean.equals/hashCode` 与记录 key 保持一致，比较 `beanName`、`scope`、`group` 三个维度。
- 现有 `getRootBeans()`、`getAppBeans()`、`getPlatformBeans()` API 保持不变。

## 兼容性

公共方法签名不变。自动启动仍按原有时间间隔运行，但重复调用 `setApplicationStarted(true)` 不再创建重复任务。共享 Bean 记录可以保留更多导出项，不会改变已存在的单 scope 导出行为。

## 验证

运行：

- `.\gradlew.bat :pf4boot-api:compileJava`
- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`

手动验证建议：

- 多次触发 `setApplicationStarted(true)`，确认只有一个自动启动任务运行；
- 构造同名 Bean 导出到不同 scope/group 的插件，确认停止时全部注销。

## 未决问题

本次不处理 AutoExport group 丢失、AutoExport 集合线程安全和 JPA 动态元数据空实现问题。
