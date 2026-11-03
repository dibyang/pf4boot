# Plugin Stop and Failure Cleanup

[中文](../plugin-resource-cleanup.md)

## Fourth Follow-up Design

- Abort unload on stop/drain failure without removing the plugin or closing its loader. Dependent unload failure also aborts provider unload.
- Keep fallback extension construction and singleton registration unchanged. Register destruction through the existing PluginListableBeanFactory observation and retry path, without adding injection or initialization callbacks.
- Use the existing workTracker monitor for RequestWork continuation selection, admission, worker exit and completion. Never append counts to released work; add no new state machine, configuration or dependency.
- Upgrade compatibility: public APIs, defaults, descriptors and persisted formats remain unchanged. Failed unload now reports failure instead of false success, and previously omitted extension destruction runs. Dependencies on unsafe forced unload or missing destruction are not preserved. Rollback requires successful stop and cannot bypass cleanup failure.
- Verify blocked-work/destruction-failure unload retention and recovery, dependent failure, extension destruction/retry and async continuation races/admission rejection. Two actual zip plugins exercise cross-plugin ASYNC dispatch, blocked unload and extension port release/recovery. Finish with a full build, both smoke profiles and both unchanged old-binary upgrade profiles. Status: locally verified; see fourth follow-up acceptance below.

## Third Follow-up Design

- Ordinary forward/include dispatches acquire independent work counts. Inner completion releases only inner work. Async continuation reuses admitted work and acquires admission for newly involved plugins. Each dispatch interceptor owns its work reference; synchronous inner completion restores the outer request attribute.
- Async callbacks use the work object as their key so nested dispatches do not overwrite each other. Keep waiting for actual Callable exit after response completion.
- Direct plugin instances get a Spring RootBeanDefinition and existing destruction processors register externally managed methods before building the standard adapter. Reuse Spring annotation/DisposableBean deduplication, with inferred close disabled to preserve direct-instance destruction selection.
- Verify dispatch accounting, nested completion, async continuation and duplicate destruction. Real Tomcat acceptance uses two actual zip plugins for forward/include, blocked inner/outer work and exactly-once plugin destruction followed by restart. Status: locally verified; see third follow-up acceptance below.

### Upgrade Compatibility

- Public method signatures, configuration defaults, management JSON/error codes, plugin descriptors, trust manifests and persisted formats remain unchanged. Existing plugins require neither recompilation nor data migration.
- Ordinary synchronous requests and admitted async continuations keep their accounting. Nested dispatch counts now reflect multiple active work scopes, including values greater than one for the same plugin; treat these as in-flight work, not distinct HTTP requests.
- One method serving both PreDestroy and DisposableBean runs once. Dependencies on the previous erroneous duplicate call are not preserved. Direct instances do not gain inferred close callbacks; normal Spring bean selection and cleanup retry semantics remain unchanged.
- Preserve pre-fix 81526a7 plugin zips and sidecars with SHA-256 checksums, then run them unchanged on the repaired framework through business checks, restart and reload. New probe fixtures do not replace old-binary compatibility acceptance.

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

## Third Follow-up Acceptance

2026-11-03, Windows / Temurin JDK 17 / `--release 8`. Use the complete build and both profile smoke tasks shown above. Status: locally verified, no artifact release performed.

