# JPA Runtime Refresh Design

## 1. Background

The current cross-plugin JPA design uses `pf4boot-jpa-domain-starter` to export a shared `DataSource`, `EntityManagerFactory`, `PlatformTransactionManager`, and `JpaDomainDescriptor`. Consumer plugins use `pf4boot-jpa-starter` in `SHARED` mode to join the same domain and transaction environment.

The earlier decision is documented in [decisions/jpa-runtime-refresh-decision.md](decisions/jpa-runtime-refresh-decision.md): already-created Hibernate metamodels must not be mutated online. Runtime refresh must stop affected consumers, rebuild the domain JPA environment, and then start consumers again.

## 2. Goals

1. Keep runtime refresh disabled by default.
2. Deliver `PLAN_ONLY` first, with impact analysis and no runtime mutation.
3. Refresh one JPA domain at a time.
4. Rebuild the provider JPA environment instead of mutating Hibernate metamodels.
5. Provide management APIs, Actuator read-only visibility, runtime smoke coverage, and acceptance tracking.
6. Exclude local JPA plugins, cross-datasource transactions, XA, and multi-domain atomic refresh.

## 3. Current Anchors

`Pf4bootJpaDomainStarter` creates the domain datasource, EMF, transaction manager, and `DomainJpaPlatformExporter`.

The exporter registers:

- `domain.{domainId}.dataSource`
- `domain.{domainId}.entityManagerFactory`
- `domain.{domainId}.transactionManager`
- `domain.{domainId}.descriptor`

`JpaDomainDescriptor` contains `domainId`, `providerPluginId`, entity packages, exported bean names, readiness, and creation time. These fields are the source of truth for planning and verification.

Shared consumers are bound by `PluginJPAStarter` through `JpaPluginBinding`. In `SHARED` mode it validates the descriptor, exported EMF/TM bean names, and local placeholder bean definitions used by Spring Data JPA.

## 4. Key Decisions

### V1 Positioning

V1 is a restart-based JPA domain refresh. It is not zero-downtime refresh and it does not mutate Hibernate metamodels online.

V1 has two delivery levels:

- `V1-Plan`: disabled/default behavior, `PLAN_ONLY`, binding registry, impact analysis, blockers, and read-only visibility.
- `V1-Execute`: explicit restart-based refresh after `V1-Plan` is stable.

Executable V1 is a maintenance-window capability. It does not promise transparent production refresh.

The refresh unit is a single `domainId`.

The first executable implementation restarts the provider plugin:

1. Drain and stop consumers of the domain.
2. Stop the provider plugin so exported JPA beans are unregistered.
3. Start the provider plugin so datasource, EMF, TM, and descriptor are recreated.
4. Verify the new descriptor is ready.
5. Start consumers in dependency order.
6. Run health checks and record the result.

Provider-internal EMF recreation is intentionally out of scope for the first version.

## 5. Impact Analysis

`PLAN_ONLY` must output:

- provider plugin ID;
- domain descriptor snapshot;
- exact consumers bound to the domain;
- inferred consumers that depend on the provider but cannot be proven exact;
- unaffected plugins;
- stop order;
- start order;
- drain capability and risks;
- blockers and warnings.

Execution is allowed only when all consumers are exact, or when the request explicitly allows inferred consumers.

## 6. Module Boundaries

- `pf4boot-jpa`: public JPA reload models and SPI under `net.xdob.pf4boot.jpa.reload`.
- `pf4boot-jpa-starter`: shared consumer binding registry, consumer resolver, and plan service.
- `pf4boot-jpa-domain-starter`: provider-side descriptor and exported bean verification.
- `pf4boot-core`: generic lifecycle orchestration only; no JPA dependency.
- `pf4boot-management-starter`: optional HTTP endpoints registered only when `JpaDomainReloadService` exists.
- `pf4boot-actuator`: read-only summaries.
- `samples/cross-plugin-jpa`: runtime smoke for plan, success, failure isolation, and reports.

## 7. Public API Draft

Suggested package:

- `net.xdob.pf4boot.jpa.reload`

Suggested types:

- `JpaDomainReloadMode`
- `JpaDomainReloadState`
- `JpaDomainReloadRequest`
- `JpaDomainReloadPlan`
- `JpaDomainReloadRecord`
- `JpaDomainDrainReport`
- `JpaDomainReloadService`
- `JpaDomainReloadPlanService`
- `JpaDomainReloadRecordRepository`
- `JpaDomainReloadException`
- `JpaDomainReloadFailureCode`

All Java code must remain Java 8 compatible.

V1 model requirements:

- Public API types live in `net.xdob.pf4boot.jpa.reload`.
- Starter internals live in `net.xdob.pf4boot.jpa.starter.reload`.
- Domain starter diagnostics live in `net.xdob.pf4boot.jpa.domain.starter.reload`.
- Public models should use stable fields, defensive copies for collections, and stable failure codes.

Required V1 blockers:

| Code | Meaning |
| --- | --- |
| `RELOAD_DISABLED` | Reload is disabled |
| `PLAN_ONLY_MODE` | Only planning is allowed |
| `DOMAIN_NOT_FOUND` | Domain descriptor is missing |
| `DOMAIN_NOT_READY` | Domain descriptor is not ready |
| `PROVIDER_NOT_RUNNING` | Provider plugin is missing or not running |
| `INFERRED_CONSUMER_PRESENT` | A consumer cannot be proven exact |
| `CONCURRENT_RELOAD` | Another reload is running |
| `MULTI_DOMAIN_PROVIDER_UNSUPPORTED` | Provider exposes multiple domains |
| `LIFECYCLE_OPERATION_UNAVAILABLE` | Required lifecycle operation is missing |
| `UNSUPPORTED_REPLACEMENT_PATH` | the original V1 phase did not support provider package replacement; after P2, replacement requests usually use provider replacement failure codes |

Suggested service contract:

```java
public interface JpaDomainReloadPlanService {
    JpaDomainReloadPlan plan(JpaDomainReloadRequest request);
}
```

```java
public interface JpaDomainReloadService {
    JpaDomainReloadPlan plan(JpaDomainReloadRequest request);

    JpaDomainReloadRecord reload(JpaDomainReloadRequest request);

    JpaDomainReloadRecord getRecord(String reloadId);

    JpaDomainReloadRecord getCurrent(String domainId);
}
```

V1 `reload()` must call `plan()` first and must not bypass blockers.

## 7.1 V1 Planning Algorithm

`DefaultJpaDomainReloadPlanService` must:

1. Resolve configuration and request mode.
2. Return `RELOAD_DISABLED` when disabled.
3. Lookup `domain.{domainId}.descriptor`.
4. Validate descriptor readiness.
5. Validate provider plugin state.
6. Read exact consumers from `JpaPluginBindingRegistry`.
7. Use the PF4J dependency graph for fallback candidates.
8. Mark registry matches as `EXACT_BINDING`.
9. Mark dependency-only candidates as `INFERRED_DEPENDENCY`.
10. Exclude local-mode plugins and other domains.
11. Compute downstream-first stop order.
12. Compute upstream-first start order.
13. Return stable ordering.
14. Add blockers for inferred consumers and plan-only mode.

V1 execute accepts only exact consumers.

## 7.2 V1 Execution Algorithm

Executable V1 must:

1. Require an idempotency key.
2. Acquire global and per-domain reload locks.
3. Rebuild the plan immediately before execution.
4. Reject non-executable plans.
5. Record all state transitions.
6. Run drain through `JpaDomainReloadDrainCoordinator` and the common `PluginTrafficDrainer` SPI.
7. Stop consumers in stop order only after drain is accepted.
8. Stop the provider.
9. Verify old exported descriptor, EMF, TM, and datasource beans are removed.
10. Start the provider.
11. Wait for a ready descriptor.
12. Start consumers in start order.
13. Run health checks.
14. Mark success or enter recovery/manual intervention.

The original V1 phase did not support `providerReplacementPath`; after P2, JPA reload delegates staged provider replacement to `PluginDeploymentService` and records a provider replacement summary.

### 7.3 Drain SPI Behavior

JPA reload does not define a separate drain SPI. It reuses the same `PluginTrafficDrainer` contract used by hot replacement deployment:

