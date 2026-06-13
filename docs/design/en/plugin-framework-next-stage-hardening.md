# Plugin Framework Next-Stage Production Hardening Design

## Background

The P0-P6 production-hardening track is complete. The framework now has package trust verification, persistent operation records, lifecycle diagnostics, capability manifests, HTTP management APIs, Actuator observations, and complex sample runtime smoke coverage. Three follow-up items are ready for implementation:

1. Offline plugin repository governance: promote plugin packages from local files to indexed, signed, rollout-aware, rollback-aware release artifacts.
2. Strict version-range prechecks: turn `pf4bootVersionRange`, `springBootVersionRange`, and `capability.versionRange` from diagnostics into deploy-time checks.
3. Gradle/CI runtime smoke: turn the existing PowerShell smoke into a stable Gradle entry point with machine-readable reports.

This design does not reopen P0-P6 and does not implement a remote marketplace, management console UI, cross-datasource transactions, or online JPA metamodel refresh.

## Goals

- Define stable APIs, index format, resolution flow, deployment integration, and sample verification for offline-index repositories.
- Define a Java 8 compatible version-range parser, matching rules, error codes, and precheck modes.
- Define a Gradle task, report files, isolated work directories, and CI assertion rules for runtime smoke.
- Preserve compatibility by keeping new features disabled or warning-only by default.
- Provide concrete module boundaries, task cards, acceptance criteria, and forbidden changes for later implementation.

## Non-Goals

- No public plugin marketplace, account system, billing, review flow, or central service.
- No mandatory HTTP client, object storage SDK, artifact repository SDK, Spring Security, or CI platform SDK dependency in `pf4boot-core`.
- No replacement of PF4J descriptors or PF4J plugin dependency resolution.
- No removal of the PowerShell smoke script; the first Gradle task may wrap it.
- No cross-datasource atomic transactions, JPA runtime reload, or management console UI in this stage.

## Current State

| Area | Current State | Gap |
| --- | --- | --- |
| Package source | Local directories, zip, link/development repositories, management staged paths | No release index, version selection, rollback candidate, or repository-source audit |
| Package trust | `.pf4boot-trust.json`, checksum, WARN/ENFORCE | Repository index needs validation and must link to package trust |
| Capabilities | `PluginCapability` and `PluginCapabilityRequirement.versionRange` exist | Version ranges are not enforced by a unified matcher |
| Management deployment | `PluginDeploymentService` supports plan/replace/rollback records | No dry-run from repository release to staged package |
| Runtime smoke | `samples/cross-plugin-jpa/scripts/runtime-smoke.ps1` works end to end | No Gradle task, machine-readable report, or CI artifact contract |

## Core Constraints

- Keep Java 8 compatibility.
- Put new public types in `pf4boot-api`, runtime implementations in `pf4boot-core`, auto-configuration in `pf4boot-starter`, HTTP management extensions in `pf4boot-management-starter`, read-only observation in `pf4boot-actuator`, and samples under `samples/*`.
- Keep offline repositories and strict version prechecks disabled by default.
- All write management operations still go through authentication, idempotency, audit, and path validation.
- Repository indexes, reports, and logs must not contain tokens, private keys, full stacks, sensitive user paths, or internal absolute paths.
- Gradle/CI smoke must not depend on external networks, browsers, or private credentials.

## Affected Modules

| Module | Responsibility |
| --- | --- |
| `pf4boot-api` | Repository release models, resolver SPI, version range model, matcher SPI |
| `pf4boot-core` | Offline index loading, checksum verification, release resolution, staging integration, default version matcher |
| `pf4boot-starter` | Bind `spring.pf4boot.repository.*` and compatibility precheck properties; register default beans |
| `pf4boot-management-starter` | Add repository dry-run/plan entry or extend existing plan request |
| `pf4boot-actuator` | Expose repository configuration summary, last index state, and version precheck statistics |
| `samples/cross-plugin-jpa` | Sample repository index, index-generation instructions, Gradle smoke task |
| `docs/design` | Design, plan, acceptance tracking, and bilingual developer-guide updates |

## Interface Design

### Offline Plugin Repository

Suggested public package: `net.xdob.pf4boot.repository`.

