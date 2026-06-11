# Starter 边界收敛

## 问题

`pf4boot-starter` 当前通过 `api` 依赖直接暴露 `pf4boot-web-starter` 和 `pf4boot-jpa`，并在核心自动配置中直接创建 Web 支持组件。这样即使宿主应用只需要插件生命周期和共享 Bean，也会被动感知 Web MVC 和 JPA/Hibernate 相关类型，和“Web/JPA 是可选集成层”的架构目标不一致。

## 影响模块

- `pf4boot-starter`：收敛为核心 starter，只负责插件管理器、生命周期监听和共享 Bean 基础能力。
- `pf4boot-web-starter`：独立注册 Web MVC patch、资源解析器和 `WebPf4BootPluginSupport`。
- `pf4boot-core`：移除未使用的 JPA/Hibernate 运行时依赖，避免核心 starter 通过 core 间接携带 Hibernate。
- `pf4boot-jpa` / `pf4boot-jpa-starter`：保持 JPA 能力独立，插件需要时显式依赖或 bundle。
- `samples/cross-plugin-jpa:demo-host`：显式声明 Web starter，保持 sample 中动态 controller/resource 行为。

## 设计方案

`pf4boot-starter` 移除对 `pf4boot-web-starter` 和 `pf4boot-jpa` 的 `api` 依赖。`pf4boot-core` 移除未使用的 Hibernate/JPA 外部依赖，只保留位于 `pf4boot-api` 的动态元数据接口实现。核心自动配置不再引用 `WebPf4BootPluginSupport` 或 Web 资源解析器，只通过 `ObjectProvider<Pf4bootPluginSupport>` 收集 classpath 上可用的扩展支持组件。若没有任何组件，则使用空实现；若有多个组件，则按 `getPriority()` 从小到大组合调用。

`pf4boot-web-starter` 增加自己的 `META-INF/spring.factories`，注册 `Pf4bootMvcPatchAutoConfiguration`。该自动配置创建 `WebPf4BootPluginSupport`、`PluginPathResourceResolver`、`PluginRequestMappingHandlerMapping` patch、插件资源链 customizer 和 REST 管理接口。只有应用显式依赖 Web starter 时，这些 Web MVC 集成才进入宿主上下文。

JPA 保持插件侧 opt-in：核心 starter 不依赖 `pf4boot-jpa` 或 `pf4boot-jpa-starter`。插件如果需要 JPA starter，继续通过 `@PluginStarter({..., PluginJPAStarter.class})` 和插件本地配置 `pf4boot.plugin.jpa.enabled=true` 启用，并按插件打包规则 bundle 必要 starter。

## 兼容性

二进制 API 不变，`Pf4bootPluginSupport` 仍是原有扩展接口。行为兼容点如下：

- 只依赖 `pf4boot-starter` 的应用将不再自动获得插件 Web MVC controller、interceptor、静态资源集成和 REST 管理接口。
- 需要 Web 集成的宿主应用必须新增 `pf4boot-web-starter` 依赖；迁移方式是保留 `pf4boot-starter`，再显式加入 `pf4boot-web-starter`。
- 需要 JPA 的插件继续显式 bundle `pf4boot-jpa-starter`，核心宿主不再因为 starter 传递依赖而被动带入 Hibernate 支持。
- 如果应用自定义了 `Pf4bootPluginSupport`，现在可以和 Web 支持并存，框架会按 priority 组合调用。

## 验证

最小验证：

- `.\gradlew.bat :pf4boot-starter:compileJava`
- `.\gradlew.bat :pf4boot-web-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava`

广义验证：

- `.\gradlew.bat build`

## 未决问题

本阶段只收敛 starter 依赖边界，不改变管理接口 HTTP 语义，也不实现 JPA 运行时元数据同步。插件包中哪些 JPA 支持类应由宿主提供还是插件 bundle，仍需在后续打包契约设计中进一步明确。
