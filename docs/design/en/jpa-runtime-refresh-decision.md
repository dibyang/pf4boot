# JPA Runtime Refresh Decision

## Background

`pf4boot-jpa-starter` supports plugin-local `LOCAL` JPA and shared `SHARED` JPA domains. `pf4boot-jpa-domain-starter` lets a domain capability plugin create and export `DataSource`, `EntityManagerFactory`, `TransactionManager`, and a domain descriptor. Today, `DynamicMetadata.sync()` explicitly does not update an already-created Hibernate metamodel.

The open question is whether the framework should support runtime refresh when domain entity packages, model jars, repository bindings, or datasource configuration change. This affects class loading, transaction draining, repository proxies, connection pool release, and hot replacement boundaries.

## Goals

- Decide whether online Hibernate metamodel refresh is supported.
- Define the future granularity for rebuilding a shared JPA domain.
- Provide future interface, configuration, state, and verification guidance.
- Keep historical default behavior unchanged.

## Non-Goals

- P6 does not implement JPA runtime refresh.
- Do not add or remove entities from an already-started EMF.
- Do not promise uninterrupted JPA provider replacement.
- Do not bind the public contract to Hibernate private SPI.
- Cross-datasource transactions are covered by `cross-datasource-transaction-decision.md`.

## Current Flow

| Area | Current Behavior |
| --- | --- |
| Entity scan | `PluginJPAStarter` scans `@EntityScan`, auto-configuration packages, and plugin main class package when creating EMF |
| Shared domain | Provider plugin exports `domain.{id}.*` beans through `Pf4bootJpaDomainStarter` |
| Consumer binding | Consumer uses `mode=SHARED` and explicit `@EnableJpaRepositories` |
| Dynamic metadata | `DynamicMetadata.sync()` fails explicitly |
| Hot replacement | Provider replacement must stop related JPA consumers and drain transactions |

## Core Constraints

- Java 8, Spring Boot 2.7.x, and Hibernate 5.6.x compatibility.
- Once EMF exists, repository proxies, metamodel, transaction manager, and connection pool form one runtime unit.
- The domain provider classloader must see all entity model classes.
- Consumer repositories cannot add new entities after provider EMF creation.
- Any EMF/TM rebuild must drain transactions and stop related consumers first.

## Alternatives

| Option | Description | Pros | Cons | Decision |
| --- | --- | --- | --- | --- |
| A. Forbid refresh; restart domain plugin | Keep current boundary; restart provider and consumers after model changes | Safest, simplest, current-compatible | More operational manual work | Keep as default |
| B. Stop consumers and rebuild domain EMF/TM | Framework drains transactions, stops consumers, closes old EMF/TM, rebuilds provider domain, restarts consumers | Clear boundary, can reuse hot replacement state machine | Requires lifecycle locks, rollback, and leak checks | Recommended future candidate |
| C. Online Hibernate metamodel refresh | Try to update managed entities without stopping consumers | Theoretical minimum interruption | Private Hibernate behavior, repository/cache consistency risk | Rejected |

## Recommendation

Recommend continuing to forbid online refresh. If the framework later manages JPA refresh, only option B should be evaluated: stop related consumers and rebuild the domain EMF/TM. Online Hibernate metamodel refresh is rejected unless a stable public SPI, complete leak verification, and explicit version-binding policy exist.

## Interface And Configuration Draft

Future draft only:

```java
public interface JpaDomainReloadService {
  JpaDomainReloadPlan planReload(String domainId);

  JpaDomainReloadRecord reload(JpaDomainReloadPlan plan);

  JpaDomainReloadRecord rollback(String reloadId);
}
```

```java
public class JpaDomainReloadPlan {
  private String domainId;
  private String providerPluginId;
  private List<String> consumerPluginIds;
  private boolean transactionsDrained;
  private List<String> warnings;
}
```

```yaml
spring:
  pf4boot:
    plugin:
      jpa:
        domain-reload-mode: DISABLED # DISABLED, PLAN_ONLY, STOP_CONSUMERS_AND_REBUILD
        domain-reload-timeout: 60s
```

Default must be `DISABLED`. `PLAN_ONLY` only reports impact.

## Data Structures

| Type | Fields | Description |
| --- | --- | --- |
| `JpaDomainReloadPlan` | `domainId`, `providerPluginId`, `consumerPluginIds`, `warnings` | Rebuild plan |
| `JpaDomainReloadRecord` | `reloadId`, `state`, `startedAt`, `updatedAt`, `errorCode` | Auditable record |
| `JpaDomainDrainReport` | `activeTransactions`, `activeConnections`, `timeout` | Transaction/connection drain summary |

## State Machine

```text
PLANNED -> DRAINING -> STOPPING_CONSUMERS -> CLOSING_DOMAIN
  -> REBUILDING_DOMAIN -> STARTING_CONSUMERS -> HEALTH_CHECKING -> SUCCEEDED
Any executing state -> ROLLING_BACK -> ROLLED_BACK / MANUAL_INTERVENTION
```

Illegal transitions must be rejected and recorded.

## Sequence

1. Resolve `domainId` and read the domain descriptor.
2. Compute consumers depending on the domain.
3. Enter `DRAINING`, reject new reload requests, and wait for active transactions.
4. Stop consumers in dependency order.
5. Close old EMF/TM/DataSource and run resource diagnostics.
6. Rebuild provider domain.
7. Start consumers and run repository/transaction/HTTP smoke.
8. On failure, restore the old domain and previous consumer states.

## Error Handling

| Error | Behavior |
| --- | --- |
| Active transactions | `PLAN_ONLY` reports warning; execution waits and times out if needed |
| Consumer stop failure | Abort before rebuilding; keep old domain |
| EMF rebuild failure | Roll back old EMF/TM/DataSource |
| Resource leak | Report diagnostics; execution may enter `MANUAL_INTERVENTION` |

## Compatibility

- Default `DISABLED` changes no historical behavior.
- `mode=LOCAL` plugins are unaffected.
- Future implementation is orchestration only and does not change current `DynamicMetadata.sync()` failure semantics.

## Rollback

Future implementation must save provider version, old descriptor, old bean names, consumer start states, and health results before closing the old domain. Rollback failure enters manual intervention with evidence.

## Rollout

1. Implement `PLAN_ONLY` first.
2. Validate stop-consumers-and-rebuild in samples.
3. Enable execution only for explicitly configured domains.
4. Later evaluate integration with hot replacement orchestration.

## Verification

- `.\gradlew.bat :pf4boot-jpa-domain-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test`
- Provider rebuild failure rollback tests.
- Consumer stop/start ordering tests.
- Refresh rejection or timeout while transactions are active.
- `samples/cross-plugin-jpa` runtime smoke.

## Risks

| Risk | Mitigation |
| --- | --- |
| Hibernate metamodel inconsistency | Reject online refresh; rebuild EMF |
| Transactions not drained | Add drain state, timeout, and diagnostics |
| Classloader leaks | Reuse lifecycle cleanup reports and weak-reference helper checks |
| Provider/consumer order mistakes | Reuse PF4J dependency graph and hot replacement impact calculation |

## Entry Criteria

- P5 runtime smoke is stable.
- Lifecycle cleanup diagnostics cover shared beans, Web mappings, and JPA domain descriptors.
- Consumers depending on a `domainId` can be identified.
- Provider rebuild failure injection tests are repeatable.

## Final Decision

This topic does not enter implementation now. The framework continues to forbid online Hibernate metamodel refresh. Future implementation, if any, must follow the managed stop-consumers-and-rebuild-domain path.
