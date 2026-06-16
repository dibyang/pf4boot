# Plugin Hot Replacement Deployment Improvement Plan

## 1. Goal and Scope

This plan tracks the upgrade from lifecycle operations to an auditable, rollbackable, and acceptance-tested hot replacement deployment flow.

Scope:

- hot replacement deployment state machine;
- deployment plan and record;
- dependency-chain impact calculation;
- drain, stop, package replacement, start, health check, and rollback;
- resource cleanup validation;
- version compatibility and package verification;
- unified deployment service for management APIs/CLI.

Out of scope:

- distributed gateway integration;
- strict zero-downtime switching;
- JTA/XA or cross-datasource transaction migration;
- arbitrary class-level hot replacement.

## 2. Milestones

| Milestone | Goal | Deliverable | Passing Condition |
| --- | --- | --- | --- |
| M1 Design freeze | lock state machine and boundaries | improvement design and this plan | review passes, low-level lifecycle semantics unchanged |
| M2 Record and precheck | generate replacement plan without execution | `DeploymentPlan`, `DeploymentRecord`, precheck logic | impact scope and compatibility results are available |
| M3 Basic replace and rollback | safe replacement with short downtime | stop/replace/load/start/rollback orchestration | old version restored when new version fails |
| M4 Drain and cleanup validation | prevent requests/tasks entering half-unloaded plugins | web drain, task pause, cleanup checks | no web/bean/task residue after stop |
| M5 Health and observability | automatically decide replacement success | health probe, logs, metrics | health failure triggers rollback |
| M6 JPA/database constraints | add guards for stateful plugins | JPA drain, schema rules, tests | provider/consumer replacement order is controlled |
| M7 Docs and acceptance | create reviewable deployment capability | guide, acceptance records, scripts | docs, implementation, and acceptance align |

## 3. Task Breakdown

### M1 Design freeze

- [x] State that `reloadPlugin` is not safe hot replacement deployment.
- [x] Add a deployment orchestration layer without changing lifecycle contracts.
- [x] Set phase-one target to controlled short downtime and automatic rollback.
- [x] Keep hot replacement separate from cross-plugin transaction planning.
- [x] Review deployment state machine.
- [x] Review staged/backup/failed directory convention.

Review conclusion:

- Accept the main path `PLANNED -> PRECHECKED -> APPLYING -> STARTING -> VERIFYING -> SUCCEEDED`.
- Accept `FAILED` as the common failure state and `MANUAL_INTERVENTION` for rollback failure.
- In phase one, `staged/backup/failed` are internal directories owned by the deployment service and do not change the public plugin repository contract.
- Keep `reloadPlugin` as a low-level lifecycle operation; safe replacement is owned by the deployment orchestration service.

### M2 Record and Precheck

- [x] Define `DeploymentPlan`.
- [x] Define `DeploymentRecord`.
- [x] Define `DeploymentState`.
- [x] Parse staged plugin package descriptor.
- [x] Verify plugin ID, version, requires, dependency ranges, and framework version.
- [x] Calculate impact scope: target plugin and all dependents.
- [x] Save `RollbackSnapshot`.
- [x] Add precheck unit tests.

Implementation notes:

- Public models and entrypoint live in the `net.xdob.pf4boot.deployment` package in `pf4boot-api`.
- The default read-only precheck implementation lives in `DefaultPluginDeploymentService` in `pf4boot-core`.
- `pf4boot-starter` auto-configures `PluginDeploymentService` for future management API/CLI reuse.
- `planReplacement` only parses, verifies, and records the plan. It does not call `stop/load/start/unload`, satisfying AC-01.

### M3 Basic Replacement and Rollback

- [x] Add `PluginDeploymentService`.
- [x] Implement replacement flow: stop dependents -> stop target -> activate package -> load -> start target -> start dependents.
- [x] Implement rollback flow: stop new -> restore old package -> load old -> start old dependency chain.
- [x] Write `DeploymentRecord` during replacement and rollback.
- [x] Move failed replacements to `ROLLING_BACK`.
- [x] Move failed rollbacks to `MANUAL_INTERVENTION`.
- [x] Add tests for successful replacement, startup failure rollback, and package activation failure rollback.

Implementation notes:

- `PluginDeploymentService.replace(...)` reuses the M2 precheck plan first. Failed precheck does not execute lifecycle actions.
- The success path stops and unloads the impact chain by `stopOrder`, loads the staged target package and affected dependents, then starts them by `startOrder`.
- Rollback restores old package paths and original started state from `RollbackSnapshot`.
- `DeploymentRecord.stateHistory` keeps intermediate states such as `ROLLING_BACK` and `MANUAL_INTERVENTION` for audit.
- Package activation in this phase means loading the staged path. Physical `staged/backup/failed` directory archival is deferred to later package-management hardening.

### M4 Drain and Cleanup Validation

- [x] Add plugin-level web draining state.
- [x] Reject new requests or return maintenance responses while draining.
- [x] Count and wait for in-flight plugin requests to reach zero.
- [x] Pause scheduled tasks in the affected chain.
- [x] Wait for running tasks to finish.
- [x] Validate controller/interceptor/mapping cleanup after stop.
- [x] Validate shared bean, extension, and scheduled task cleanup after stop.
- [x] Validate plugin context and classloader have no obvious residue.
- [x] Add drain timeout and cleanup failure tests.

