# Cross-Datasource Transaction Decision

## Background

Current cross-plugin JPA transactions support only one shared `domain-id`. A consumer plugin may depend on multiple JPA domains, but repositories must be grouped by package and explicitly bound to their own `EntityManagerFactory` and `TransactionManager`. Existing designs state that cross-domain and cross-datasource atomic transactions are not supported.

The open question is whether the framework should continue forbidding local cross-datasource transactions, or provide Saga, Outbox, or optional XA/JTA support.

## Goals

- Decide whether framework defaults support cross-datasource atomic transactions.
- Define Saga, Outbox, and XA boundaries.
- Provide capability declaration, precheck, and error guidance.
- Preserve current same-domain transaction behavior.

## Non-Goals

- P6 does not implement cross-datasource transactions.
- Do not make XA/JTA a core dependency.
- Do not implement generic business compensation semantics.
- Do not promise strong consistency across databases.

## Current Flow

| Capability | Current State |
| --- | --- |
| Same-domain transaction | Supported through one shared `JpaTransactionManager` |
| Multi-domain repositories | Supported with explicit package-level EMF/TM binding |
| Cross-domain atomic transaction | Not supported |
| Capability precheck | Can declare `jpa.datasource`, `jpa.consumer`, and package scan attributes |
| Hot replacement/JPA refresh | Provider replacement must stop related consumers and drain transactions |

## Core Constraints

- A local `JpaTransactionManager` manages one EMF.
- Multiple datasources have no shared transaction log or two-phase coordinator.
- Business consistency semantics cannot be inferred by the framework.
- Java 8 and Spring Boot 2.7.x should not be forced into an XA implementation.
- Defaults must remain compatible with same-domain transactions.

## Alternatives

| Option | Description | Pros | Cons | Decision |
| --- | --- | --- | --- | --- |
| A. Continue forbidding local cross-datasource transactions | Precheck/docs state that multiple TMs do not mean atomic commit | Clear, safe, no dependency | Business must design eventual consistency | Recommended default |
| B. Saga/TCC/compensation | Framework provides guidance or light extension points; business owns state and compensation | Good for long-running and cross-system workflows | Business complexity, not automatic | Recommended business pattern |
| C. Outbox/Inbox | Each local transaction writes data and outbox; async delivery provides eventual consistency | Auditable and practical | Not synchronous strong consistency | Recommended business pattern |
| D. Optional XA/JTA module | Transaction coordinator and XA datasources provide two-phase commit | Stronger consistency | Heavy dependency, recovery and performance complexity | Future optional evaluation only |

## Recommendation

The framework core continues to forbid local cross-datasource atomic transactions. Saga and Outbox are business-layer consistency patterns documented and optionally sampled. XA/JTA can only be evaluated as a future independent optional module and must not enter `pf4boot-core` or `pf4boot-jpa-starter` defaults.

## Interface And Configuration Draft

Precheck first, not transaction implementation:

```java
public enum CrossDatasourceTransactionPolicy {
  FORBID,
  WARN,
  ALLOW_APPLICATION_MANAGED
}
```

```java
public class TransactionCapabilityDescriptor {
  private String pluginId;
  private List<String> datasourceIds;
  private CrossDatasourceTransactionPolicy policy;
  private String consistencyPattern; // LOCAL, SAGA, OUTBOX, XA_OPTIONAL
}
```

```yaml
spring:
  pf4boot:
    transaction:
      cross-datasource-policy: FORBID # FORBID, WARN, ALLOW_APPLICATION_MANAGED
```

Default must be `FORBID`. `ALLOW_APPLICATION_MANAGED` only means the framework does not block; it does not provide atomicity.

## Capability Declaration

```json
{
  "capabilities": {
    "requires": [
      {
        "name": "jpa.datasource",
        "attributes": {
          "datasource": "orderDs",
          "transactionRole": "LOCAL"
        }
      },
      {
        "name": "jpa.datasource",
        "attributes": {
          "datasource": "billingDs",
          "transactionRole": "LOCAL"
        }
      }
    ]
  }
}
```

When a single transaction entrypoint declares or diagnoses multiple `transactionRole=LOCAL` datasources, `FORBID` must fail precheck or emit runtime diagnostics.

## State Machine

Saga/Outbox samples should use business state:

```text
RECEIVED -> LOCAL_COMMITTED -> EVENT_PUBLISHED -> CONSUMED -> COMPLETED
LOCAL_COMMITTED -> PUBLISH_FAILED -> RETRYING / MANUAL_INTERVENTION
CONSUMED -> COMPENSATING -> COMPENSATED / MANUAL_INTERVENTION
```

This is a business sample state machine, not a core transaction manager.

## Sequence

### Default Forbid Path

1. Deployment precheck reads capability manifest.
2. The plugin requires multiple datasources.
3. `FORBID` returns a clear error or warning: cross-datasource atomic transactions are unsupported.
4. The plugin may still use separate transaction methods, but cannot claim one-method atomic commit.

### Outbox Path

1. Write order data and outbox in the `orderDs` local transaction.
2. Dispatcher publishes the outbox event.
3. `billingDs` consumer processes idempotently.
4. Failure is handled by retry, dead-letter, or manual intervention.

## Error Handling

| Error | Behavior |
| --- | --- |
| One method misuses multiple local TMs | Docs/precheck warn; no atomic rollback guarantee |
| Outbox delivery failure | Business retry or manual intervention |
| Saga compensation failure | Business state enters `MANUAL_INTERVENTION` |
| XA resource unsupported | Optional module precheck fails; core unaffected |

## Compatibility

- Same-domain transactions stay unchanged.
- Multi-domain repository binding remains available.
- New policy defaults to `FORBID` and only affects future explicit precheck/diagnostics.
- No new core dependency.

## Rollback

Future policy precheck can be moved to `WARN` or disabled. Business Saga/Outbox samples must not become framework startup requirements.

## Verification

- Same-domain cross-plugin transaction success and rollback continue passing.
- Multi-datasource single-method atomic transaction fails precheck under `FORBID`.
- `WARN` emits diagnostics without changing historical startup.
- Outbox sample covers idempotent consume, duplicate delivery, and retry.
- Optional XA module, if ever added, must be independently tested.

## Risks

| Risk | Mitigation |
| --- | --- |
| Users assume multiple TMs are atomic | Docs, error codes, and capability diagnostics repeat the boundary |
| Saga/Outbox is mistaken for generic framework implementation | Mark it as business pattern |
| XA dependencies pollute core | XA only as independent optional module |

## Entry Criteria

- A real business sample for Saga or Outbox exists.
- Capability precheck can identify datasource groups.
- Management smoke can show multi-datasource precheck failure or warning.
- XA evaluation first defines module, dependencies, and recovery strategy.

## Final Decision

This topic does not enter core transaction implementation now. The framework core keeps local cross-datasource atomic transactions forbidden. Saga/Outbox are recommended business patterns. XA/JTA remains future optional-module evaluation only.
