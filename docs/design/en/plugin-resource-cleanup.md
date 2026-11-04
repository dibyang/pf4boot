# Plugin Stop and Failure Cleanup

[中文](../plugin-resource-cleanup.md)

Actual Java 8 JVM and Linux acceptance is now complete; see [Java 8 and Linux Runtime Acceptance](java8-linux-acceptance.md) for results and limits. Earlier rounds' untested-platform notes describe their historical state.

## Twelfth Follow-up Early Failure Drain Design

- Review initiate failure before context creation followed by rollback drain timeout: no cleanup plan exists yet, so a FAILED plugin without a context is mistaken for resource-free on start/bulk stop/close. It may reinitialize, retain ports or unload its loader with resources still active.
- Before draining failed startup, insert a null entry in existing pendingCleanup to mean cleanup is owed but not yet executed, without replacing any existing failure plan. Admission/shutdown checks already use containsKey. Once drain recovers, cleanupPlugin converts that entry into its normal stepwise plan and only removes it after success.
- Change core behavior and sample fixtures only, without new production state fields, registries, public APIs, configuration or dependencies. Never clean up un-drained work. The intentional compatibility correction rejects activation/shutdown bypassing owed cleanup; recovery still uses existing stop/start/unload retries without recompiling old plugins.
- Regress real ports before context creation, restart after drain recovery, unload and host-close retention/retry. Validate actual in-flight worker threads in real zips, then the complete build, three JSON host runs and four unchanged old-binary upgrade runs against final JARs. Status: locally verified; see twelfth follow-up acceptance below.

## Eleventh Follow-up JSON Cache Cleanup Design

- An isolated plugin-owned DTO roundtrip leaves a root deserializer entry and the old loader retained after existing MVC cleanup. Clearing mapper caches makes the loader collectible. Previous Map responses did not exercise plugin DTO types.
- The Gson fallback also retains two typeTokenCache entries after cleanup. Reuse existing Map cleanup for that converter without adding a Gson dependency or changing its instance/configuration. Add real HTTP acceptance with Gson preferred, rather than validating only default Jackson.
- Extend web-starter's existing MVC cleanup to identity-deduplicate configured Jackson/Gson converter default mappers. Do not replace mappers, instantiate new host beans or change modules, mixins, subtypes or JSON settings. Hosts without Jackson do not load Jackson types. Run after plugin drain; continue across converter failures and report them explicitly.
- Prefer reflective invocation of public Jackson clearCaches. For Jackson 2.13, reuse public serializer/type cache methods and verified root-deserializer/internal-deserializer-cache field adapters, failing explicitly on incompatible fields. Add no production Jackson dependency; test dependencies reuse the host Boot JSON version.
- Jackson/Gson have no unified public plugin-type eviction API for all derived caches, so only MVC default mapper derived caches are invalidated, preserving configuration with a subsequent warmup cost. Add no ownership registry, switch or lifecycle component. Explicit plugin module/mixin/subtype/per-type mapper configuration and self-managed mappers must be removed by their registrants.
- Verify emptied converter caches, preserved mapper identity/settings, multiple mappers and failure retry, Jackson 2.13 and no-Jackson paths. The compatibility init script only adjusts test runtime classpaths, not published dependencies or host configuration. Exercise real zip DTO calls before repeated reload/GC, then run the complete build, both smoke profiles and four unchanged old-binary runs. Public APIs, configuration, packaging and old plugin binaries stay unchanged. Status: locally verified; see eleventh follow-up acceptance below.

## Tenth Follow-up Pre-refresh Cleanup Design

- Check failure after plugin context creation but before refresh: Spring close does not destroy singletons in an inactive context, potentially losing manually registered plugin/resource destruction callbacks and leaving ports occupied on retry.
- Only add beanFactory.destroySingletons to freeContext's existing inactive branch, after retrying previous destruction failures and before normal close/verification. Successfully destroyed callbacks are not repeated. Add no cleanup registry or state.
- Scope: API context release, core lifecycle regressions and sample acceptance fixtures. Normal refresh/stop order, public signatures, configuration, dependencies and plugin format stay unchanged. Early registered destruction callbacks now execute according to ownership; failures remain explicit and recover through existing stop/start retry.
- Verify real ServerSocket rebinding after pre-refresh failure, repeated failure/start recovery and failed destruction preventing unsafe restart while retrying only failed actions. Then run the complete build, both zip/Tomcat profiles and four unchanged old-binary runs. Status: locally verified; see tenth follow-up acceptance below.

