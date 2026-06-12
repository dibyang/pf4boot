# Plugin Repository Governance Decision

## Background

pf4boot can load plugins from local zip/link/development repositories. Production hardening already added trust manifests, deployment records, HTTP management APIs, hot replacement orchestration, and runtime smoke. The missing piece is the path from developer-local package files to governable release artifacts: version metadata, signatures, rollout, rollback, index, and repository source.

This decision defines repository governance boundaries only. It does not implement a remote marketplace or central service.

## Goals

- Decide the first-stage plugin repository shape.
- Define package index, release records, signing, rollout, and rollback governance.
- Stay compatible with current local plugin directories and deployment APIs.
- Avoid mandatory remote service dependencies.

## Non-Goals

- Do not implement a public plugin marketplace.
- Do not add accounts, billing, review workflows, or remote console behavior.
- Do not make core depend on HTTP clients, object storage SDKs, or artifact repository SDKs.
- Do not replace PF4J descriptors or dependency resolution.

## Current Flow

| Area | Current State |
| --- | --- |
| Plugin loading | Local directory, zip, link, and development repositories |
| Package verification | Checksum, trust manifest, WARN/ENFORCE |
| Deployment management | HTTP management API supports plan/replace/rollback query |
| Records | Operation/deployment/idempotency store |
| Gap | No repository index, release metadata, rollout policy, or rollback package selection |

## Core Constraints

- The plugin package remains the deployment unit.
- Trust verification must run before loading/deployment.
- Repository governance must not change historical local plugin directory behavior by default.
- Remote central service cannot be a framework startup prerequisite.
- Release indexes must not contain private keys, tokens, or internal absolute paths.

## Alternatives

| Option | Description | Pros | Cons | Decision |
| --- | --- | --- | --- | --- |
| A. Local directory repository | Keep manually placed zips and directory scan | Simple, compatible | Weak version and rollback governance | Keep compatible |
| B. Offline index repository | Local/intranet directory has `repository-index.json` and packages | Signed, auditable, no remote dependency | Needs index generation and validation tooling | Recommended first stage |
| C. Remote central repository | Framework connects to central service to query/download packages | Feature-rich | Network, auth, availability, and security complexity | Deferred |
| D. Mirror/cache repository | Host syncs packages from index or remote source into local cache | Good for isolated production networks | Needs sync tooling and cache cleanup | Later candidate |

## Recommendation

Use an offline index repository in the first stage. The host explicitly configures a local or intranet read-only repository directory containing a signed index and plugin packages. Deployment remains explicitly triggered by management API or operations scripts. No mandatory remote central service is introduced.

## Interface And Configuration Draft

```java
public interface PluginRepositoryResolver {
  PluginRepositoryIndex loadIndex();

  PluginReleaseRecord resolve(String pluginId, String version);
}
```

```java
public class PluginRepositoryIndex {
  private int schemaVersion;
  private String repositoryId;
  private List<PluginReleaseRecord> releases;
  private String signature;
}
```

```java
public class PluginReleaseRecord {
  private String pluginId;
  private String version;
  private String packagePath;
  private String packageSha256;
  private String trustManifestPath;
  private String rolloutPolicy;
  private boolean rollbackCandidate;
}
```

```yaml
spring:
  pf4boot:
    repository:
      enabled: false
      type: offline-index
      location: ${PF4BOOT_PLUGIN_REPOSITORY:}
      trust-mode: WARN # WARN, ENFORCE
      cache-directory: ${PF4BOOT_PLUGIN_CACHE:}
```

Default is `enabled=false`.

## Data Structure

`repository-index.json` example:

```json
{
  "schemaVersion": 1,
  "repositoryId": "local-prod",
  "generatedAt": 1781280000000,
  "releases": [
    {
      "pluginId": "sample-workflow",
      "version": "3.0.0",
      "packagePath": "plugins/plugin-workflow-3.0.0.zip",
      "packageSha256": "lowercase-sha256",
      "trustManifestPath": "plugins/plugin-workflow-3.0.0.zip.pf4boot-trust.json",
      "rolloutPolicy": "manual",
      "rollbackCandidate": true
    }
  ],
  "signature": "base64-signature"
}
```

## State Machine

```text
INDEX_LOADED -> PACKAGE_RESOLVED -> PACKAGE_VERIFIED -> STAGED
STAGED -> PLANNED -> DEPLOYED / REJECTED
PACKAGE_VERIFIED -> REJECTED
```

Repository resolution stops before `STAGED`; it must not directly start or replace plugins.

## Sequence

1. Host or management API requests deployment for a plugin version.
2. `PluginRepositoryResolver` reads the index.
3. Index signature, package digest, and trust manifest are verified.
4. The package is copied or resolved into staging.
5. Existing deployment plan/replace flow runs.
6. Deployment record stores repository id, release record summary, and rollback candidate package.

## Error Handling

| Error | Behavior |
| --- | --- |
| Missing index | Repository feature unavailable; historical local loading unaffected |
| Index signature invalid | `ENFORCE` blocks; `WARN` only allows dry-run |
| Package digest mismatch | Block staging |
| Rollback package missing | Deployment precheck warning or failure depending on policy |

## Compatibility

- Disabled by default.
- Deployment API may keep accepting direct staged paths.
- Release record metadata supplements, not replaces, PF4J descriptor.

## Rollback

Deployment planning must resolve and record a rollback candidate. If repository is unavailable, an already-staged old package may still roll back through deployment records. If old package is unavailable, enter manual intervention.

## Rollout

1. Add offline index documentation and sample index.
2. Add dry-run resolution to management API.
3. Add operations scripts for index generation and signing.
4. Later evaluate mirror/cache repositories.

## Verification

- Index parsing and signature tests.
- Package digest mismatch rejection tests.
- Release record to deployment plan dry-run tests.
- Rollback package missing diagnostics.
- Sample offline index smoke.

## Risks

| Risk | Mitigation |
| --- | --- |
| Remote service complexity enters core too early | First stage is offline index only |
| Index and package drift | Digest and trust manifest verification |
| Rollback package lost | Plan phase must check rollback candidate |
| Sensitive data leaks | Index stores relative paths, digests, and safe metadata only |

## Entry Criteria

- P1 trust manifests and P2 persistent records are stable.
- A real release flow or sample validates index generation.
- Management API can dry-run repository release records into staged packages.

## Final Decision

Do not implement a remote plugin marketplace now. If implemented later, start with offline index repository and signed release governance. Remote central services, mirror caches, and UI marketplace are separate future topics.