- Framework JUnit XML totals 321 tests with zero failures, errors or skips. Seven new regressions cover exactly-once direct plugin destruction, cross-plugin forward, outer work surviving inner completion, include failure, same-plugin nesting and two async continuation cases. Another upgrade regression ensures direct instances do not gain inferred close callbacks.
- Both ordinary and production Tomcat/zip-plugin runs pass forward/include. Blocked inner work prevents stopping either plugin. After inner completion, outer work still prevents its plugin from stopping while the inner plugin can restart independently. After outer completion, plugin destruction runs once, its port can be rebound and restart succeeds. Reports include `dispatchBoundaries=PASSED`.
- Both smoke profiles retain passing async drain, destruction failure recovery, 20 restarts, 10 reloads, collection of ten old loaders, JPA, transactions, persistence, isolation and Actuator checks.
- Four actual pre-fix `81526a7` plugin zips/sidecars run unchanged on the repaired framework, without recompilation. Ordinary and production profiles each pass business requests, 20 restarts, 10 reloads, collection of ten old loaders and start/stop failure recovery. The script records the old plugin class signature and plugin/framework SHA-256 hashes in `build/reports/legacy-plugin-compatibility{,-production}/result.json`, relative to sample app-run.
- Diff and strict UTF-8 checks pass. Old-binary acceptance covers this baseline, not every historical plugin or third-party Spring extension. No Java 8 JVM execution was performed; external-resource limits still apply.

Reverification requires preserved pre-upgrade plugins, not rebuilt substitutes. This baseline resides in ignored repository `build` output and is not a release artifact:

```powershell
$baseline = "$PWD\build\compatibility-81526a7\plugins"
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -Production
```

Old zip SHA-256 values (all file versions are `3.3.0-SNAPSHOT`):

| Plugin | SHA-256 |
| --- | --- |
| plugin-demo-jpa-domain | `6F8A886667E724323515B4DE26DA31B7491BCAB535CF9E757C1B76D1CED6FF04` |
| plugin-unrelated-service | `8482FC7A6189BDF3F0C18C5CE8F83CBB262D1B4A73B40D255E0BF07A8CA58F6E` |
| plugin-user-book-service | `C6707C64839B9C7B7AA458A6542B1FAFAA8BEF9AE55514528A31A17F6382A2C8` |
| plugin-workflow | `864BC0C8C112511EFAF586739006EEB9FA045C8C19B53E3C6726D878781CC2AB` |

## Fourth Follow-up Acceptance

2026-11-03, Windows / Temurin JDK 17 / `--release 8`. Status: locally verified, no artifact release performed.

- The full build and both profile smoke tasks shown above pass. A final `.\gradlew.bat build --quiet` with all original tests retained also exits with code 0. Framework JUnit XML totals 329 tests with zero failures, errors or skips.
- Eight new regressions cover blocked unload retention/recovery, failed-cleanup unload retry, dependent failure preventing provider unload, extension destruction/port release, failed-action-only retry, no duplicate destruction for Spring-managed extensions, and two completion/admission races (normal admission and drain rejection). Direct-instance initialization remains unchanged, without new PostConstruct callbacks.
- Both ordinary and production Tomcat/zip-plugin runs reject unload during a blocked interceptor while retaining the original plugin/context, then allow unload/load/start after drain. A non-component PF4J extension owns a real port; failed destruction prevents unload/start from bypassing FAILED. Recovery closes it successfully once, permits rebinding and reuses the port in a new instance. Reports add `unloadRetention` and `extensionDestruction`.
- Each smoke profile completes 20 cross-plugin Servlet ASYNC dispatches followed by successful stop/restart of both plugins. Previous forward/include, timed-out-worker drain, destruction deduplication and close-failure recovery checks remain passing. Targeted regressions control the precise race schedule; ordinary HTTP repetition is not claimed as deterministic race reproduction.
- Both profiles subsequently pass 20 restarts, 10 reloads and collection of ten old loaders, plus JPA, rollback, persisted records, provider replacement/isolation and Actuator checks.
- The same preserved `81526a7` zips/sidecars and script commands recorded above pass business calls, 20 restarts, 10 reloads, ten collected loaders and failure recovery on the new framework in both ordinary and production profiles. Old zip SHA-256 values remain unchanged; every framework JAR hash in the reports matches the final build.
- Diff and strict UTF-8 checks pass. No Java 8 JVM execution was performed. Old-binary coverage is limited to the specified baseline, not every historical plugin. Unregistered external resources and arbitrary non-terminating user threads remain outside automatic cleanup guarantees.
