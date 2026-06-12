# Plugin Framework Production Hardening Design

## Background

`pf4boot` already supports PF4J plugin loading, Spring Boot plugin contexts, Web/JPA integration, cross-plugin JPA transactions, hot replacement orchestration, read-only observability, and HTTP management APIs. The next step is not another isolated feature. The framework needs to become production-governable, auditable, recoverable, and evolvable.

This design consolidates the remaining framework-level hardening work: package signing and trust, persistent deployment records, lifecycle concurrency and leak checks, capability manifests, management smoke tests, observability closure, and a compatibility matrix.

## Goals

- Provide a pluggable integrity, signature, trust, and compatibility verification chain before loading or deploying plugin packages.
- Persist deployment operations, management operations, audit events, and idempotency records through recoverable interfaces.
- Make lifecycle concurrency, resource cleanup, and failed hot replacement paths repeatably verifiable.
- Let plugins declare provided capabilities, required capabilities, framework compatibility, and runtime constraints.
- Close the loop across management APIs, read-only Actuator diagnostics, deployment orchestration, and samples.
- Preserve Java 8, Spring Boot 2.7.x, existing PF4J dependencies, and current module boundaries.

## Non-Goals

- Cross-datasource transactions are not supported in this design.
- Spring Security, Flyway, Liquibase, external KMS, or a specific CA system must not become hard framework dependencies.
- Class-level hot replacement is out of scope; the replacement unit remains the plugin package.
- A management console UI is out of scope.
- Management write APIs, strict signature verification, and persistent stores remain opt-in.

## Current State

| Area | Existing Foundation | Remaining Gap |
| --- | --- | --- |
| Package verification | `PluginPackageVerifier`, checksum, system version checks | Signature format, trust roots, revocation, certificate rotation, audit |
| Hot replacement | `PluginDeploymentService`, precheck, rollback, HTTP entrypoints | Persistent records, crash recovery, cross-restart idempotency, audit persistence |
| Lifecycle | start/stop/reload/restart/delete, dependency order, cleanup | Concurrency gates, leak assertions, failure injection, runtime smoke |
| Observability | `pf4boot-actuator` snapshots and basic metrics | Deployment/management metrics, leak metrics, diagnostic error matrix |
| JPA | Optional JPA starter, single-datasource cross-plugin transactions, complex sample | Multi-datasource capability declarations, runtime refresh decision, cross-datasource boundary docs |
| Management | `pf4boot-management-starter`, local token, delegated auth SPI, idempotency | Persistent recorder and sample smoke scripts |
| Documentation | Chinese designs, English translations, developer guide, plans | Compatibility matrix and production acceptance evidence |

## Core Constraints

- Keep Java 8 source compatibility.
- Public SPI, DTOs, annotations, and error codes belong in `pf4boot-api`.
- PF4J runtime behavior, lifecycle locks, package verification calls, deployment orchestration, and default in-memory implementations belong in `pf4boot-core`.
- Spring Boot auto-configuration belongs in starter modules; HTTP management remains in `pf4boot-management-starter`.
- `pf4boot-actuator` remains read-only and must not expose lifecycle or deployment writes.
- JPA capabilities remain in `pf4boot-jpa*`; schema migration tools must not be hard dependencies.
- Production hardening features default to disabled or no-op.
- Breaking default changes must pass through WARN/compatibility modes first.

## Affected Modules

| Module | Responsibility |
| --- | --- |
| `pf4boot-api` | Public types for trust verification, capability manifests, operation records, audit, and recorder SPI |
| `pf4boot-core` | Verification chain integration, lifecycle mutual exclusion, default recorder, recovery entrypoint, cleanup diagnostics |
| `pf4boot-starter` | Default no-op/in-memory implementations and production properties |
| `pf4boot-actuator` | Read-only diagnostics, verification status, deployment summaries, and resource metrics |
| `pf4boot-management-starter` | Persistent idempotency, audit, deployment records, and local smoke support |
| `pf4boot-jpa*` | JPA datasource capability declarations and single-datasource transaction boundary |
| `samples/cross-plugin-jpa` | Complex JPA, management, hot replacement, and smoke examples |

## Implementation Conventions

This section is a hard implementation guide for follow-up coding agents. Prefer these names, packages, and steps. Reuse an existing equivalent type only when the current code already provides the same semantics, and document the difference.

### Packages And Classes

