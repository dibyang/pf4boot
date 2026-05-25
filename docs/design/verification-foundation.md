# 验证地基

## 问题

框架已经覆盖插件生命周期、共享 Bean、动态 MVC、JPA starter 和插件打包等高风险路径，但测试目录基本为空，并且根构建会禁用名称包含 `test` 的任务。当前 `build` 成功只能证明编译和打包通过，不能证明插件启动、停止、重载和动态资源清理行为稳定。

## 影响模块

- `build.gradle`：调整测试任务策略，让显式测试任务可以执行。
- `pf4boot-core`：补充生命周期、共享 Bean、自动导出和动态元数据边界测试。
- `pf4boot-web-starter`：补充动态 controller 和 interceptor 注册/注销测试。
- `pf4boot-jpa-starter`：补充 JPA starter 条件和扫描包边界测试。

## 设计方案

移除根构建里按任务名禁用 `test` 的全局逻辑，保留现有 JUnit4 依赖，并按模块补充窄测试。测试优先直接驱动当前实现，而不是引入新的测试框架或运行完整 demo 应用。

`pf4boot-core` 测试使用轻量 Spring `ApplicationContext` 和测试插件类，覆盖：

- 插件 start、stop、restart、reload 的状态和 hook 顺序；
- 依赖插件停止时 dependent 也被停止；
- 启动失败后插件上下文被清理；
- `ROOT`、`APPLICATION`、`PLATFORM` 和 group 共享 Bean 注册与停止注销；
- 重复导出不会在停止后残留；
- `DynamicMetadata.sync()` 明确失败。

`pf4boot-web-starter` 测试直接实例化 `PluginRequestMappingHandlerMapping`，使用测试 controller/interceptor 和 mock request 验证动态映射、拦截器注册、注销和重复注册行为。

`pf4boot-jpa-starter` 测试不启动真实数据库连接。未启用时验证配置类不会创建 JPA bean；启用边界通过暴露 `getPackagesToScan()` 的测试子类验证插件主类包可作为扫描包；动态同步失败继续由 core 测试覆盖。

## 兼容性

测试策略变更会让显式 `test` 和 `build` 中的测试任务真实执行。运行时 API、插件打包结构和生命周期顺序不改变。若后续确实需要跳过测试，应使用 Gradle 标准的 `-x test`，而不是在根构建中全局禁用。

## 验证

最小验证命令：

- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :pf4boot-web-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test`

广义验证命令：

- `.\gradlew.bat build`

## 未决问题

本阶段先建立回归测试地基，不重构 Web/JPA starter 依赖边界，也不实现 JPA 运行时元数据同步。后续模块边界收敛应单独设计。
