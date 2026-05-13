# 代码质量修复

## 问题

本次检查发现三个会影响运行稳定性的代码质量问题：

- `DefaultScheduledMgr` 使用 `IdentityHashMap<String, ...>` 按插件 id 记录定时任务。`IdentityHashMap` 按引用比较 key，可能导致同名插件 id 无法注销定时任务。
- `DefaultScheduledMgr.destroy()` 遍历 `scheduledTasks.keySet()` 时会调用注销逻辑删除 map 项，存在遍历期间修改集合的风险。
- `ZipPf4bootPluginLoader` 假设解压后的 `lib` 目录一定存在；没有 `lib` 目录或没有 jar 时可能触发空指针。
- `Pf4bootPluginClassLoader.setPluginFirstClasses` 是空实现，配置项被读取但无法影响类加载。
- `plugin2` 源码引用 `pf4boot-api` 和 `plugin1` 类型，但模块构建文件没有把它们加入编译 classpath，导致 `:plugin2:build` 无法独立通过。

## 影响模块

- `pf4boot-core`：修复定时任务管理、zip 插件加载和插件 classloader 行为。
- `plugin2`：补齐编译期依赖声明，保持插件间运行时依赖声明不变。

## 设计方案

- 将 `DefaultScheduledMgr.scheduledTasks` 改为普通 `HashMap`，继续通过现有 synchronized 块保证访问串行化。
- `destroy()` 先复制插件 id 快照，再逐个注销，避免边遍历边修改。
- `ZipPf4bootPluginLoader` 在读取 `lib` 目录结果前做空值判断；没有 jar 时直接返回已创建的 classloader。
- `Pf4bootPluginClassLoader` 将 `pluginFirstClasses` 和 `pluginOnlyResources` 都转换为 glob pattern，并在命中 `pluginFirstClasses` 时先尝试 `findClass`，失败后回退到默认 PF4J 加载策略。
- `plugin2` 增加 `compileOnlyApi project(":pf4boot-api")` 和 `compileOnlyApi project(":plugin1")`，让源码编译所需 API 明确进入编译 classpath；保留 `plugin project(":plugin1")` 用于表达插件间依赖。

## 兼容性

这些修复不改变公共 API。`pluginOnlyResources` 的语义保持不变；`pluginFirstClasses` 从无效配置变为生效配置。定时任务注销更符合原设计意图。`plugin2` 的补充依赖只影响编译 classpath，不改变插件间运行时依赖声明。

## 验证

运行：

- `.\gradlew.bat :pf4boot-core:compileJava`

如需更完整验证，再运行：

- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`

## 未决问题

未覆盖更大范围的重构，例如生命周期中重复调用 `pluginSupport.stopPlugin` 的行为，本次只修复明确的稳定性缺陷。
