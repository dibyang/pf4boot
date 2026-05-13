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

## 兼容性

动态 MVC 注册依赖 Spring MVC 内部行为和 Bean 名称。`requestMappingHandlerMapping`、handler mapping 替换、interceptor 顺序或 resource resolver 顺序的变更，都可能影响宿主和插件路由。

## 验证

Web 变更运行：

- `.\gradlew.bat :pf4boot-web-starter:compileJava`
- `.\gradlew.bat :pf4boot-starter:compileJava`
- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`

手动检查应包括启动 demo 应用、启动/停止插件、访问插件 controller 路由，并确认停止后路由消失。