## Ninth Follow-up Scheduling Cleanup Design

- With a shared TaskScheduler, annotation processing submits fixed-delay/rate work before later conflicting attributes fail validation, losing handles before rollback. Attach each bean's existing task set to its group before scheduling so every returned handle is owned immediately. Preserve annotation validation order, errors and scheduler ownership.
- A dequeued wrapper may survive cancel(false) before entering work accounting. Admission by pluginId alone then invokes a destroyed bean after restart. Check existing task-group object identity and acquire work under the same workTracker lock; retired groups cannot use a new lifecycle's admission.
- Use ConcurrentHashMap for the group index while retaining registration/unregistration synchronization. Workers do not wait on the registration lock. Add no generation numbers, state registry, configuration or public API, and do not rewrite Spring annotation parsing.
- Core regressions cover both conflicts, repeated failure/external-work preservation, and stale work after restart/unload. Real zip/Tomcat acceptance uses a shared scheduler with bounded ordering barriers, followed by a full build, both smoke profiles and four unchanged old-binary runs. Normal schedules/defaults/APIs/binaries are unchanged; only leaked failed-start work and retired-instance execution are corrected. Status: locally verified; see ninth follow-up acceptance below.

## Eighth Follow-up Manager Shutdown Design

- Review reproduced two manager.close gaps: platform ContextClosed callbacks can restart stopped plugins and retain live contexts/ports; bulk-stop drain timeout is logged but close still destroys parent contexts and reports success.
- Hold existing stateLock throughout close. Add one shutdownStarted admission flag, cancel auto-start first, then stop plugins. Report failure and retain root/platform while running instances, pending cleanup or failed contexts remain. A later close retries cleanup; close after root release is a no-op.
- Once shutdown starts, reject new load/start/restart/reload/upgrade and new platform contexts; allow existing-context access and stop/unload cleanup. Existing-context reads do not acquire the lifecycle lock, so draining work can finish. First-time context creation is activation: prepare it before running work rather than synchronously waiting for it from work that must drain. Recheck the flag after scheduled tasks acquire the lock. Reject recursive close from activation callbacks, reusing existing starting with nested-state restoration so an in-progress start cannot escape the stop snapshot.
- Signatures, defaults and plugin formats remain unchanged. Intentional corrections: close failure is explicit and a manager beginning shutdown cannot be reactivated; construct a new manager instead. The framework cannot keep arbitrary external host dependencies alive or force-terminate user threads if the host continues destroying them.
- Core regressions cover close callbacks, drain/cleanup retry, idempotence, concurrent admission, activation callbacks and dequeued auto-start tasks. Both real-host profiles run shutdown boundaries only after all business acceptance; unchanged old-binary acceptance remains required. Status: locally verified; see eighth follow-up acceptance below.

## Seventh Follow-up Cleanup Reentry Design

- Review reproduced recursive stop/start from PreStop: an executing pendingCleanup object is incorrectly retried as a failed plan, allowing premature stop completion and a replacement context that closes while remaining globally registered.
- Track active run depth inside existing PluginCleanup. Reject retry while any action is executing without clearing failures or running pending retries. Nested run remains valid, and failed-action retry remains unchanged after actions exit. Add no lifecycle registry or public methods.
- API/core regressions cover active cleanup protection, nested actions, normal-stop callback reentry, failed-start cleanup reentry and recovery. Real zip/Tomcat checks verify rejection of restart during cleanup, context/global-registration removal, port release and subsequent restart.
- Compatibility: signatures, defaults, binary and persisted formats remain unchanged. Only unsafe recursive cleanup retry is rejected. Call restart after stop completes rather than synchronously inside cleanup callbacks. Unhandled rejection is reported through existing failure handling; corrected callbacks permit retry. Status: locally verified; see seventh follow-up acceptance below.

## Sixth Follow-up Design