```java
public interface PluginRepositoryResolver {
  PluginRepositoryIndex loadIndex();

  PluginReleaseRecord resolve(PluginReleaseRequest request);
}
```

```java
public class PluginReleaseRequest {
  private String pluginId;
  private String version;
  private String versionRange;
  private boolean rollback;
  private Map<String, String> attributes;
}
```

```java
public class PluginRepositoryIndex {
  private int schemaVersion;
  private String repositoryId;
  private long generatedAt;
  private List<PluginReleaseRecord> releases;
  private String signature;
}
```

```java
public class PluginReleaseRecord {
  private String repositoryId;
  private String pluginId;
  private String version;
  private String packagePath;
  private String packageSha256;
  private String trustManifestPath;
  private String rolloutPolicy;
  private boolean rollbackCandidate;
  private Map<String, String> attributes;
}
```

The first stage supports only `type=offline-index`. Index locations are local or mounted directories; core does not download remote packages.

### Version Ranges

Suggested public package: `net.xdob.pf4boot.version`.

```java
public interface VersionRangeMatcher {
  VersionRange parse(String expression);

  boolean matches(String version, VersionRange range);
}
```

```java
public class VersionRange {
  private String expression;
  private VersionBoundary lower;
  private VersionBoundary upper;
  private boolean exact;
}
```

Supported first-stage expressions:

| Expression | Meaning |
| --- | --- |
| `1.2.3` | Exact match |
| `[1.0,2.0)` | `>= 1.0` and `< 2.0` |
| `(1.0,2.0]` | `> 1.0` and `<= 2.0` |
| `[1.0,)` | `>= 1.0` |
| `(,2.0]` | `<= 2.0` |

### Gradle/CI Smoke

Add this task under `samples/cross-plugin-jpa`:

```text
:samples:cross-plugin-jpa:app-run:runtimeSmoke
```

Expected artifacts:

| Artifact | Path |
| --- | --- |
| Text log | `samples/cross-plugin-jpa/app-run/build/tmp/runtime-smoke/runtime.log` |
| Machine-readable report | `samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json` |
| Optional JUnit XML | `samples/cross-plugin-jpa/app-run/build/test-results/runtimeSmoke/TEST-runtime-smoke.xml` |

## Data Structures

`repository-index.json` first-stage format:

```json
{
  "schemaVersion": 1,
  "repositoryId": "sample-offline",
  "generatedAt": 1781280000000,
  "releases": [
    {
      "pluginId": "sample-workflow",
      "version": "3.0.0",
      "packagePath": "plugins/plugin-workflow-3.0.0.zip",
      "packageSha256": "lowercase-sha256",
      "trustManifestPath": "plugins/plugin-workflow-3.0.0.zip.pf4boot-trust.json",
      "rolloutPolicy": "manual",
      "rollbackCandidate": true,
      "attributes": {
        "channel": "stable"
      }
    }
  ],
  "signature": "base64-signature"
}
```

`runtime-smoke/result.json` first-stage format:

```json
{
  "status": "PASSED",
  "startedAt": 1781280000000,
  "finishedAt": 1781280060000,
  "port": 7791,
  "checks": [
    {"name": "hostReady", "status": "PASSED", "message": "HTTP 200"},
    {"name": "managementIdempotency", "status": "PASSED", "message": "operation replayed"}
  ]
}
```

## State Machines

Repository release to deployment:

```text
DISABLED
ENABLED -> INDEX_LOADING -> INDEX_READY / INDEX_FAILED
INDEX_READY -> RELEASE_RESOLVED -> PACKAGE_VERIFIED -> STAGED
STAGED -> DEPLOYMENT_PLANNED -> DEPLOYED / REJECTED / MANUAL_INTERVENTION
```

Version precheck:

```text
NOT_CONFIGURED -> SKIPPED
RANGE_PARSED -> MATCHED / MISMATCHED / INVALID_RANGE
MISMATCHED + WARN -> WARNING_RECORDED
MISMATCHED + ENFORCE -> DEPLOYMENT_REJECTED
INVALID_RANGE + WARN -> WARNING_RECORDED
INVALID_RANGE + ENFORCE -> DEPLOYMENT_REJECTED
```

## Sequences

### Repository Dry-Run

