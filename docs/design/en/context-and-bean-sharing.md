# Context And Bean Sharing

## Problem

Plugins need isolation for local beans and class loading, but selected services must be visible to the host application or other plugins. The design provides explicit sharing scopes and records what each plugin exported so stop cleanup does not depend on rescanning a closing context.

## Context Scopes

`SharingScope` drives where exported beans are registered:

- `ROOT`: registered into the plugin manager root context, globally visible through parent bean factories.
- `APPLICATION`: registered into the host application context.
- `PLATFORM`: registered into the default platform context or a group-specific platform context.

`Pf4bootPlugin.getGroup()` reads the group from `@PluginStarter`; the default group is `PluginStarter.DEFAULT`.

## Export Mechanisms

The sharing system is implemented by `DefaultShareBeanMgr`.

Supported export declarations:

- `@Export` on bean classes or bean methods.
- `@ShareComponent`, which combines Spring `@Component` and `@Export`.
- `@ExportBeans`, which exports beans by name or by type.
- `@PluginStarter`, which is meta-annotated with `@ExportBeans`.
- `@AutoExports`, which registers export rules for matching bean types and preserves the declared `scope` and `group`.

The host application is scanned once during plugin manager initiation so host-side shared services can also be exported.

## Start Registration Order

When a plugin starts, `DefaultShareBeanMgr.startedPlugin`:

1. Registers plugin-declared auto-export rules.
2. Finds and registers shared beans into root, application, or platform scopes.
3. Records the actual `SharingBeans` for the plugin id.
4. Registers PF4J extension classes as Spring beans in the platform context.
5. Registers plugin `@Scheduled` tasks.

This order makes shared beans and extension beans available before scheduled tasks begin running.

## Stop Cleanup Order

When a plugin stops, `DefaultShareBeanMgr.stopPlugin`:

1. Cancels plugin scheduled tasks.
2. Unregisters extension beans from the platform context.
3. Unregisters the recorded shared beans in reverse scope order: application, platform, root.
4. Removes the plugin's auto-export rules.

The design intentionally unregisters from the recorded `SharingBeans` rather than rescanning the plugin context during shutdown. This avoids failures when beans have already been destroyed or the plugin context has partially changed.

## Event Publication

`Pf4bootPluginManagerImpl.publishEvent` publishes events to:

1. root context;
2. application context;
3. default platform context;
4. group-specific platform contexts;
5. plugin contexts, including the originating plugin context first when provided.

Spring parent event propagation is not relied on for the plugin context graph; publication is explicit.

## ApplicationContextProvider

`ApplicationContextProvider` maps a plugin context class loader to that plugin's Spring context. The manager registers it after `PreStartPluginEvent` and unregisters it during stop. This enables code that only has a class or class loader to locate its plugin-local context.

## Hot Replacement Cleanup Validation

Hot replacement deployment calls `PluginCleanupVerifier` after stop and before unload. `DefaultShareBeanMgr` acts as the core module verifier and checks that these resources have been released:

- shared beans exported by the plugin into root, application, or platform contexts;
- PF4J extension beans in the platform context;
- plugin scheduled tasks and scheduled tasks that are still running.

The same object also implements `PluginTrafficDrainer`:

- `beginDrain(pluginIds)` marks the impact chain as draining in the scheduled task manager.
- While draining, plugin scheduled tasks no longer start new executions.
- `awaitDrain(pluginIds, timeoutMillis)` waits for already running tasks to finish; timeout returns failure and triggers deployment rollback.
- `endDrain(pluginIds)` clears draining markers during success or failure cleanup.

During health checks, `DefaultShareBeanMgr` also implements `PluginHealthVerifier` and contributes shared bean, extension bean, and scheduled task counts to the deployment record. Count results are observational by default; residue found during cleanup validation is what blocks deployment.

This design keeps the deployment service from understanding every shared resource type. Instead, each module contributes cleanup through `PluginTrafficDrainer`, `PluginCleanupVerifier`, and `PluginHealthVerifier`.

## Compatibility

Changes to export annotations, sharing scopes, bean names, unregister order, or event publication can break plugin-to-plugin integration. Prefer additive changes and document any altered visibility rule.

## Verification

For sharing changes, run:

- `.\gradlew.bat :pf4boot-api:compileJava`
- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

Manual checks should confirm exported beans disappear after plugin stop and reappear after restart.