- Collect UNLOADED events locally and notify in dependency order only after the batch resource phase and dependency-graph refresh finish. Reuse PluginCleanup; no listener can restart a pending provider midway through batch unload.
- Route IOException, RuntimeException and Error from loader close through the existing FAILED/unresolved/retry protection. Retain the wrapper/loader and prohibit start on a partly closed loader; recover through unload retry and fresh load without a new state registry.
- Record extension names with their instance identities and reuse shared-bean ownership checks. If REPLACE changes ownership, discard only the old registration; the old plugin context still destroys its own original extension.
- Compatibility: public APIs, binary formats and defaults remain unchanged. UNLOADED callbacks occur at batch completion and cannot assume other affected plugins still exist. All close failures block unsafe restart; stopping an old plugin no longer reverses REPLACE. Add no runtime switches or dependencies.
- Regress notification reentry with real ports, unchecked close failures/errors and retry, extension replacement and registration rollback. Both real-zip smoke profiles inject partial close failure through the existing custom-loader setting; unchanged old-binary upgrade acceptance continues using the default loader. Status: locally verified; see sixth follow-up acceptance below.

## Fifth Follow-up Unload Design

- Hold the existing stateLock for the entire unload, serialized with start/stop/reload. Collect a deduplicated dependents-first order, drain the entire scope before any mutation, stop all affected plugins, then close loaders and remove records from leaves to providers.
- Aggregate unload notification failures with existing PluginCleanup so listeners cannot skip remaining unloads or dependency-graph refresh. Notify only after loader close and record removal. Report listener failure without repeating completed destruction.
- On loader close failure, retain the wrapper/loader, mark FAILED and remove it from resolved plugins to prevent restart on a possibly partly closed loader. Retry unload before loading a replacement; still refresh the dependency graph. Add no cleanup registry or public API.
- Compatibility: signatures, defaults and plugin formats remain unchanged. Unload ordering is corrected and concurrent start cannot cross the unload critical section. UNLOADED now means the loader is closed; listeners must not rely on a usable loader at that point. Notification failures remain explicit without rolling back completed cleanup. Close failure no longer permits unsafe direct restart.
- Test three-level/diamond dependencies, blocked-leaf no-mutation, concurrent start, single/multiple notification failures and loader-close retry. Real zips exercise dependencies and concurrency/listener boundaries; both smoke profiles and unchanged old-binary upgrade acceptance must pass. Status: locally verified; see fifth follow-up acceptance below.
- Upgrade acceptance additionally reproduced one retained old loader with actual `3a43ec2` zips. The isolated heap shows live context -> parentBeanFactory -> mergedBeanDefinitions -> RootBeanDefinition.resolvedTargetType -> old CleanupProbeExtension. Spring 5.3 can mark removed merged definitions stale without releasing their references. Evict only the removed dynamic bean's merged definition; retain unrelated entries and fail explicitly on unsupported Spring metadata rather than clearing global caches.

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

## Fifth Follow-up Acceptance

2026-11-03, Windows / Temurin JDK 17 / `--release 8`. Status: locally verified, no artifact release performed.

- The final full `build`, `runtimeSmoke` and `runtimeSmokeProduction` command shown above exits with code 0. Framework JUnit XML totals 335 tests with zero failures, errors or skips. Six new regressions cover blocked three-level drain preservation/recovery, diamond ordering/deduplication, concurrent start serialization, multi-plugin notification failures with close/graph refresh, loader-close failure blocking restart with unload retry, and selective stale merged-definition eviction.
- Both Tomcat profiles pass `unloadBoundaries`: blocked workflow work preserves the domain/service/workflow instances and loaders. After drain, unload follows workflow -> service -> domain; loading and starting the chain restores business calls and rollback. Workflow also directly depends on the domain, exercising shared-dependency deduplication.
- Two concurrent callers verify that start waits for complete unload and cannot restart the removed instance on its old loader; a freshly loaded instance starts. A throwing UNLOADED listener cannot skip loader/context/global-registration cleanup. Repeated unload does not repeat destruction, and loading/starting a replacement succeeds.
- Both profiles retain passing async/forward/extension-port boundaries, 20 restarts, 10 reloads, collection of ten old loaders, JPA reload, persistence, provider replacement/isolation and Actuator checks after the new scenarios.
- Preserved actual `81526a7` and pre-fix `3a43ec2` zips/sidecars run without recompilation. Each baseline passes business requests, 20 restarts, 10 reloads, collection of ten old loaders and start/stop failure recovery in both ordinary and production profiles. Reports are `legacy-plugin-compatibility{,-production}/result.json` and `legacy-plugin-compatibility{,-production}-3a43ec2/result.json`, relative to sample app-run `build/reports`.
- Initial and isolated `3a43ec2` runs retained one old loader. An isolated heap identified the platform merged-definition strong reference described above. After selective cleanup, all acceptance reruns pass with the original GC count and criteria. Warmed smoke that did not observe the initial loader is not substituted for cold-start upgrade acceptance. Temporary heap diagnostics, probes and dumps remain only in ignored build output, outside the commit.
- Old zip SHA-256 values are unchanged and every reported framework JAR hash matches the final build. Diff and strict UTF-8 checks pass. No Java 8 JVM, other operating-system or exhaustive historical-plugin acceptance was performed; external-resource limits still apply.