| Capability | Public API Location | Runtime Implementation Location | Starter/Test Location |
| --- | --- | --- | --- |
| Trust verification | `net.xdob.pf4boot.trust.*` | `net.xdob.pf4boot.trust.DefaultPluginPackageTrustVerifier`, `DefaultPluginTrustManifestLoader` | `pf4boot-core/src/test/java/net/xdob/pf4boot/trust/*Test.java` |
| Capability manifest | `net.xdob.pf4boot.capability.*` | `net.xdob.pf4boot.capability.DefaultPluginCapabilityResolver`, `PluginCapabilityPrecheck` | `pf4boot-core/src/test/java/net/xdob/pf4boot/capability/*Test.java` |
| Operation persistence | Prefer extending `net.xdob.pf4boot.management.PluginOperationStore`; add `PluginOperationRecorder` only if needed | `net.xdob.pf4boot.management.FilePluginOperationStore` or `net.xdob.pf4boot.management.store.FilePluginOperationStore` | `pf4boot-management-starter/src/test/java/net/xdob/pf4boot/management/starter/*Test.java` |
| Deployment persistence | Reuse `net.xdob.pf4boot.deployment.DeploymentRecord`; add store SPI under `net.xdob.pf4boot.deployment` | `net.xdob.pf4boot.deployment.FilePluginDeploymentRecordStore` | `pf4boot-core/src/test/java/net/xdob/pf4boot/deployment/*Test.java` |
| Lifecycle diagnostics | `net.xdob.pf4boot.diagnostic.*` | `net.xdob.pf4boot.diagnostic.DefaultPluginLifecycleDiagnostic` | `pf4boot-core/src/test/java/net/xdob/pf4boot/diagnostic/*Test.java` |
| Actuator summaries | Reuse `net.xdob.pf4boot.actuate.PluginRuntimeSnapshot`; add read-only DTOs only when needed | `net.xdob.pf4boot.actuate.DefaultPluginRuntimeInspector` | `pf4boot-actuator/src/test/java/net/xdob/pf4boot/actuate/*Test.java` |

Do not place these public types in `pf4boot-management-starter`. The starter owns Spring Boot conditional wiring, HTTP controllers, security policy, and local default beans only.

### Configuration

New settings should live under the existing `spring.pf4boot` namespace.

```yaml
spring:
  pf4boot:
    plugin-package-trust-mode: DISABLED # DISABLED, WARN, ENFORCE
    plugin-package-trust-manifest-extension: .pf4boot-trust.json
    plugin-package-trust-roots:
      - ${PF4BOOT_TRUST_ROOT:}
    plugin-capability-precheck-mode: DISABLED # DISABLED, WARN, ENFORCE
    plugin-operation-store:
      type: memory # memory, file
      directory: ${PF4BOOT_OPERATION_STORE:}
      fail-closed: true
    plugin-cleanup-diagnostic:
      enabled: false
      fail-deployment-on-leak: false
      classloader-check-enabled: false
```

Implementation rules:

- Prefer extending `net.xdob.pf4boot.spring.boot.Pf4bootProperties`.
- Keep HTTP management-only properties in `net.xdob.pf4boot.management.starter.Pf4bootManagementProperties`.
- Null enum inputs must fall back to safe defaults: trust/capability `DISABLED`, operation store `memory`, fail-closed `true`.
- Default configuration must not expose remote management writes, enforce signatures, or enable file persistence.

### Manifest Format

The first stage uses a sidecar file beside the plugin zip: `<pluginZipName>.pf4boot-trust.json`. Zip-internal `META-INF/pf4boot-trust.json` can be added later and is not required for P1.

```json
{
  "formatVersion": 1,
  "pluginId": "sample-order-plugin",
  "pluginVersion": "1.2.0",
  "packageSha256": "hex-lowercase-sha256",
  "signature": {
    "algorithm": "SHA256withRSA",
    "keyId": "local-dev-key",
    "value": "base64-signature"
  },
  "certificateChain": [
    "base64-der-or-pem-without-private-key"
  ],
  "capabilities": {
    "provides": [
      {
        "name": "web.mvc",
        "version": "1",
        "scope": "PLUGIN",
        "attributes": {
          "basePath": "/api/sample/order"
        }
      }
    ],
    "requires": [
      {
        "name": "jpa.datasource",
        "versionRange": "[1,2)",
        "required": true,
        "attributes": {
          "datasource": "orderDs"
        }
      }
    ]
  },
  "compatibility": {
    "javaVersion": "1.8",
    "pf4bootVersionRange": "[0.0.1,1.0.0)",
    "springBootVersionRange": "[2.7.0,2.8.0)"
  }
}
```

Validation rules:

