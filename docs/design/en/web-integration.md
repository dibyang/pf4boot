# Web Integration

## Problem

Started plugins can contribute Spring MVC controllers, handler interceptors, and static resources. These dynamic web contributions must be added when a plugin starts and removed when it stops.

## Auto-Configuration

`Pf4bootMvcPatchAutoConfiguration` is enabled when PF4Boot is enabled and PF4J classes are present. It:

- provides a `WebMvcRegistrations` bean that replaces Spring MVC's `RequestMappingHandlerMapping` with `PluginRequestMappingHandlerMapping`;
- registers `PluginResourceHandlerRegistrationCustomizer` to add plugin-aware static resource resolution;
- listens for `AppCacheFreeEvent`, currently as an extension point.

`Pf4bootAutoConfiguration` creates `WebPf4BootPluginSupport` when the web starter is on the classpath.

## Controller And Interceptor Registration

`WebPf4BootPluginSupport.startedPlugin` obtains the host application's `requestMappingHandlerMapping` and:

1. registers plugin `HandlerInterceptor` beans;
2. registers plugin `@Controller` and `@RestController` beans.

`PluginRequestMappingHandlerMapping` registers plugin controllers by:

- registering the controller singleton into the host MVC context;
- calling `detectHandlerMethods(controller)`;
- calling `handlerMethodsInitialized(getHandlerMethods())` after plugin controller registration.

Plugin interceptors are stored in a `CopyOnWriteArrayList` and inserted at the front of each request's `HandlerExecutionChain`.

## Web Cleanup

`WebPf4BootPluginSupport.stopPlugin`:

1. unregisters plugin controllers;
2. unregisters plugin interceptors;
3. clears a `MethodValidationPostProcessor` validator reference if it was created by the plugin class loader.

`PluginRequestMappingHandlerMapping.unregisterControllers` removes mappings for both direct plugin controller beans and any handler method whose bean type class loader is the plugin class loader.

## Static Resource Resolution

`PluginPathResourceResolver` resolves classpath resources from started plugin class loaders before falling back to the normal Spring resource chain.

`PluginResourceHandlerRegistrationCustomizer`:

- ensures a resource chain cache exists;
- adds `PluginPathResourceResolver`;
- preserves configured encoded and versioned resource resolvers;
- clears the cache on `AppCacheFreeEvent`.

The plugin manager publishes `AppCacheFreeEvent` after plugin start/stop/restart/reload so web resource caches can be refreshed.

## Hot Replacement Drain And Mapping Removal

`PluginRequestMappingHandlerMapping` participates in three hot replacement deployment phases:

- as `PluginTrafficDrainer`, it marks the impact chain as draining after `beginDrain(pluginIds)`;
- as `PluginCleanupVerifier`, it checks controller, interceptor, and in-flight request counts after plugin stop;
- as `PluginHealthVerifier`, it reports web mapping and interceptor counts after the new version starts.

Requests entering plugin controllers pass through an internal drain interceptor:

1. If the handler belongs to a draining plugin, it returns HTTP 503 so new requests do not enter a plugin that is about to stop.
2. If the request is allowed, the plugin's in-flight count is incremented.
3. The in-flight count is decremented after request completion.

When the deployment service calls `awaitDrain(pluginIds, timeoutMillis)`, the web layer waits for in-flight requests in the impact chain to reach zero. Timeout fails the replacement and triggers rollback.

The stop phase is still owned by `WebPf4BootPluginSupport.stopPlugin`, which unregisters controllers and interceptors. Cleanup validation only detects residue:

- `WEB_MAPPING_NOT_CLEANED`: plugin handler mappings remain after stop.
- `WEB_INTERCEPTOR_NOT_CLEANED`: plugin interceptors remain after stop.
- `WEB_IN_FLIGHT_NOT_DRAINED`: in-flight request counts remain after stop.

## Compatibility

Dynamic MVC registration depends on Spring MVC internals and bean names. Changes to `requestMappingHandlerMapping`, handler mapping replacement, interceptor order, or resource resolver order can affect host and plugin routes.

## Verification

For web changes, run:

- `.\gradlew.bat :pf4boot-web-starter:compileJava`
- `.\gradlew.bat :pf4boot-web-starter:test`
- `.\gradlew.bat :pf4boot-starter:compileJava`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

Manual checks should include starting the sample host, starting/stopping plugins, hitting plugin controller routes, and confirming routes disappear after stop.
