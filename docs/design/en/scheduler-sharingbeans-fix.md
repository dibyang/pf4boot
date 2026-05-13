# Scheduler And SharingBeans Fix

## Problem

The current implementation has two stability issues:

- `Pf4bootPluginManagerImpl.setApplicationStarted(true)` registers a new automatic start periodic task every time it is called. It does not keep the `ScheduledFuture` and has no idempotency guard, so repeated calls can create multiple background tasks trying to auto-start plugins.
- `SharingBeans` uses only `beanName` as its key. If the same plugin exports beans with the same name to different `SharingScope` values or different platform groups, later records overwrite earlier ones, and plugin stop cannot unregister all exported entries.

## Affected Modules

- `pf4boot-core`: automatic start scheduling and shutdown.
- `pf4boot-api`: shared bean record structure.

## Proposed Design

### Automatic Start Scheduling

- Add a `ScheduledFuture<?> autoStartFuture` field to `Pf4bootPluginManagerImpl`.
- `setApplicationStarted(true)` only schedules the periodic task when `autoStartFuture` is missing, cancelled, or done.
- `setApplicationStarted(false)` cancels the existing auto-start task and clears the reference.
- `close()` cancels the auto-start task before shutting down the scheduler.
- The auto-start task still calls `doStartPlugins(true)`, preserving the existing retry behavior.

### Shared Bean Records

- Change the internal `SharingBeans` key to `scope + group + beanName`.
- `ROOT` and `APPLICATION` use an empty group, while `PLATFORM` uses the actual group.
- Make `SharingBean.equals/hashCode` consistent with the record key by comparing `beanName`, `scope`, and `group`.
- Keep the existing `getRootBeans()`, `getAppBeans()`, and `getPlatformBeans()` APIs unchanged.

## Compatibility

Public method signatures remain unchanged. Automatic start still runs at the existing interval, but repeated `setApplicationStarted(true)` calls no longer create duplicate tasks. Shared bean records preserve more exports and do not change existing single-scope export behavior.

## Verification

Run:

- `.\gradlew.bat :pf4boot-api:compileJava`
- `.\gradlew.bat :pf4boot-core:compileJava`
- `.\gradlew.bat :plugin1:build`
- `.\gradlew.bat :plugin2:build`

Recommended manual checks:

- trigger `setApplicationStarted(true)` multiple times and confirm only one auto-start task runs;
- create a plugin that exports same-name beans to different scopes/groups and confirm all are unregistered on stop.

## Open Questions

This change does not address lost AutoExport group values, AutoExport collection thread safety, or the empty JPA dynamic metadata implementation.