- `pluginId` and `pluginVersion` must match the descriptor.
- `packageSha256` must be lowercase hex; comparison trims edge whitespace only.
- Unknown `formatVersion` records a warning in `WARN` mode and blocks in `ENFORCE` mode.
- `signature.value` must never be logged.
- Manifest parse failure is ignored in `DISABLED`, recorded in `WARN`, and blocked in `ENFORCE`.

### Persistence Format

The first file store uses JSON Lines. Each line is one complete JSON record. Writes must use a temp file or buffered line, flush, fsync, and atomic rename or append with fsync. If fsync cannot be guaranteed on the platform, document and test the downgrade.

Recommended directories:

```text
work/pf4boot/
  operations/
    operations-2026-06-12.jsonl
  deployments/
    deployments-2026-06-12.jsonl
  idempotency/
    keys-2026-06-12.jsonl
  recovery/
    recovery-scan.log
```

Minimal `operations` line:

```json
{
  "schemaVersion": 1,
  "operationId": "op-...",
  "idempotencyKey": "principal:hash",
  "requestHash": "sha256",
  "operationType": "DEPLOY_REPLACE",
  "pluginId": "sample-order-plugin",
  "state": "SUCCEEDED",
  "resultCode": "OK",
  "message": "deployment succeeded",
  "createdAt": 1781184000000,
  "updatedAt": 1781184001000
}
```

Recovery scan rules:

- Skip unparsable partial lines and record `PFP-STORE-004`.
- For the same `operationId`, use the record with the greatest `updatedAt`.
- For the same idempotency key, use the latest complete record; different request hash means conflict.
- `EXECUTING` and `ROLLING_BACK` after restart require recovery evaluation and must not be marked successful directly.

### Error Codes

| Prefix | Owner | Examples |
| --- | --- | --- |
| `PFT-` | package trust | `PFT-001` manifest missing, `PFT-002` checksum mismatch, `PFT-003` signature invalid, `PFT-004` trust root rejected |
| `PFC-` | capability | `PFC-001` manifest invalid, `PFC-002` required capability missing, `PFC-003` version range mismatch |
| `PFP-STORE-` | persistence | `PFP-STORE-001` store unavailable, `PFP-STORE-002` write failed, `PFP-STORE-003` idempotency conflict, `PFP-STORE-004` corrupted record skipped |
| `PFL-` | lifecycle diagnostic | `PFL-001` lifecycle lock conflict, `PFL-002` cleanup leak detected, `PFL-003` classloader still reachable |
| `PFS-` | smoke | `PFS-001` host not ready, `PFS-002` management call failed, `PFS-003` actuator check failed |

HTTP errors may contain only the error code, safe summary, and request/operation/deployment id. Do not return exception objects, full stacks, tokens, private keys, or sensitive absolute paths.

### Implementation Order Rules

1. Add API models and no-op/in-memory implementations before wiring runtime flows.
2. For every runtime call site, add unit tests before sample smoke.
3. `pf4boot-core` must not depend on `pf4boot-management-starter` or `pf4boot-actuator`.
4. `pf4boot-actuator` can only read snapshots and diagnostics; it must not call mutating manager/deployment methods.
5. P1/P2/P3 must not change required fields in existing plugin descriptors; use sidecar manifests for new metadata.
6. Every new public type must have JavaDoc or a design table explaining its semantics.

## Interface Design

### Package Trust Chain

```java
public interface PluginPackageTrustVerifier {
  PluginPackageTrustResult verify(PluginPackageTrustRequest request);
}
```

| Type | Owner | Description |
| --- | --- | --- |
| `PluginPackageTrustRequest` | `pf4boot-api` | Plugin ID, version, package path, descriptor, checksum, signature metadata, host trust configuration |
| `PluginPackageTrustResult` | `pf4boot-api` | `PASS`, `WARN`, `FAIL`, error code, auditable messages |
| `PluginTrustRootProvider` | `pf4boot-api` | Trust roots, certificate chains, revocation lists, or offline trust material |

The first implementation stage should define the SPI and model first. A default external manifest example can follow; the design does not force native JAR signing.

### Capability Manifest

```java
public interface PluginCapabilityDescriptor {
  String getPluginId();

  List<PluginCapability> provides();

  List<PluginCapabilityRequirement> requires();
}
```

Capability declarations are used for prechecks, not as a replacement for PF4J dependency resolution.

| Capability | Example |
| --- | --- |
| `web.mvc` | Dynamic MVC endpoints |
| `jpa.datasource` | Named JPA datasource and transaction environment |
| `jpa.consumer` | Consumer of a named datasource |
| `management.local` | Local management entrypoint |
| `scheduler` | Scheduled tasks |

### Persistent Records

