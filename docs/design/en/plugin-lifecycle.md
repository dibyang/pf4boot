# Plugin Lifecycle

## Problem

Plugins can be loaded, started, stopped, restarted, reloaded, unloaded, and deleted at runtime. The manager must preserve dependency ordering, expose lifecycle events, and release plugin-owned resources reliably.

## Main Classes

- `Pf4bootPluginManagerImpl`: lifecycle orchestration and PF4J manager customization.
- `Pf4bootPlugin`: plugin-local Spring context creation and cleanup.
- `Pf4bootPluginSupport`: ordered extension hooks for framework integrations.
- `DefaultShareBeanMgr`: shared bean, extension, auto-export, and scheduled task lifecycle.
- `Pf4bootPluginWrapper`: start failure count, manual stop marker, and manual-intervention dependency checks.
- `MainAppStartedListener`: starts plugins after the host application has started.

## Startup Flow

1. Spring Boot loads `Pf4bootAutoConfiguration`.
2. `Pf4bootPluginManagerImpl` creates root and platform contexts and initializes PF4J.
3. `@PostConstruct init()` registers the manager as `pluginManager` in the root context, clears the plugin cache directory, and calls `loadPlugins()`.
4. `MainAppStartedListener` starts plugins when the host `ApplicationStartedEvent` arrives and `autoStartPlugin` is enabled.
5. `setApplicationStarted(true)` also schedules periodic automatic starts every 20 seconds after an initial delay. Failed plugins can be retried up to `MAX_FAILED_NUM`.

## Start Plugin Flow

`doStartPlugin` performs the critical sequence:

1. Validate the wrapper is resolved and not already started.
2. Start non-optional dependencies first.
3. Reject start if a required dependency is stopped or marked for manual intervention during auto-start.
4. Temporarily point Spring metadata reading at the plugin class loader.
5. Run `pluginSupport.initiatePlugin(plugin)`.
6. Create or get the plugin's group platform context.
7. Run `plugin.initiate()`.
8. Run `pluginSupport.initiatedPlugin(plugin)`.
9. Create the plugin Spring context from `@PluginStarter` classes.
10. Refresh the plugin context.
11. Publish `PreStartPluginEvent`.
12. Register the plugin context in `ApplicationContextProvider`.
13. Autowire the plugin instance.
14. Run `pluginSupport.startPlugin(plugin)` and publish `StartingPluginEvent`.
15. Call `plugin.start()`.
16. Register shared beans, extensions, auto-exports, and scheduled tasks through `ShareBeanMgr.startedPlugin`.
17. Run `pluginSupport.startedPlugin(plugin)`.
18. Mark the wrapper as `STARTED`, fire PF4J state events, publish `StartedPluginEvent`, and publish `AppCacheFreeEvent`.

If startup fails after context creation, `plugin.closePluginContext()` is called before the exception is rethrown.

## Stop Plugin Flow

`doStopPlugin` performs the stop sequence:

1. Stop dependent plugins first when requested.
2. Publish `PreStopPluginEvent`.
3. Run `pluginSupport.stopPlugin(plugin)`.
4. Publish `StoppingPluginEvent`.
5. Call `plugin.stop()`.
6. Run `pluginSupport.stoppedPlugin(plugin)`.
7. Mark state as `STOPPED` or `FAILED` when a dependency caused the stop.
8. Remove the wrapper from `startedPlugins`.
9. Fire PF4J state events and publish `StoppedPluginEvent`.
10. Run plugin release hooks.
11. Run `pluginSupport.releasePlugin(plugin)`.
12. Unregister the plugin context from `ApplicationContextProvider`.
13. Close the plugin context.
14. Call `plugin.closed()`.

`Pf4bootPlugin.closePluginContext()` calls `pluginManager.releasePlugin(this)`, destroys the plugin bean, closes the Spring context, closes the class loader when possible, clears Spring factory caches, and clears plugin-set system properties.

## Reload And Delete

- `restartPlugin` stops a started plugin and starts it again.
- `reloadPlugin` stops, unloads, reloads from the original path, then starts the plugin.
- `reloadPlugins(restartStartedOnly)` unloads all loaded plugins, reloads from plugin roots, and either restarts previously started plugins or starts all eligible plugins.
- `deletePlugin` stops and unloads the plugin, invokes plugin delete hooks, then delegates path deletion to the active plugin repository.

## Lifecycle Operations And Deployment Orchestration

`reloadPlugin`, `restartPlugin`, and `upgradePlugin` are low-level lifecycle primitives. They are useful for development, operational fallback, or local management operations, but they are not the safe hot replacement deployment flow.

Safe hot replacement is owned by `PluginDeploymentService`:

- `planReplacement(pluginId, stagedPluginPath)`: generates the deployment plan, impact scope, precheck results, and rollback snapshot without mutating runtime state.
- `replace(pluginId, stagedPluginPath)`: runs precheck, drain, impact-chain stop, cleanup validation, staged package load, impact-chain start, health checks, and rollback on failure.

The deployment orchestration layer reuses existing lifecycle ordering and does not change the `Pf4bootPluginManagerImpl` start/stop/reload/delete contracts. The difference is that deployment treats one replacement as an auditable state machine:

1. Calculate the impact chain from the PF4J dependency graph.
2. Drain the impact chain, reject new requests and scheduled executions, and wait for in-flight work to reach zero.
3. Stop plugins in dependents -> target order and run module cleanup validation.
4. Load the staged target package and reload affected dependents when needed.
5. Start plugins in target -> dependents order.
6. Run module health verifiers and plugin-local `PluginHealthProbe` beans.
7. Restore old packages and original started state from `RollbackSnapshot` on any failure; rollback failure moves to `MANUAL_INTERVENTION`.

Release-oriented hot replacement should therefore use `PluginDeploymentService.replace(...)`; do not expose `reloadPlugin` as the safe hot replacement capability.

## Compatibility

Lifecycle ordering is a contract. Changes can affect web mappings, exported beans, scheduled tasks, JPA resources, and external plugin code that listens for events.

## Verification

For lifecycle changes, run at least:

- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :pf4boot-starter:compileJava`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

Manual verification should include start, stop, restart, and reload through the `pf4boot-management-starter` endpoints `/pf4boot/admin/plugins/{pluginId}/start`, `/stop`, `/restart`, and `/reload` when possible. The old `pf4boot-web-starter` management controller is no longer kept.