1. Management API receives pluginId/version or pluginId/versionRange.
2. Authentication, idempotency, and parameter validation pass.
3. `PluginRepositoryResolver.loadIndex()` reads the index.
4. Resolver validates schema, relative paths, signature summary, and release records.
5. `resolve()` selects the release and checks rollback candidates.
6. Resolver verifies package sha256 and trust manifest presence.
7. The package is copied to staging.
8. Existing `PluginDeploymentService.planReplacement()` runs.
9. The response includes repository id, release version, staged-path summary, and deployment checks.

### Version Precheck

1. Parse the staged plugin trust manifest.
2. Check `pf4bootVersionRange` against the current framework version.
3. Check `springBootVersionRange` against the current Spring Boot version.
4. Check each `PluginCapabilityRequirement.versionRange` against provider capability versions.
5. Convert results to `DeploymentCheckResult`.
6. Warning or rejection follows the configured mode.

### Gradle Runtime Smoke

1. `runtimeSmoke` depends on sample runtime assembly.
2. The task prepares an isolated work directory and port.
3. It starts the app-run runtime.
4. It runs business, management, Actuator, failure-path, and cleanup checks.
5. It writes `result.json` and logs.
6. Any required check failure exits non-zero.

## Failure Handling

| Scenario | Behavior |
| --- | --- |
| Repository disabled | Historical flow continues; Actuator shows `repository.enabled=false` |
| Missing or invalid index | Repository feature unavailable; local directory loading unaffected |
| Path traversal | Staging blocked with a sanitized error |
| Package checksum mismatch | Staging blocked with `PFR-003` |
| Invalid version range | WARN records warning; ENFORCE rejects |
| Gradle smoke timeout | Print log tail, write failed report, clean process |
| CI without PowerShell | Task fails clearly; a later Java runner can be added |

## Idempotency

- Repository dry-run uses existing management idempotency keys.
- Staging directories are isolated by operation id or request hash.
- `runtimeSmoke` uses an isolated build tmp directory and cleans up processes and data; failures keep logs and reports.

## Rollback

- A rollback candidate must be resolved before repository deployment.
- Existing rollback orchestration is reused if deployment fails.
- New configuration is disabled by default, so removing it restores P0-P6 behavior.
- The PowerShell script remains available if the Gradle task fails.

## Compatibility

- Offline repository is disabled by default.
- Strict version precheck does not block by default.
- Public APIs are additive only.
- Existing `assembleSampleRuntime` and `runtime-smoke.ps1` remain supported.

## Rollout

1. Add docs and sample index first; production code remains no-op by default.
2. Add version ranges in WARN mode.
3. Add offline repository dry-run before replace.
4. Add smoke task for local use, then CI.
5. Move sample configs to stricter ENFORCE examples only after stability.

## Verification

| Level | Coverage |
| --- | --- |
| Unit | Version range parse/match, index parse, path traversal, checksum mismatch, release selection |
| Integration | Repository release to deployment plan; version precheck to `DeploymentCheckResult` |
| Starter | Config binding, disabled defaults, conditional beans |
| Management | Dry-run authentication, idempotency, sanitized errors |
| Actuator | Repository/version precheck summary |
| Sample smoke | Gradle task success, failure, report, cleanup |

## Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Repository governance expands into a remote marketplace | Core complexity and security surface grow | Stage one is offline-index only |
| Version comparison differs from user expectations | False deployment rejection | Start with WARN and document supported syntax |
| Gradle task depends on PowerShell | Linux CI may fail | Detect clearly; add Java runner later if needed |
| Weak path handling | Repository escape | Require relative paths that resolve within repository root |
| Report leaks secrets | Security risk | Report only summaries, codes, and relative paths |

## Implementation Plan

Detailed tasks and acceptance tracking live in:

- [plugin-framework-next-stage-hardening-plan.md](plugin-framework-next-stage-hardening-plan.md)
- [plugin-framework-next-stage-hardening-acceptance.md](plugin-framework-next-stage-hardening-acceptance.md)

## Open Questions

- Framework version source: prefer package implementation version, with configurable fallback.
- Spring Boot version override: allow `spring.pf4boot.compatibility.spring-boot-version`, defaulting to `SpringBootVersion.getVersion()`.
- JUnit XML: optional in stage one; `result.json` and non-zero task exit are required.