1. the coordinator injects all host `PluginTrafficDrainer` beans;
2. impact plugin IDs are `stopOrder + providerPluginId`, de-duplicated with stable order;
3. it calls `beginDrain(pluginIds)` for every drainer, then calls `awaitDrain(pluginIds, remainingMillis)` within one shared timeout budget;
4. begin exceptions, await exceptions, or await returning `false` make the reload fail with `DRAIN_REJECTED` or `DRAIN_TIMEOUT`, and no consumer/provider stop is attempted;
5. after an accepted drain, reload continues with the existing stop/start flow; success, failure, and exceptional cleanup all call `endDrain(pluginIds)` for begun drainers;
6. when no drainer exists, the default is compatibility mode with a warning; `require-drainer=true` turns this into `DRAIN_REJECTED`;
7. `drain-end-on-failure=true` keeps releasing drain state even when stop/start later fails, and end failures are recorded as warnings without overriding the primary failure.

`JpaDomainReloadRecord` carries the full `drainReport`. Management record queries return it naturally, while the `pf4bootjpareload` Actuator endpoint exposes only summary fields for the latest drain.

## 8. Configuration

```yaml
pf4boot:
  plugin:
    jpa:
      domain-reload:
        mode: DISABLED
        require-idempotency-key: true
        default-drain-timeout: 30s
        default-health-check-timeout: 60s
        allow-inferred-consumers: false
        max-recent-records: 100
        require-drainer: false
        drain-end-on-failure: true
```

## 9. HTTP Examples

Plan:

```http
POST /pf4boot/admin/jpa/domains/demo/reload/plan
Content-Type: application/json

{
  "reason": "preview demo domain refresh"
}
```

Execute:

```http
POST /pf4boot/admin/jpa/domains/demo/reload
Content-Type: application/json
Idempotency-Key: reload-demo-20260615-001

{
  "reason": "rebuild demo EntityManagerFactory after provider upgrade",
  "mode": "STOP_CONSUMERS_AND_REBUILD",
  "drainTimeoutMillis": 30000,
  "healthCheckTimeoutMillis": 60000
}
```

## 10. Compatibility

Existing plugins do not need code changes. The default mode is `DISABLED`, `PLAN_ONLY` is read-only, and executable refresh requires explicit configuration and a management operation.

`pf4boot-core` must not depend on JPA modules. New public types are additive.

## 10.1 V1 Test Matrix

Required unit tests:

- default `DISABLED` configuration;
- mode parsing;
- request validation;
- record repository ring buffer and idempotency;
- binding registry register/remove/snapshot;
- plan blockers;
- stable stop/start ordering.

Required integration tests:

- exact shared consumer detection;
- local-mode exclusion;
- unrelated plugin exclusion;
- provider export cleanup;
- provider restart descriptor readiness;
- disabled management endpoint behavior;
- plan endpoint response shape.

Required runtime smoke checks:

- `jpaReloadPlanOnly`;
- `jpaReloadDisabledNoMutation`;
- `jpaReloadSuccess`;
- `jpaReloadIdempotency`;
- `jpaReloadDrainSuccess`;
- `jpaReloadDrainTimeoutNoMutation`;
- `actuatorJpaReloadDrainSummary`;
- `jpaReloadProviderFailureIsolation`;
- report entries in `result.json` and JUnit XML.

## 10.2 V1 Completion Criteria

V1 is complete only when:

1. Default `DISABLED` changes no existing behavior.
2. `PLAN_ONLY` returns provider, consumers, unaffected plugins, order, warnings, and blockers.
3. Binding registry precisely tracks shared consumers and cleans up on stop.
4. Executable mode requires explicit configuration, no blockers, and an idempotency key.
5. Provider restart cleanup and descriptor readiness are tested.
6. Failure scenarios do not stop unrelated plugins.
7. Runtime smoke covers plan, success, drain success, drain-timeout no-mutation, failure isolation, and reports.
8. Management and Actuator output do not leak sensitive paths, tokens, or full stacks.

## 11. Open Decisions

| Topic | Recommendation |
| --- | --- |
| Management endpoint location | Put it in `pf4boot-management-starter` initially and conditionally register it when `JpaDomainReloadService` exists |
| Inferred consumers | Reject by default; allow only with explicit request |
| Provider-internal EMF rebuild | Do not support in the first version |
| Record persistence | Use an in-memory ring buffer first |
| Concurrent domain reloads | Keep serialized initially |