Additional baseline commands (the earlier `81526a7` commands remain valid):

```powershell
$baseline = "$PWD\build\compatibility-3a43ec2\plugins"
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -BaselineRevision 3a43ec2
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -BaselineRevision 3a43ec2 -Production
```

`3a43ec2` old zip SHA-256 values (version `3.3.0-SNAPSHOT`):

| Plugin | SHA-256 |
| --- | --- |
| plugin-demo-jpa-domain | `90B16514393F3A2BF975645C53999D808C15CBF2A82E047062C73F4167488B6A` |
| plugin-unrelated-service | `0C7CF4D3CF5B9A9C7F3EDD4FBA790DC156EBD061FDB946C7D58A4475730C3340` |
| plugin-user-book-service | `E672B5ED88A99836687D90CCF4D571259E7ACBDD15FFE49038926685D30822B2` |
| plugin-workflow | `E3B2F9393A469970F7C6F316FB3ACDA619BA74989DD30341AC52DF9D25495E77` |

## Sixth Follow-up Acceptance

2026-11-03, Windows / Temurin JDK 17 / `--release 8`. Status: locally verified; changes remain uncommitted in the working tree, without push or artifact release.

- The final full build and both profile smoke tasks shown above exit with code 0. Framework JUnit XML totals 340 tests with zero failures, errors or skips. Five new regressions cover batch-notification reentry/port release, RuntimeException and Error after partial close, preserving replaced extensions, and extension registration rollback/recovery.
- Both real zip/Tomcat smoke profiles configure `SampleUnloadFailureLoader` through the existing custom-loader option. It reuses production zip loading and injects failure after actual loader close only when the smoke property is set. Production defaults remain unchanged. Failure retains FAILED, blocks context creation on the old loader, and allows unload retry/fresh load. The four old-binary runs below exercise the default loader.
- Before each batch notification, all domain/service/workflow records and contexts are removed and all loaders closed. Reentrant start(domain) receives PluginNotFoundException rather than creating an orphan context. Dependency ordering, concurrent-start waiting and cleanup despite listener failure remain passing.
- With REPLACE, stopping the former extension owner preserves the host replacement while releasing the original extension port. After the probe removes its own replacement, the original plugin restarts. Replacement cleanup is not assigned to its former owner.
- Both profiles subsequently pass 20 restarts, 10 reloads, collection of ten old loaders, async/forward boundaries, rollback, JPA reload/persistence, provider replacement/isolation and Actuator checks. `unloadBoundaries` adds `reentrantStartRejected`, `uncheckedLoaderClose` and `extensionReplacement`.
- Preserved actual `41a04ab` and `81526a7` zips/sidecars run without recompilation in ordinary and production modes using the default loader. All four runs pass business calls, 20 restarts, 10 reloads, collection of ten old loaders and failure recovery. Reports are sample app-run `build/reports/legacy-plugin-compatibility{,-production}-41a04ab/result.json` and the original `81526a7` paths. Old zip hashes remain unchanged and every reported framework JAR hash matches the final build.
- Diff and strict UTF-8 checks pass. No Java 8 JVM or other operating-system execution was performed; selected old binaries do not establish compatibility with all historical versions or arbitrary unmanaged resources.

