# AutoExport 与 JPA 能力边界

## 问题

前两批修复后仍有两个边界问题：

- `@AutoExports` 中声明的 `group` 没有传递到运行时规则，导致自动导出的 platform Bean 可能进入错误分组。
- `DefaultAutoExportMgr` 使用普通 `ArrayList`，插件启动、停止和共享 Bean 扫描并发时存在遍历与修改竞争。
- `DynamicMetadata` 暴露了运行时同步入口，但当前没有可安全更新 Hibernate `EntityManagerFactory` 和 metamodel 的实现。

## 影响模块

- `pf4boot-api`：补充 `AutoExportMgr` 的分组感知方法，并明确 `DynamicMetadata` 的运行时同步边界。
- `pf4boot-core`：使用线程安全集合保存自动导出规则；注册和移除规则时保留 `scope + group + type`。
- `docs/design`：记录 JPA 动态元数据当前不支持运行时同步，插件 JPA entity 仍以启动时扫描为准。

## 设计方案

`AutoExportMgr` 保留已有两参数和单参数方法，新增 Java 8 default 方法承载 `group`，避免已有调用方立刻适配。默认实现仍退化到旧语义；`DefaultAutoExportMgr` 覆盖分组感知方法。

`DefaultAutoExportMgr` 使用 `CopyOnWriteArrayList`。自动导出规则数量小、变更发生在插件启停阶段，读多写少，复制开销可接受，并且可以让共享 Bean 扫描拿到稳定快照。`getAutoExportClasses()` 返回不可变快照，避免调用方修改内部集合。

`DefaultShareBeanMgr` 注册 `@AutoExports` 时传递注解中的 `group`，注销时按 `type + scope + group` 删除对应规则，避免删除其他插件或其他分组的同类型规则。

`DynamicMetadata` 不实现运行时动态同步。`DefaultDynamicMetadata` 只维护线程安全的候选 entity class 集合，`sync()` 明确抛出 `UnsupportedOperationException`。插件 JPA entity 的有效边界仍是 `PluginJPAStarter` 创建 `EntityManagerFactory` 时的启动时扫描。

## 兼容性

`AutoExportMgr` 新方法是 default 方法，已有实现可继续编译；旧的 `removeAutoExportClass(Class<?>)` 仍保留旧行为。依赖旧行为的调用方不需要调整，但使用 `@AutoExports(group=...)` 的插件现在会按声明分组导出。

`DynamicMetadata.sync()` 从空操作变为明确失败。依赖空操作的代码会暴露调用错误；这是有意的兼容性收紧，避免误认为运行时 Hibernate metamodel 已同步。

## 验证

- `.\gradlew.bat :pf4boot-api:compileJava`
- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :pf4boot-jpa:compileJava`
- `.\gradlew.bat :pf4boot-jpa-starter:compileJava`

手动检查应覆盖同一类型在不同 group 下的 `@AutoExports` 规则不会互相覆盖或误删；JPA 插件仍通过启动时扫描创建 repository 和 entity。

## 未决问题

若未来要支持真正的运行时 JPA 同步，需要单独设计 `EntityManagerFactory` 重建或隔离策略、事务边界、repository 代理刷新和插件停止清理。
