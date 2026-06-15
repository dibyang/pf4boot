# JPA Runtime Refresh Implementation Plan

## 1. Scope

This plan tracks the implementation of [jpa-runtime-refresh.md](jpa-runtime-refresh.md). Delivery is phased: visibility first, planning second, and explicit executable refresh last.

## 2. Phases

| Phase | Status | Goal |
| --- | --- | --- |
| R0 Docs and current-state alignment | Done | Complete design, plan, acceptance docs, and stale acceptance cleanup |
| R1 Public models and configuration | Done | Add reload models, SPI, and disabled-by-default configuration |
| R2 PLAN_ONLY impact analysis | Done | Output provider, consumers, unrelated plugins, orders, and blockers |
| R3 Binding registry and exact consumer detection | Done | Register shared JPA bindings on plugin start and remove them on stop |
| R4 Management APIs and Actuator visibility | Done | Expose plan and record queries without enabling mutation by default |
| R5 Executable mode | Done | Stop consumers and restart the provider for one domain |
| R6 Runtime smoke and sample extension | Done | End-to-end plan, success, failure isolation, and reports |
| R7 Docs and acceptance closure | Done | Update guides, API docs, translations, and acceptance evidence |

## 2.1 V1 Boundary

V1 is restart-based and split into:

- `V1-Plan`: models, configuration, binding registry, `PLAN_ONLY`, management plan endpoint, and Actuator summary.
- `V1-Execute`: explicit provider restart based refresh, consumer stop/start, records, and runtime smoke.

V1 does not implement provider-internal EMF rebuild, provider package replacement, multi-domain atomic refresh, persistent reload records, or zero-downtime production guarantees.

## 3. R1 Public Models and Configuration

Affected modules:

- `pf4boot-jpa`
- `pf4boot-jpa-starter`
- `pf4boot-actuator`

Tasks:

- Add `net.xdob.pf4boot.jpa.reload`.
- Add reload mode, state, failure code, request, plan, record, consumer, drain report, service, and plan service models.
- Add request validation for empty domain, missing idempotency key on execute, long reason, and unsupported `providerReplacementPath`.
- Add `pf4boot.plugin.jpa.domain-reload.*` configuration.
- Keep `DISABLED` as the default.
- Verify Java 8 compilation.

Verification:

```powershell
.\gradlew.bat :pf4boot-jpa:compileJava :pf4boot-jpa-starter:compileJava
```

## 4. R2 PLAN_ONLY Impact Analysis

Tasks:

- Read `domain.{domainId}.descriptor` from the platform context.
- Validate descriptor readiness and provider state.
- Return stable blocker codes such as `DOMAIN_NOT_FOUND`, `DOMAIN_NOT_READY`, and `PROVIDER_NOT_RUNNING`.
- Use the PF4J dependency graph to find direct and transitive provider dependents.
- Use the binding registry for exact shared consumers.
- Mark dependency-only candidates as `INFERRED_DEPENDENCY`.
- Calculate stop and start order.
- Output unrelated plugins, warnings, and blockers.
- Keep all lists stably sorted.
- Add focused tests.

Verification:

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test
```

## 5. R3 Binding Registry

Tasks:

- Add `JpaPluginBindingRegistry`.
- Make it thread-safe and expose register, remove, find, and snapshot operations.
- Register `JpaPluginBinding` after successful shared-mode binding.
- Remove bindings when the plugin stops or its context closes.
- Prefer exact registry matches over dependency-graph inference.
- Exclude local-mode plugins and other domains.

Verification:

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-core:test
```

## 6. R4 Management and Actuator

Tasks:

- Register JPA reload controllers only when `JpaDomainReloadService` exists.
- Add the plan endpoint.
- Add the execute endpoint, but keep it non-mutating until execution mode is enabled.
- Add reload record query endpoints.
- Add current-domain reload query.
- Keep execution disabled unless the configured mode allows it.
- Add read-only Actuator summary.
- Reuse existing management security, audit, and idempotency rules.

Verification:

```powershell
.\gradlew.bat :pf4boot-management-starter:test :pf4boot-actuator:test
```

## 7. R5 Executable Refresh

Tasks:

- Add per-domain reload lock.
- Keep reloads globally serialized in V1.
- Add in-memory record repository with ring buffer and idempotency mapping.
- Reuse idempotency handling.
- Build and validate an executable plan.
- Reject non-empty `providerReplacementPath` with `UNSUPPORTED_REPLACEMENT_PATH`.
- Drain consumers.
- Stop consumers in dependency-downstream order.
- Stop the provider.
- Verify old exported JPA beans are removed.
- Start the provider and wait for a ready descriptor.
- Start consumers in dependency-upstream order.
- Run health checks.
- Record state transitions and failure codes.

Verification:

```powershell
.\gradlew.bat :pf4boot-core:test :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-management-starter:test
```

## 8. R6 Runtime Smoke

Tasks:

- Add sample host reload configuration.
- Add `jpaReloadPlanOnly`.
- Add `jpaReloadDisabledNoMutation`.
- Add `jpaReloadSuccess`.
- Add `jpaReloadIdempotency`.
- Add failure-isolation checks proving unrelated plugins still respond.
- Write checks to `result.json` and JUnit XML.

Verification:

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

## 9. R7 Documentation Closure

Tasks:

- Update the developer guide.
- Update JPA integration docs.
- Update HTTP management API docs.
- Sync English translations.
- Update acceptance evidence.

Final verification:

```powershell
git diff --check
rg -n "U\+FFFD" docs/design docs/design/en samples/cross-plugin-jpa
.\gradlew.bat build
```

## 10. Completed Implementation Order

1. R1 public models and configuration.
2. R3 binding registry.
3. R2 `PLAN_ONLY` service.
4. R4 management plan endpoint and Actuator summary.
5. R5 record repository, locks, and execute service.
6. R6 runtime smoke and sample README.
7. R7 docs and acceptance closure.

Run the narrow module tests before each commit. After R5, runtime smoke is mandatory.

## 11. Post-V1 Evolution

1. Add a persistent `JpaDomainReloadRecordRepository` so reload history survives host restarts.
2. Add a drain SPI so business plugins can declare active requests, transactions, or background tasks.
3. Support provider package replacement and a fuller rollback strategy.
4. Evaluate provider-internal EMF rebuild only after Hibernate, Spring Data JPA proxies, and transaction boundaries are proven safe to switch.
5. Evaluate cross-domain or cross-datasource transactions separately; V1 explicitly does not support them.