Implementation notes:

- `PluginTrafficDrainer` drains traffic before deployment stops plugins and waits for in-flight work to reach zero.
- `PluginCleanupVerifier` validates module-level resources after stop and before unload. ERROR results trigger rollback.
- `PluginRequestMappingHandlerMapping` provides web draining, 503 maintenance responses, in-flight counts, and web mapping/interceptor cleanup validation.
- `DefaultScheduledMgr` skips new scheduled executions while draining and waits for running scheduled tasks to finish.
- `DefaultShareBeanMgr` participates in deployment drain and verifies shared beans, extension beans, and scheduled tasks leave no residue.
- Classloader validation in this phase is based on plugin context unregistration plus absence of web/core resources holding plugin objects. Weak-reference or GC-level checks are deferred to later observability hardening.

### M5 Health and Observability

- [x] Define `PluginHealthProbe`.
- [x] Support plugin-local health probe beans.
- [x] Default health check includes plugin state, shared beans, web endpoints, and JPA availability.
- [x] Log deployment id, state, duration, impact scope, and error codes.
- [x] Add metrics.
- [x] Roll back automatically when health check fails.
- [x] Add health probe success/failure tests.

Implementation notes:

- `PluginHealthProbe` lives in `pf4boot-api`; plugins can expose local health-check beans from their own Spring context.
- `PluginHealthVerifier` lives in `pf4boot-api`; framework modules such as core, web, and JPA can contribute their own resource health to the default check.
- `DefaultPluginDeploymentService` enters `VERIFYING` after the full impact chain starts, checks plugin state and startup errors, then runs module-level verifiers and plugin-local probes.
- Any ERROR or null health result triggers automatic rollback. The restored old version is also health-checked after rollback.
- `DeploymentRecord` now includes `durationMillis` and `errorCode`, and keeps `VERIFYING` in `stateHistory`.
- `DefaultPluginDeploymentRecorder` provides first-phase in-memory records and metric snapshots.
- Optional `pf4boot-actuator` exports deployment total, rollback total, failed total, and last deployment duration metrics.
- `DefaultShareBeanMgr` and `PluginRequestMappingHandlerMapping` now contribute module-level health. JPA availability verifier is implemented in M6 together with database constraints.

### M6 JPA and Database Constraints

- [x] Stop all dependent consumers before replacing a JPA domain provider.
- [x] Wait for current transactions to complete or fail by timeout.
- [x] Check DataSource/EMF/TM are closed or unregistered from platform context.
- [x] Document that production schema migration must not rely on `ddl-auto=update`.
- [x] Provide expand/contract schema migration templates.
- [x] Add JPA provider replacement order and failure rollback tests.

Implementation notes:

- A JPA domain provider is still a normal plugin. Hot replacement uses the M2/M3 dependency graph: stop order is all dependent consumers -> provider, and start order is provider -> consumers.
- `PluginTrafficDrainer` is the common transaction-wait extension point. In phase one, web and scheduled-task draining block new business entrypoints and wait for in-flight work to reach zero; JPA local transactions do not get a separate state machine to avoid duplicating Spring transaction interception.
- `pf4boot-jpa-starter` adds `JpaPluginDeploymentVerifier`. When the host includes the JPA starter, it automatically participates in the deployment service. After startup it checks `DataSource`, `EntityManagerFactory`, and `PlatformTransactionManager` in the plugin context; after stop it verifies these JPA resources do not remain.
- The domain datasource capability plugin is still owned by `pf4boot-jpa-domain-starter`, which creates and exports one transaction domain. Consumers depending on it share the same transaction environment. Multiple datasources use multiple domain plugins; cross-datasource transactions remain unsupported.
- Production must not use `spring.jpa.hibernate.ddl-auto=update` as a schema migration mechanism. Package precheck and health checks verify runtime resources only; they do not create tables, alter tables, or backfill data.
- Plugins with schema changes must use expand/contract: publish a structure compatible with old code, publish the new plugin that uses the new structure, and clean old structures only after rollback is no longer needed.

Expand/contract template:

| Phase | Database Action | Plugin Action | Rollback Requirement |
| --- | --- | --- | --- |
| Expand | add nullable columns, new tables, new indexes, or compatible views; do not remove old fields | old plugin keeps running | old plugin can ignore the new structure |
| Backfill | backfill new fields or tables in an idempotent and repeatable process | old plugin keeps running or gradually reads new structures | failed backfill can be rerun without affecting old reads/writes |
| Switch | hot replace providers/consumers and start reading/writing new structures | use `PluginDeploymentService.replace(...)` | health failure rolls back to old plugins; old structures remain usable |
| Contract | remove old columns, tables, indexes, or views | execute only after all instances are stable on the new version | old plugin rollback is no longer guaranteed after Contract |

Constraints:

- Provider replacement guarantees consistency only within a single datasource transaction domain. If one plugin depends on multiple datasources, entities and repositories must be grouped by datasource package, and each datasource must have explicit scan paths in the plugin.
- Schema migration should be executed by operations scripts, Flyway/Liquibase, or a host-defined migration flow. In phase one, pf4boot provides replacement orchestration, drain, health checks, and cleanup validation only.
- Do not run Contract operations during the rollback window if they break compatibility with the old version.

### M7 Docs and Acceptance Closure

- [x] Update `plugin-lifecycle.md` with lifecycle vs deployment orchestration.
- [x] Update `plugin-loading-and-packaging.md` with staged/backup/failed package management.
- [x] Update `context-and-bean-sharing.md` with hot replacement cleanup validation.
- [x] Update `web-integration.md` with draining and mapping removal.
- [x] Add hot replacement acceptance document.
- [x] Sync English translations.
- [x] Add sample commands and operations script notes.

Implementation notes:

- `plugin-lifecycle.md` states that `reloadPlugin`, `restartPlugin`, and `upgradePlugin` are lifecycle primitives, and release-grade safe hot replacement should use `PluginDeploymentService.replace(...)`.
- `plugin-loading-and-packaging.md` states that phase one does not change the public plugin repository format. `staged/backup/failed` are deployment-flow conventions, and physical archival is owned by operations.
- `context-and-bean-sharing.md` documents `DefaultShareBeanMgr` responsibilities in drain, cleanup verifier, and health verifier phases.
- `web-integration.md` documents web drain 503 responses, in-flight counts, and mapping/interceptor residue checks.
- `plugin-hot-replacement-deployment-acceptance.md` is the standalone acceptance record covering AC-01 through AC-12 evidence, recommended verification commands, and manual smoke script.

## 4. Acceptance Checklist

| ID | Item | Passing Standard |
| --- | --- | --- |
| AC-01 | precheck does not mutate runtime | only plan and record are generated |
| AC-02 | impact scope is accurate | dependents list matches PF4J dependency graph |
| AC-03 | stop order is correct | dependents stop before target |
| AC-04 | start order is correct | target starts before dependents |
| AC-05 | failed new package rolls back | load/start/health failures restore old version |
| AC-06 | rollback failure is diagnosable | state moves to manual intervention with records |
| AC-07 | web drain works | new requests do not enter target plugin after draining |
| AC-08 | in-flight requests can drain | zero before timeout continues, timeout rolls back |
| AC-09 | cleanup is verifiable | mappings/beans/tasks leave no residue after stop |
| AC-10 | JPA provider order is safe | consumers stop before provider replacement, restart in reverse order, and JPA resource health failure can roll back |
| AC-11 | health check is extensible | custom plugin health probe participates in decision |
| AC-12 | old APIs remain compatible | start/stop/reload/delete behavior unchanged |

## 5. Recommended Verification Commands

Lifecycle and core compilation:

```powershell
.\gradlew.bat :pf4boot-core:compileJava :pf4boot-starter:compileJava
```

Web cleanup:

```powershell
.\gradlew.bat :pf4boot-web-starter:compileJava :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

JPA plugins:

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test
```

Plugin package verification:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

Manual smoke:

```text
1. Start the sample demo host.
2. Access plugin controller and verify old version works.
3. Submit staged new plugin package.
4. Run plan and verify impact scope.
5. Run replace and observe drain/stop/replace/start/health.
6. Access plugin controller and verify new version works.
7. Repeat replacement with a faulty package and verify automatic rollback.
```

## 6. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| in-flight requests cannot be counted accurately | replacement happens while old plugin is still serving | first remove web mappings plus timeout wait, then improve exact counting |
| classloader leaks are hard to prove automatically | long-running memory leak | start with observability and weak-reference checks; do not rely on GC result alone |
| schema-incompatible rollback | old version cannot start | database plugins must use expand/contract and precheck |
| package switch is not atomic | repository sees partial package | verify staged package first, then move to active; restore backup on failure |
| health check false result | wrong rollback or wrong success | support default checks plus custom probes with timeout/retry configuration |

## 7. Open Question Recommendations

### Q1: Where should deployment records be stored?

Recommendation: use local JSON files or a lightweight persistent directory under the host deployment directory in the first phase. Later evaluate database or management-plane storage.

### Q2: Should existing `reloadPlugin` be changed?

Recommendation: no. Keep `reloadPlugin` as a low-level operation and add `PluginDeploymentService.replace(...)` as the safe replacement entrypoint.

### Q3: Should every plugin provide a health probe?

Recommendation: no. Use default checks when custom probes are absent. Production plugins should provide probes.

### Q4: Should phase one provide zero downtime?

Recommendation: no. First implement controlled short downtime, automatic rollback, and observability. Evaluate zero downtime after state machine and cleanup validation stabilize.

### Q5: Should staged/backup/failed change the plugin repository immediately?

Recommendation: manage staged/backup/failed internally in the deployment service first. Promote it to a public repository convention after it stabilizes.

## 8. Status

- Plan start date: 2026-06-11
- Current status: M7 complete, phase one closed
- Owner: Codex
- Blockers: none