`41a04ab` baseline commands:

```powershell
$baseline = "$PWD\build\compatibility-41a04ab\plugins"
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -BaselineRevision 41a04ab
& .\samples\cross-plugin-jpa\app-run\verify-legacy-plugin-compatibility.ps1 -BaselinePlugins $baseline -BaselineRevision 41a04ab -Production
```

Old zip SHA-256 values (version `3.3.0-SNAPSHOT`):

| Plugin | SHA-256 |
| --- | --- |
| plugin-demo-jpa-domain | `05346C71758F884790049378EDF61A73FAC876AC9E68B25BBB53FB66E9C2AD7B` |
| plugin-unrelated-service | `E318D0F8C2C61989BDF6CE770E180CF5667B97CAFD3C30147FF892FFDCDF9C87` |
| plugin-user-book-service | `B468463003AF03CED87580232202A783D38214CABA6774C6EE22EAF83CA9153C` |
| plugin-workflow | `40E3D58625739BB1AD2BBD36E1F3A2A313EB6F48A72EDF77E90FBB846D2CCB88` |

## Seventh Follow-up Acceptance

2026-11-03, Windows / Temurin JDK 17 / `--release 8`. Status: locally verified; existing changes remain in the working tree without commit, push or release.

- The final complete build and both profile smoke tasks shown above exit with code 0. Framework JUnit XML totals 344 tests with zero failures, errors or skips. Four new regressions cover active retry preserving failures, outer protection through nested run, normal-stop callback reentry, and failed-start cleanup reentry/recovery after callback correction.
- Before the fix, the isolated reproducer returned STOPPED with no current context but retained a closed replacement context globally. After the fix, the same unhandled rejection causes explicit stop failure without creating a replacement context or retaining global registration. The handled-rejection regression stops normally and destroys resources once. Removing the problematic callback permits cleanup retry and start.
- Both real zip/Tomcat profiles add `cleanupReentry=PASSED`: restart from PreStop is rejected, outer stop clears the context and global registration, the original extension port can be rebound, and subsequent start/business calls succeed.
- Both profiles retain passing sixth-round boundaries, 20 restarts, 10 reloads, collection of ten old loaders, async/forward behavior, transactions, JPA reload/persistence, provider replacement/isolation and Actuator checks.
- Final-code reruns of the preserved `41a04ab` and `81526a7` zips/sidecars pass ordinary and production default-loader acceptance: business calls, 20 restarts, 10 reloads, ten collected loaders and start/stop failure recovery. No old binary is rebuilt. Script commands, report paths and old zip hashes are unchanged; every reported framework JAR hash matches the final build.
- Diff and strict UTF-8 checks pass. This round fixes one newly reproduced cleanup-reentry omission without new public methods, configuration or lifecycle components. No Java 8 JVM, other operating-system or exhaustive unmanaged-resource acceptance was performed; arbitrary plugin behavior is not claimed to be fully covered.

## Eighth Follow-up Acceptance

2026-11-03, Windows / Temurin JDK 17 / `--release 8`. Status: locally verified; working-tree changes remain uncommitted without push or release.

