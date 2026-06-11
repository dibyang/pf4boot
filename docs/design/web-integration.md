# Web 集成

## 问题

已启动插件可以贡献 Spring MVC controller、handler interceptor 和静态资源。这些动态 Web 能力必须在插件启动时加入，并在插件停止时移除。

## 自动配置

`Pf4bootMvcPatchAutoConfiguration` 在 PF4Boot 启用且 PF4J 类存在时生效。它会：

- 提供 `WebMvcRegistrations` Bean，将 Spring MVC 默认的 `RequestMappingHandlerMapping` 替换为 `PluginRequestMappingHandlerMapping`；
- 注册 `PluginResourceHandlerRegistrationCustomizer`，加入插件感知的静态资源解析；
- 监听 `AppCacheFreeEvent`，当前作为扩展点保留。

当 web starter 在 classpath 上时，`Pf4bootAutoConfiguration` 会创建 `WebPf4BootPluginSupport`。

## Controller 与 Interceptor 注册

`WebPf4BootPluginSupport.startedPlugin` 获取宿主应用中的 `requestMappingHandlerMapping` 后：

1. 注册插件 `HandlerInterceptor` Bean；
2. 注册插件 `@Controller` 和 `@RestController` Bean。

`PluginRequestMappingHandlerMapping` 注册插件 controller 的方式：

- 将 controller singleton 注册到宿主 MVC 上下文；
- 调用 `detectHandlerMethods(controller)`；
- 插件 controller 注册完成后调用 `handlerMethodsInitialized(getHandlerMethods())`。

插件 interceptor 保存在 `CopyOnWriteArrayList` 中，并插入到每个请求 `HandlerExecutionChain` 的最前面。

## Web 清理

`WebPf4BootPluginSupport.stopPlugin` 会：

1. 注销插件 controller；
2. 注销插件 interceptor；
3. 如果 `MethodValidationPostProcessor` 的 validator 由插件 classloader 创建，则清空该引用。

`PluginRequestMappingHandlerMapping.unregisterControllers` 会移除直接匹配插件 controller Bean 的映射，也会移除 bean type classloader 等于插件 classloader 的 handler method。

## 静态资源解析

`PluginPathResourceResolver` 会先从已启动插件的 classloader 中解析 classpath 资源，再回退到普通 Spring resource chain。

`PluginResourceHandlerRegistrationCustomizer` 会：

- 确保 resource chain cache 存在；
- 加入 `PluginPathResourceResolver`；
- 保留已配置的 encoded 和 versioned resource resolver；
- 在 `AppCacheFreeEvent` 时清空缓存。

插件管理器在插件启动、停止、重启和重载后发布 `AppCacheFreeEvent`，以便刷新 Web 资源缓存。

## 热替换 drain 与 mapping 摘除

`PluginRequestMappingHandlerMapping` 参与热替换部署的三个阶段：

- 作为 `PluginTrafficDrainer`，在 `beginDrain(pluginIds)` 后把影响链标记为 draining。
- 作为 `PluginCleanupVerifier`，在插件 stop 后检查 controller、interceptor 和在途请求计数是否已清零。
- 作为 `PluginHealthVerifier`，在新版本启动后输出 Web mapping 和 interceptor 数量。

请求进入插件 controller 前会经过内部 drain interceptor：

1. 如果 handler 属于 draining 插件，直接返回 HTTP 503，避免新请求进入即将停止的插件。
2. 如果允许进入，则增加该插件的 in-flight 计数。
3. 请求完成后减少 in-flight 计数。

部署服务调用 `awaitDrain(pluginIds, timeoutMillis)` 时，Web 层会等待影响链 in-flight 请求归零。超时会导致本次替换失败并进入回滚。

stop 阶段仍由 `WebPf4BootPluginSupport.stopPlugin` 负责注销 controller 和 interceptor。清理验证阶段只判断是否有残留：

- `WEB_MAPPING_NOT_CLEANED`：停止后仍有插件 handler mapping。
- `WEB_INTERCEPTOR_NOT_CLEANED`：停止后仍有插件 interceptor。
- `WEB_IN_FLIGHT_NOT_DRAINED`：停止后仍有在途请求计数。

## 兼容性

动态 MVC 注册依赖 Spring MVC 内部行为和 Bean 名称。`requestMappingHandlerMapping`、handler mapping 替换、interceptor 顺序或 resource resolver 顺序的变更，都可能影响宿主和插件路由。

## 验证

Web 变更运行：

- `.\gradlew.bat :pf4boot-web-starter:compileJava`
- `.\gradlew.bat :pf4boot-web-starter:test`
- `.\gradlew.bat :pf4boot-starter:compileJava`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

手动检查应包括启动 sample host、启动/停止插件、访问插件 controller 路由，并确认停止后路由消失。