```java
public interface PluginOperationRecorder {
  void saveOperation(PluginOperationRecord record);

  PluginOperationRecord findOperation(String operationId);

  List<PluginOperationRecord> findRecentOperations(PluginOperationQuery query);
}
```

```java
public interface PluginIdempotencyStore {
  PluginIdempotencyRecord get(String key);

  void putIfAbsent(PluginIdempotencyRecord record);

  void complete(String key, PluginAdminResponse<?> response);
}
```

The default implementation is in-memory. A file implementation is the first production option. Database support is only an SPI at this stage.

### Lifecycle Diagnostics

```java
public interface PluginLifecycleDiagnostic {
  PluginCleanupReport inspectAfterStop(String pluginId);

  PluginConcurrencyReport inspectLifecycleLocks();
}
```

Diagnostics are read-only and can be used by tests, Actuator, and management smoke checks.

## Data Structures

### `PluginOperationRecord`

| Field | Type | Description |
| --- | --- | --- |
| `operationId` | `String` | Management or deployment operation ID |
| `operationType` | `String` | `START`, `STOP`, `RELOAD`, `DEPLOY_REPLACE`, `ROLLBACK` |
| `pluginId` | `String` | Target plugin |
| `principal` | `String` | Principal summary, no sensitive credential |
| `requestHash` | `String` | Request hash for idempotency conflict checks |
| `state` | `String` | Operation state |
| `resultCode` | `String` | Error code or `OK` |
| `message` | `String` | Safe summary |
| `createdAt` | `long` | Creation time |
| `updatedAt` | `long` | Update time |

### `PluginCapability`

| Field | Type | Description |
| --- | --- | --- |
| `name` | `String` | Capability name |
| `version` | `String` | Capability version |
| `scope` | `String` | `HOST`, `PLUGIN`, `DATASOURCE` |
| `attributes` | `Map<String, String>` | Datasource name, package scans, transaction manager name, and similar attributes |

### `PluginCleanupReport`

| Field | Type | Description |
| --- | --- | --- |
| `pluginId` | `String` | Plugin ID |
| `passed` | `boolean` | Whether no leak remains |
| `remainingBeans` | `int` | Remaining beans |
| `remainingMappings` | `int` | Remaining MVC mappings |
| `remainingSchedulers` | `int` | Remaining scheduled tasks |
| `classLoaderReachable` | `boolean` | Whether the classloader is still strongly reachable |
| `messages` | `List<String>` | Diagnostic summaries |

## State Machine

### Operation Record States

| State | Meaning | Transitions |
| --- | --- | --- |
| `RECEIVED` | Request received | `VALIDATING`, `REJECTED` |
| `VALIDATING` | Permission, idempotency, parameter, and precheck validation | `EXECUTING`, `REJECTED` |
| `EXECUTING` | Lifecycle or deployment operation is running | `SUCCEEDED`, `FAILED`, `ROLLING_BACK` |
| `ROLLING_BACK` | Rollback is running | `ROLLED_BACK`, `MANUAL_INTERVENTION` |
| `SUCCEEDED` | Operation succeeded | Terminal |
| `FAILED` | Operation failed without automatic rollback | Terminal |
| `ROLLED_BACK` | Failure recovered to old state | Terminal |
| `MANUAL_INTERVENTION` | Automatic recovery failed | Terminal |
| `REJECTED` | Validation failed; runtime unchanged | Terminal |

### Trust Verification States

| State | Meaning |
| --- | --- |
| `NOT_CONFIGURED` | No signature or trust chain configured; mode decides WARN or PASS |
| `CHECKSUM_PASS` | Digest verification passed |
| `SIGNATURE_PASS` | Signature verification passed |
| `TRUST_PASS` | Trust root, revocation, and validity checks passed |
| `WARN` | Non-blocking issue |
| `FAIL` | Blocking issue |

## Sequences

### Deployment Precheck

1. Management endpoint receives a deployment plan request.
2. Authentication, authorization, idempotency, and staging path validation run.
3. The plugin descriptor and capability manifest are parsed.
4. Checksum, signature, trust root, and compatibility matrix verification run.
5. The dependency impact scope and datasource capability requirements are calculated.
6. A `DeploymentPlan` and `PluginOperationRecord` are generated.
7. The dry-run result is returned; runtime state is unchanged by default.

### Post-Stop Diagnostics

1. Lifecycle service completes `stopPlugin`.
2. The framework calls `PluginLifecycleDiagnostic.inspectAfterStop(pluginId)`.
3. Dynamic beans, MVC mappings, interceptors, scheduled tasks, JPA resources, and classloader references are inspected.
4. The report is written to operation/deployment records and exposed through Actuator.
5. If this is part of a deployment and diagnostics fail, the flow enters rollback or manual intervention.

