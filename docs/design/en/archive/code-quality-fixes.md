# Code Quality Fixes

## Problem

This review found three code quality issues that can affect runtime stability:

- `DefaultScheduledMgr` uses `IdentityHashMap<String, ...>` to track scheduled tasks by plugin id. Because `IdentityHashMap` compares keys by reference, tasks may not be unregistered when an equal plugin id string is not the same object.
- `DefaultScheduledMgr.destroy()` iterates `scheduledTasks.keySet()` while the unregister path removes entries from the same map, which risks mutating a collection during iteration.
- `ZipPf4bootPluginLoader` assumes the extracted plugin `lib` directory always exists; a plugin zip with no `lib` directory or no jars can trigger a null pointer.
- `Pf4bootPluginClassLoader.setPluginFirstClasses` is empty, so the configuration is read but cannot affect class loading.
- `plugin2` source references `pf4boot-api` and `plugin1` types, but its build file does not add them to the compile classpath, so `:plugin2:build` cannot pass independently.

## Affected Modules

- `pf4boot-core`: scheduled task management, zip plugin loading, and plugin class loader behavior.
- `plugin2`: compile-time dependency declarations while keeping the runtime plugin dependency declaration unchanged.

## Proposed Design

- Change `DefaultScheduledMgr.scheduledTasks` to a normal `HashMap` and keep the existing synchronized blocks for serialized access.
- Make `destroy()` copy plugin ids into a snapshot before unregistering each plugin.
- Make `ZipPf4bootPluginLoader` handle a missing or empty `lib` directory before iterating jar files.
- Convert both `pluginFirstClasses` and `pluginOnlyResources` to glob patterns in `Pf4bootPluginClassLoader`. When a class matches `pluginFirstClasses`, try `findClass` first and fall back to the default PF4J loading strategy when it is not found locally.
- Add `compileOnlyApi project(":pf4boot-api")` and `compileOnlyApi project(":plugin1")` to `plugin2`, making the source-level APIs explicit on the compile classpath. Keep `plugin project(":plugin1")` to express the runtime plugin dependency.

## Compatibility

These fixes do not change public APIs. `pluginOnlyResources` keeps the same semantics; `pluginFirstClasses` changes from ineffective configuration to effective behavior. Scheduled task unregistering now matches the original design intent more closely. The `plugin2` dependency fix only affects the compile classpath and does not change the runtime plugin dependency declaration.

## Verification

Run:

- `.\gradlew.bat :pf4boot-core:compileJava`

For broader validation, also run:

- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`

## Open Questions

This does not cover broader refactors such as repeated `pluginSupport.stopPlugin` calls in the lifecycle. This change only fixes clear stability defects.
