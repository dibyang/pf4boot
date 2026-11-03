# Plugin Stop and Failure Cleanup

[中文](../plugin-resource-cleanup.md)

## Follow-up Boundary Fix

Review reproduced double counting on Callable redispatch, uncounted plugin interceptors on host requests, and destruction failures swallowed inside Spring. Keep existing configuration and SPIs:

- One request work lease covers its controller and plugin interceptors. Async redispatch reuses it; async completion, timeout and error release it exactly once. Drain rejects new plugin work but allows admitted async requests to finish.
- Callable work remains counted after response completion until the actual worker exits. Work not yet started is rejected after request completion. DeferredResult producer threads remain plugin-owned.
- Registered handler methods retain plugin IDs. Admission validates live ownership under the work-tracker lock, rejecting previously matched but removed handlers. Snapshot interceptors together with their ownership.
- Respect dynamic MappedInterceptor path rules. The existing management base-path bypasses plugin dynamic interceptors to avoid stop waiting for its own control request. Host interceptors remain in place.
- The existing PluginListableBeanFactory observes Spring 5.3 destruction adapters using their selected annotation, DisposableBean, AutoCloseable and custom callbacks. Record failures with PluginCleanup, attempt remaining callbacks, and report after close. Retain only failed callbacks for retry; fail explicitly on incompatible metadata.
- Register destruction for the directly registered plugin object; remove the erroneous destroyBean call passing a bean-name string as an instance.
- Failed destruction retains FAILED and the cleanup context, blocking a fresh context until failed callbacks succeed. Persistent plugin close errors cannot be force-repaired.
- The adapter targets current Spring 5.3 destruction metadata. Tests cover four standard callback kinds, inheritance, duplicate exclusion and custom processor overrides. Third-party processors swallowing their own failures and external custom-scope destruction must report failures themselves; no global log interception is used.

Verification covers real MockMvc redispatch, blocked global interceptors, port release failures and recovery, plus real zip plugins in Tomcat. Status: locally verified; follow-up acceptance results are recorded below.

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

## Initial Acceptance (Historical Baseline)

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

## Follow-up Acceptance

2026-11-03, Windows / Temurin JDK 17 / `--release 8`. The complete build and both profile smoke tasks shown above exit with code 0. Status: locally verified, no artifact release performed.

- Framework JUnit XML totals 313 tests with zero failures, errors or skips. New destruction tests cover standard callbacks, inheritance/deduplication, custom processor overrides and failed-action-only retry. Nine MVC/real-port regression tests cover the reviewed boundaries.
- Ordinary and production Tomcat smoke each complete 20 Callable/DeferredResult requests. A plugin interceptor blocked on a host route prevents stop. A timed-out response with a still-running Callable also rejects stop and preserves STARTED. Stop/start succeeds once the work exits.
- Injected ServerSocket close failure leaves the original port occupied; the framework reports FAILED and blocks a fresh start. After the close action recovers, cleanup retry succeeds and a new context binds the original port.
- After these recovery scenarios, both profiles complete 20 restarts, 10 reloads and collection of all ten old loaders. Existing JPA, transactions, idempotency, persistence, isolation and Actuator checks pass.
- Reports include `cleanupBoundaries=PASSED`; diff and UTF-8 checks pass. No Java 8 JVM execution was performed; the external-resource limitations above still apply.
