# Lifecycle Cleanup Responsibility Fix

## Problem

The current plugin stop flow overlaps responsibilities:

- `Pf4bootPluginManagerImpl.doStopPlugin()` already executes `pluginSupport.stopPlugin`, `pluginSupport.stoppedPlugin`, release hooks, and `pluginSupport.releasePlugin`, then calls `plugin.closePluginContext()`.
- `Pf4bootPlugin.closePluginContext()` calls `pluginManager.releasePlugin(this)`.
- `Pf4bootPluginManagerImpl.releasePlugin()` again executes `pluginSupport.stopPlugin`, `shareBeanMgr.stopPlugin`, `pluginSupport.stoppedPlugin`, and `pluginSupport.releasePlugin`.

This can unregister web controllers/interceptors, shared beans, scheduled tasks, and release hooks more than once. Web mapping unregistering also calls `unregisterMapping()` while iterating `getHandlerMethods()`, which risks mutating the handler map during iteration.

## Affected Modules

- `pf4boot-api`: adjust the responsibility of `Pf4bootPlugin.closePluginContext()`.
- `pf4boot-core`: adjust the responsibility boundary of `Pf4bootPluginManagerImpl.releasePlugin()` and `doStopPlugin()`.
- `pf4boot-web-starter`: adjust plugin controller mapping unregistering.

## Proposed Design

The stop flow responsibilities become:

- `doStopPlugin()` owns plugin lifecycle semantics: publish stop events, call `pluginSupport.stopPlugin/stoppedPlugin/releasePlugin`, call plugin `stop()`, run release hooks, unregister `ApplicationContextProvider`, close the plugin context, call `plugin.closed()`, and update plugin state.
- `releasePlugin()` only releases framework-registered resources for the plugin: call `shareBeanMgr.stopPlugin(plugin)` and clear Spring metadata caches. It no longer calls `pluginSupport.stopPlugin/stoppedPlugin/releasePlugin`.
- `closePluginContext()` only closes the plugin Spring context, destroys the plugin-local `pf4j.plugin` singleton, closes the delegating class loader, clears Spring factories cache, and clears plugin-set system properties. It no longer calls `pluginManager.releasePlugin(this)`.
- On startup failure, `doStartPlugin()` still calls `plugin.closePluginContext()` for context-level cleanup. Since the plugin has not completed `shareBeanMgr.startedPlugin()`, a full stop hook is not required.

Web mapping unregistering becomes:

- copy matching `RequestMappingInfo` entries from `getHandlerMethods()` into a snapshot list;
- call `unregisterMapping()` while iterating the snapshot;
- avoid mutating the handler method map inside its `forEach`.

## Compatibility

Public APIs remain unchanged. Stop hooks change from possibly repeated calls to one call, which matches the lifecycle documentation. External plugins that depended on repeated hook side effects should move that work into a single hook call.

## Verification

Run:

- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :pf4boot-web-starter:compileJava`
- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`

Recommended manual checks:

- start the demo application and access plugin controller routes after plugin start;
- stop the plugin and confirm plugin controller routes disappear;
- restart or reload the plugin and confirm routes come back;
- inspect logs and confirm stop/release hooks no longer run twice.

## Open Questions

This change does not address auto-start scheduler idempotency, narrow `SharingBeans` keys, lost AutoExport group values, or the empty JPA dynamic metadata implementation. Those remain for later batches.