## Error Handling

| Error | External Behavior | Record Requirement |
| --- | --- | --- |
| Missing signature | `WARN` or `FAIL` depending on mode | Plugin, version, mode, missing item |
| Invalid trust root | Block deployment | No private key, token, or full sensitive path |
| Idempotency conflict | Return `409` | Key, principal summary, request hash |
| Persistent store unavailable | Fail write operations by default; explicit downgrade may use memory with warnings | Recorder type and reason |
| Cleanup failure | Roll back or require manual intervention | Remaining resource counts |
| Missing capability | Precheck fails; runtime unchanged | Missing capability and requirement source |

## Idempotency

- Management writes use `principal + operation + pluginId + requestHash`.
- Deployment writes also bind `deploymentId` and target package checksum.
- Same key and same request return the existing result.
- Same key but different request hash returns conflict.
- With persistent storage enabled, completed, running, and recoverable operations survive host restarts.

## Rollback

- Trust, capability, and compatibility failures do not modify runtime state.
- Before deployment execution, the framework stores old package path, old descriptor summary, old start state, and affected plugin list.
- If new startup or health check fails, old package and old start state are restored first.
- If a host restarts while a record is `EXECUTING`, recovery scanning decides whether the new package was activated. Unknown or unsafe states enter manual intervention.
- Rollback failure must preserve diagnostic evidence.

## Compatibility

- New SPI defaults to no-op or in-memory implementations.
- Signature verification starts as non-enforcing; production can move from `DISABLED` to `WARN` to `ENFORCE`.
- Missing capability manifests remain compatible unless strict precheck is explicitly enabled.
- `pf4boot-actuator` remains read-only.
- Single-datasource cross-plugin JPA transactions remain supported; cross-datasource transactions remain unsupported.

## Rollout

| Phase | Default | Migration Action |
| --- | --- | --- |
| P0 Docs freeze | Runtime unchanged | Freeze design, plan, and acceptance checklist |
| P1 Trust WARN | Do not block old packages | Add checksum/signature manifests |
| P2 Persistent recorder | In-memory default | Hosts may enable file recorder |
| P3 Strict precheck | WARN default | Enable capability and compatibility matrix checks gradually |
| P4 ENFORCE | Recommended only for new apps | Block unsigned or capability-invalid packages |

## Verification

- API compile: `.\gradlew.bat :pf4boot-api:compileJava`
- Lifecycle concurrency and cleanup: `.\gradlew.bat :pf4boot-core:test`
- Management idempotency, audit, and safe errors: `.\gradlew.bat :pf4boot-management-starter:test`
- Read-only observability: `.\gradlew.bat :pf4boot-actuator:test`
- Complex sample packaging: `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`
- Runtime smoke: start the sample host and call management, Actuator, and cross-plugin JPA endpoints.

## Risks

| Risk | Severity | Mitigation |
| --- | --- | --- |
| Trust implementation is bound too early | High | Define SPI first; keep CA/KMS optional |
| Store outage makes management writes unpredictable | High | Fail closed by default; explicit downgrade only |
| Capability declarations drift from runtime behavior | Medium | Add startup diagnostics and smoke checks |
| Cleanup diagnostics are flaky | Medium | Start as WARN and test assertions before enforcing |
| Plans are not traceable | Medium | Maintain separate plan and acceptance documents with evidence |

## Phased Plan

Tracking lives in [plugin-framework-production-hardening-plan.md](plugin-framework-production-hardening-plan.md) and [plugin-framework-production-hardening-acceptance.md](plugin-framework-production-hardening-acceptance.md).

1. P0: Freeze design, Chinese/English docs, and indexes.
2. P1: Add trust-chain SPI and WARN-mode sample.
3. P2: Add persistent operation/deployment/idempotency recorders.
4. P3: Add lifecycle concurrency, cleanup leak, and failure-injection verification.
5. P4: Add capability manifests and compatibility matrix prechecks.
6. P5: Add management smoke, Actuator diagnostics, and sample closure.
7. P6: Add follow-up decision docs for JPA runtime refresh and cross-datasource transactions.

## Open Questions

- Whether the default signature manifest should live beside the plugin zip or inside `META-INF` must be decided before P1 implementation.
- Whether the file recorder should use JSON Lines or one JSON file per record must be decided with recovery scanning complexity in mind.
- Java 8 classloader leak checks can only use weak references and GC-assisted assertions; runtime enablement needs stability proof in tests first.