- The final complete build and both profile smoke tasks shown above exit with code 0. Framework JUnit XML totals 351 tests with zero failures, errors or skips. Seven new regressions cover close callbacks/idempotence, drain failure retaining parents until retry, cleanup retry without duplicate destruction, concurrent start rejection, activation callback close rejection/nested restoration, dequeued auto-start tasks doing nothing after shutdown, and draining work reading its existing platform context.
- A final deterministic test first reproduced existing-context reads waiting on the lifecycle lock and blocking close drain. Restoring lock-free reads of existing contexts makes it pass while new-context activation remains locked and admission-checked. The complete build, both smoke profiles and all four old-binary runs were repeated against the final JARs.
- Before the fix, ContextClosed could start a plugin, leaving live context/global registration and an open ServerSocket after close returned. With blocked work, close reported success despite destroying its active plugin's parent context. The same probes now show stopped state without context/port retention, or explicit failure with both plugin and parents retained until work exits and close is retried.
- Both real zip/Tomcat profiles run `managerCloseBoundaries=PASSED` only after business, JPA, Actuator and repeated-lifecycle acceptance. Blocked interceptors cause explicit close failure with root/platform retained; shutdown rejects load/start. After drain, close retry succeeds, platform-close callbacks cannot restart plugins, repeated close is harmless, global plugin contexts reach zero and the extension port can be rebound.
- All preceding boundaries, 20 restarts, 10 reloads and collection of ten old loaders remain passing. Shutdown is terminal, so subsequent checks validate rejection/resource release rather than claiming that the same closed manager can restart.
- Final-code default-loader reruns of unchanged `41a04ab` and `81526a7` zips/sidecars pass ordinary/production business calls, 20 restarts, 10 reloads, ten collected loaders and failure recovery. Existing scripts, report paths and old zip hashes are retained; reported framework JAR hashes match the final build. Diff and strict UTF-8 checks pass.
- Changes reuse the lifecycle lock, existing activation flag and one shutdown admission flag without new public methods, configuration or dependencies. No Java 8 JVM, other OS or external-site execution was performed. The framework cannot force protection of independently destroyed host dependencies, unregistered resources or non-terminating user threads.

## Ninth Follow-up Acceptance

2026-11-03, Windows / Temurin JDK 17 / `--release 8`. Status: locally verified; preceding working-tree changes are preserved without commit, push or release.

- Final `./gradlew.bat build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction --quiet` exits with code 0. Framework JUnit XML totals 355 tests with zero failures, errors or skips.
- All four new regressions failed before the fix and pass afterward. Conflicting fixedDelay/fixedRate and fixedRate/fixedRateString declarations each fail startup five times while retaining only the host control task in the shared scheduler. Dequeued stale work cannot invoke destroyed beans after restart or unload; new-lifecycle tasks still execute.
- The independent rollback probe previously retained 1, 2 and 2 queued tasks after initial failure, retry and unload; all three values are now zero. The stale-work probe previously invoked a closed bean once; it now reports zero calls and no execution after close.
- Both real zip/Tomcat profiles add `schedulingBoundaries` with `invalidStarts=5`, `schedulingRollback=PASSED`, `staleTaskAdmission=PASSED` and `hostWorkPreserved=PASSED`. Plugin stop neither shuts down the shared scheduler nor cancels host work. The test scheduler is separately cleaned up afterward.
- Both profiles retain passing cleanup, dispatch, unload and shutdown boundaries, 20 restarts, 10 reloads, ten collected loaders, transactions, JPA persistence/provider replacement and Actuator checks.
- Final-JAR default-loader runs with unchanged `41a04ab` and `81526a7` zips/sidecars pass all four ordinary/production business, 20-restart, 10-reload, ten-collected-loader and failure-recovery runs. Existing report paths and old-binary hashes apply. Framework JARs and baseline plugin ZIPs match the SHA-256 values in all four reports.
- Diff and strict UTF-8 checks pass. Changes only register existing handles earlier and check task-group object identity, without new public APIs, configuration, dependencies or generation registries. No Java 8 JVM, other OS or external-site acceptance was performed. Arbitrarily faulty custom schedulers, self-managed threads and unregistered external resources are not proven leak-free by these checks.

## Tenth Follow-up Acceptance

2026-11-03, Windows / Temurin JDK 17 / `--release 8`. Status: locally verified; preceding changes are preserved without commit, push or release.

