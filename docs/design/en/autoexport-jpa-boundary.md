# AutoExport And JPA Capability Boundary

## Problem

Two boundaries remain after the previous fix batches:

- The `group` declared by `@AutoExports` is not carried into runtime rules, so auto-exported platform beans can be registered into the wrong group.
- `DefaultAutoExportMgr` uses a plain `ArrayList`, which can race with iteration when plugins start, stop, or scan shared beans concurrently.
- `DynamicMetadata` exposes a runtime sync entry point, but there is no safe implementation for updating Hibernate `EntityManagerFactory` and metamodel state at runtime.

## Affected Modules

- `pf4boot-api`: add group-aware `AutoExportMgr` methods and clarify the runtime sync boundary of `DynamicMetadata`.
- `pf4boot-core`: store auto-export rules in a thread-safe collection and preserve `scope + group + type` when registering and removing rules.
- `docs/design`: document that JPA dynamic metadata does not currently support runtime sync; plugin JPA entities are still determined by startup scanning.

## Proposed Design

`AutoExportMgr` keeps the existing two-argument and single-argument methods, and adds Java 8 default methods for carrying `group`, so existing callers do not need immediate changes. The default implementation falls back to the old semantics; `DefaultAutoExportMgr` overrides the group-aware methods.

`DefaultAutoExportMgr` uses `CopyOnWriteArrayList`. Auto-export rules are few and mostly changed during plugin start/stop, so the copy cost is acceptable, while shared bean scans get stable snapshots. `getAutoExportClasses()` returns an immutable snapshot to prevent callers from mutating internal state.

`DefaultShareBeanMgr` passes the annotation `group` when registering `@AutoExports`, and removes rules by `type + scope + group`, avoiding accidental removal of rules from other plugins or groups.

`DynamicMetadata` does not implement runtime dynamic sync. `DefaultDynamicMetadata` only maintains a thread-safe set of candidate entity classes, and `sync()` throws `UnsupportedOperationException`. The effective boundary for plugin JPA entities remains the startup scan used when `PluginJPAStarter` creates the `EntityManagerFactory`.

## Compatibility

The new `AutoExportMgr` methods are default methods, so existing implementations can still compile. The old `removeAutoExportClass(Class<?>)` behavior remains available. Callers that use old APIs do not need to change, while plugins using `@AutoExports(group=...)` are now exported to the declared group.

`DynamicMetadata.sync()` changes from a no-op to an explicit failure. Code that depended on the no-op will now expose the unsupported call; this is intentional to avoid implying that the Hibernate metamodel was synchronized at runtime.

## Verification

- `.\gradlew.bat :pf4boot-api:compileJava`
- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :pf4boot-jpa:compileJava`
- `.\gradlew.bat :pf4boot-jpa-starter:compileJava`

Manual checks should cover `@AutoExports` rules for the same type in different groups and confirm they do not overwrite or remove each other. JPA plugins should still create repositories and entities from startup scanning.

## Open Questions

Future true runtime JPA sync needs a separate design for `EntityManagerFactory` rebuild or isolation, transaction boundaries, repository proxy refresh, and plugin stop cleanup.
