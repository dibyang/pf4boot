# Plugin Stop and Failure Cleanup

[中文](../plugin-resource-cleanup.md)

## Problem and Scope

Stop exceptions skip cleanup, failed starts leave global registrations, ordinary stops do not drain work, and scheduling caches retain plugin classes.
Changes belong to api/core/starter/web. JPA resources remain owned by their Spring context.

## Approach

- Drain the target and its dependents before any stop mutation, using `plugin-drain-timeout-millis`. Timeout/interruption preserves runtime state and restores admission. Lifecycle operations remain serialized; workers must not synchronously wait for lifecycle operations, which would cause drain timeout.
- Share atomic admission and in-flight accounting between scheduling and Web. Nested drain scopes retain outer protection. Remove idle counters.
- Attempt every stop callback, event, release hook, shared registration cleanup, support cleanup, global context removal, context close and closed callback independently. Report STOPPED only on complete success. Failures retain FAILED and a cleanup retry marker; start must retry cleanup first.
- Roll back failed starts through the same cleanup path. Drain already running work first; on timeout retain the context until cleanup can be retried safely.
- Unregister global contexts before close. Surface close failures. Remove successful release hooks and retain failed hooks for retry.
- Remove the scheduling cache of plugin Class objects. Stop retains the original loader for restart; unload/reload closes it.
- Contexts use the original plugin loader directly. Runtime JPA restart exposed duplicate proxy definitions after the temporary delegating loader cleared CGLIB caches during stop. Remove this wrapping and reflective proxy cleanup from the lifecycle path, keeping proxy loader identity stable. Retain the public wrapper type for compatibility.
- Isolate composite support stop/release callbacks so one failure does not skip other supports.
- Remove plugin-owned keys from host MVC session/model/binder, argument resolution and exception caches. A heap dump established that unregistering routes leaves a strong reference through `RequestMappingHandlerAdapter.sessionAttributesHandlerCache`. Cleanup is scoped to the plugin loader without new runtime configuration or governance layers.

## Compatibility and Limits

Java 8, module boundaries, configuration names and normal callback order remain unchanged. Stop may now reject a drain timeout, and cleanup failure blocks direct restart.
Plugins must release custom threads, pools, ThreadLocals, drivers and external listeners through Spring destruction or release hooks. Arbitrary user callbacks cannot be safely force-terminated. Loader close is not proof of garbage collection.
Dependency soft-reference caches remain reclaimable under memory pressure. Smoke uses `-XX:SoftRefLRUPolicyMSPerMB=0` to make strong-reference retention checks repeatable; production configuration is unchanged.
Builds on JDK 9+ use `--release 8`; core explicitly declares JSR-250 annotations. Keep existing manager dependency ordering and locking, extracting only draining and failure aggregation to avoid coupling the cleanup fix to a full lifecycle refactor.

## Verification

- Lifecycle tests cover repeated restart, callback/event/support/hook failures, failed-start rollback, cleanup retry and dependency-wide drain timeout without mutation.
- Scheduling/Web tests cover blocked work, admission rejection, nested drain scopes, atomic admission, counter removal and repeated registration.
- Run real packaged-plugin runtime smoke for host/MVC/JPA/reload and failure regressions, plus a full build with explicit test result inspection.
- Record final-code commands, results and limitations; fake-loader tests do not establish real class unloading.

## Local Acceptance

2026-11-03, Windows / Temurin JDK 17, compiled with `--release 8`. Status: locally verified, not released.

```powershell
.\gradlew.bat build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction --quiet
```

- Exit code 0. JUnit XML from nine framework modules contains 303 tests with zero failures, errors or skips; tests actually ran.
- Lifecycle tests cover 50 context start/stop cycles and bean destruction; 20 consecutive failed starts leave no additional global registrations. Other checks cover stop/hook/event failures, failed-step retry, initialization failure, dependency-wide drain timeout without mutation, preserving contexts while failed-start work drains, partial registration rollback and host destruction.
- Both ordinary and production smoke run 20 real zip-plugin restarts and 10 reloads. All ten old-loader weak references clear. Injected start/stop failures release MVC/shared/task/context registrations and allow recovery.
- Existing transaction rollback, JPA reload and persisted record recovery, provider replacement, drain timeout without mutation, unrelated-plugin isolation and Actuator checks pass.
- Machine-readable reports are in sample app-run `build/reports/runtime-smoke/result.json` and `build/reports/runtime-smoke-production/result.json`, with matching JUnit XML.
- Diff whitespace and strict UTF-8/replacement-character checks pass. This acceptance did not run on a Java 8 JVM. Unmanaged external resources without registered destruction remain the plugin author's responsibility.