- Both new regressions fail before the fix: pre-refresh failure skips registered destruction, leaves its real port occupied after close and bypasses failed-destruction retention. Both pass afterward. Five failed starts destroy resources and release the real port each time, followed by successful normal start/stop.
- Failed-destruction coverage retains the original inactive context/resource and rejects replacement activation. Recovery retries only failed actions, without repeating the successful control singleton's destruction; subsequent start/stop works. All existing lifecycle tests pass too. The existing test class is retained to reuse its host, wrapper and lifecycle fixtures for this single local defect.
- The final complete build and both profile smoke tasks use the ninth-round command and exit with code 0. Framework JUnit XML totals 357 tests with zero failures, errors or skips.
- Both real zip/Tomcat profiles add `preRefreshBoundaries`: `failedStarts=5`, `earlyResourceRelease=PASSED` and `destructionRetry=PASSED`. Each failure checks port rebinding and zero retained context/global registration/mappings/tasks/shared services. Failed destruction retains its occupied port and original context until recovery releases resources and starts normally.
- Both profiles retain passing cleanup, dispatch, scheduling, unload and host shutdown boundaries, 20 restarts, 10 reloads, ten collected loaders and JPA/transaction/Actuator acceptance. New fixtures use a separate smoke endpoint, preserving the original lifecycle-probe path used by old binaries.
- The final framework directly loads unchanged `41a04ab` and `81526a7` zips/sidecars. All four ordinary/production runs pass business calls, 20 restarts, 10 reloads, ten collected loaders and failure recovery. Existing report paths and baseline hashes apply; framework JARs and old plugin ZIPs match the SHA-256 values in all four reports.
- Diff and strict UTF-8 checks pass. Production changes only destroy inactive-context singletons using existing failure collection/retry, with no new public API, configuration, dependencies or cleanup state. No actual Java 8 JVM, other OS or external-site execution was performed. Plugins remain responsible for self-managed threads and unregistered resources; these checks do not guarantee all arbitrary plugins are leak-free.

## Eleventh Follow-up Acceptance

2026-11-04, Windows / Temurin JDK 17.0.20 / `--release 8`. Status: locally verified; preceding working-tree changes are preserved without commit, push or release.

- Before the fix, an isolated Jackson probe retains one root deserializer and its old loader after cleanup; afterward it retains zero entries and the loader is collectible. Gson previously retains two typeTokenCache entries before and after cleanup; it now retains zero afterward. Both paths use independently plugin-loaded DTOs rather than Maps.
- Four new regressions fail first and pass after repair. They cover Jackson roundtrip caches, mapper identity/naming settings, shared/distinct converter mappers, non-public custom converter failure reporting/continued cleanup/retry, and actual Gson converter read/write/cache eviction/configuration preservation.
- The current host uses Jackson 2.21.2 and Gson 2.12.1. The compatibility init script passes four JSON tests with Jackson 2.13.5 and twelve Gson/existing MVC tests with Jackson completely removed. It changes test runtime classpaths only. Production runtimeClasspath confirms no new Jackson dependency. Quote version properties in PowerShell:

```powershell
$init = 'pf4boot-web-starter/src/test/gradle/jackson-compatibility.init.gradle'
.\gradlew.bat :pf4boot-web-starter:test --tests '*CacheCleanerTest' -I $init '-PjacksonCompatibility=2.13.5' --offline
.\gradlew.bat :pf4boot-web-starter:test --tests '*PluginGsonCacheCleanerTest' --tests '*PluginRequestMappingHandlerMappingTest' -I $init '-PjacksonCompatibility=none' --offline
```

- After compatibility variants, the standard-dependency final `./gradlew.bat build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction --quiet` exits with code 0. Final framework JUnit XML totals 361 tests with zero failures, errors or skips.
- Both ordinary/production real zip/Tomcat runs pass 31 plugin DTO GET/POST roundtrips, 20 restarts, 10 reloads, ten collected loaders and failure recovery, reporting `jsonConverter=MappingJackson2HttpMessageConverter`. Existing cleanup, dispatch, scheduling, unload, pre-refresh failure, JPA/transaction, Actuator and terminal host shutdown boundaries remain passing.
- A complete run on port 7794 uses test-only `src/smoke/resources/application-gson.yml` and asserts the actual `GsonHttpMessageConverter`. It passes the same 31 DTO roundtrips, 20 restarts, 10 reloads/collected loaders and full smoke workflow. Its report is `samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke-gson/result.json`. To repeat:

```powershell
$r = (Join-Path $PWD 'samples/cross-plugin-jpa/app-run').Replace('\', '/')
java '-Dsample.expected-json-converter=GsonHttpMessageConverter' -cp "$r/build/classes/java/smoke" net.xdob.sample.smoke.RuntimeSmokeRunner --runtime "$r/build/runtime" --port 7794 --config-location "config/application.yml,file:$r/src/smoke/resources/application-gson.yml" --result "$r/build/reports/runtime-smoke-gson/result.json" --junit "$r/build/test-results/runtimeSmokeGson/TEST-runtime-smoke-gson.xml"
```

- Final JARs directly load unchanged `41a04ab` and `81526a7` zips/sidecars again. All four ordinary/production runs pass business calls, 20 restarts, 10 reloads, ten collected loaders and failure recovery. The original lifecycle-probe does not require the new DTO, so old binaries need no recompilation. Existing baseline/report paths apply; framework JARs and baseline ZIPs match the SHA-256 values in all four final reports.
- Diff and strict UTF-8 checks pass. The empty root extensions.idx generated by the isolated javac probe is removed. Production changes remain within the existing Web cache cleaner, with no new runtime dependencies, public APIs, host configuration properties or state components. Subsequent JSON cache warmup is an explicit tradeoff.
- No actual Java 8 JVM, other OS or external-site execution was performed. Explicit type/module/mixin/subtype registrations, long-lived ObjectReader/ObjectWriter instances, self-managed mappers/threads and unregistered resources still require owner cleanup. These checks do not guarantee arbitrary plugins/custom JSON libraries are leak-free.

## Twelfth Follow-up Acceptance

2026-11-04, Windows / Temurin JDK 17 / `--release 8`. Status: locally verified; preceding working-tree changes are preserved without commit, push or release.

- All three new regressions fail before the fix and pass afterward. They reproduce one root cause with three outcomes after pre-context failure and drain timeout: initiate runs again without cleanup; subsequent unload closes the loader without stopping its open port; manager close skips the failed plugin and closes parents. Recovery verifies cleanup before restart, resource release before unload, and failed close retaining parents until retry/idempotent completion.
- Production logic only inserts a null placeholder in the existing HashMap cleanup table before draining failed startup, preserving any actual failure plan. Null means cleanup has not run yet; containsKey means it is still owed. Existing cleanupPlugin builds its normal plan after successful drain and removes it after success. Add no fields, registries, configuration, dependencies or public APIs.
- Final `./gradlew.bat build :samples:cross-plugin-jpa:app-run:runtimeSmoke :samples:cross-plugin-jpa:app-run:runtimeSmokeProduction --quiet` exits with code 0. Final framework JUnit XML totals 364 tests with zero failures, errors or skips.
- Ordinary, production and the independent Gson real zip/Tomcat run using the eleventh-round command all pass `earlyStartupDrain`: `inFlightDrain=PASSED`, `activationRetry=PASSED`, `socketRecovery=PASSED`. The fixture opens a real port and starts in-flight work in initiate, then fails before context creation. It rejects repeated initialization/unload while work remains. After the worker exits, work counts reach zero, stop releases the port exactly once, rebinding succeeds and normal activation recovers. Finally blocks remove test workers/root callbacks/properties and restore timeouts.
- All three hosts retain 31 plugin DTO GET/POST roundtrips, 20 restarts, 10 reloads, ten collected loaders and existing cleanup, dispatch, scheduling, unload, pre-refresh failure, JPA/transaction, Actuator and terminal shutdown checks. Both default profiles use Jackson; the Gson run asserts GsonHttpMessageConverter. Existing report paths apply.
- Final JARs load unchanged `41a04ab` and `81526a7` zips/sidecars directly. All four ordinary/production runs pass business calls, 20 restarts, 10 reloads, ten collected loaders and failure recovery. Old plugins do not require new fixtures. Existing baselines/scripts/report paths apply; framework JARs and baseline ZIPs match the SHA-256 values in all four final reports.
- Diff and strict UTF-8 checks pass. The large manager/smoke classes are not split: this repair changes one bookkeeping point in the existing lifecycle critical section and adds one acceptance stage; restructuring would touch lifecycle/test ordering and broaden compatibility risk without corresponding benefit.
- No actual Java 8 JVM, other OS or external-site execution was performed. Failed drain must explicitly reject freeing live resources rather than forcibly terminating arbitrary threads; retry after work exits. Plugins must release initiate-owned resources through existing stop/release-hook callbacks. A manager already shutting down remains cleanup-only rather than reactivating. These boundaries and preceding self-managed-resource limits are unchanged.
