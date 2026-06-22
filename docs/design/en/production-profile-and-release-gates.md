# Production Profile and Release Gates

## Problem

The framework already has package checksums, trust manifests, compatibility ranges, capability prechecks, offline repositories, and hot replacement deployment. These capabilities remain compatible by default, so many switches still use `DISABLED` or `WARN`. Production deployments need an explicit profile that turns plugin loading, repository releases, and real replacement into a fail-closed gate.

## Affected Modules

- `pf4boot-api`: add production profile and repository release gate configuration to `Pf4bootProperties`.
- `pf4boot-core`: enforce production gates in the default trust verifier, offline repository resolver, and deployment service.
- `pf4boot-starter`: continue to consume the same `Pf4bootProperties`; no new starter boundary is introduced.
- `docs/design`: keep the Chinese design as the source of truth and this English translation in sync.

## Design

Add `spring.pf4boot.production-profile-enabled`. It defaults to `false` for compatibility. When enabled, these governance modes use production effective values:

- `plugin-package-verification-mode=ENFORCE`
- `plugin-package-trust-mode=ENFORCE`
- `plugin-compatibility-verification-mode=ENFORCE`
- `plugin-capability-precheck-mode=ENFORCE`
- `plugin-compatibility-precheck-mode=ENFORCE`
- `plugin-repository-trust-mode=ENFORCE`
- `plugin-repository-release-gate-enabled=true`
- `plugin-package-signature-required=true`

The production profile does not automatically enable `plugin-repository-replace-enabled`. Applications must still opt in to real repository replacement; once enabled, release gates must also pass.

The repository release gate reads release `attributes.releaseGate` by default and requires the value `passed`. These properties customize the field and value:

- `spring.pf4boot.plugin-repository-release-gate-attribute`
- `spring.pf4boot.plugin-repository-release-gate-value`

Trust-chain enforcement has three layers:

1. The package checksum sidecar must exist and match.
2. The trust manifest must exist and must match plugin id, version, and package sha256.
3. Under the production profile, the trust manifest must include complete signature metadata, and `keyId` must be recognized by `plugin-package-trust-roots` or a `PluginTrustRootProvider`.

When a repository release is copied into controlled cache/staging, the deployment service writes a checksum sidecar for the cached package and copies the repository trust manifest next to it. Replacement can then reuse `PluginDeploymentService.replace(...)`, loader checks, rollback, and health checks without bypassing existing deployment governance.

## Compatibility

- With the production profile disabled, existing applications and legacy plugins keep their current behavior.
- With the production profile enabled, packages missing checksum, trust manifest, signature metadata, trust root, repository index signature, or release gate approval are rejected.
- Custom `PluginPackageTrustVerifier` implementations remain available as extra verifiers. The default verifier still enforces the base manifest, checksum, and trust-root gates first.
- If `plugin-repository-replace-enabled=false`, the production profile still allows only plan/dry-run repository flows, not real replacement.

## Verification

Minimal verification:

```powershell
.\gradlew.bat :pf4boot-core:test :pf4boot-api:test
```

When management endpoints are touched, also run:

```powershell
.\gradlew.bat :pf4boot-management-starter:test
```

## Open Questions

- The default verifier currently enforces signature metadata and trust-root recognition, but it does not define the canonical signed payload or cryptographic verification format. Full cryptographic signing should be designed as a separate trust-root topic.
- A sample `application-production.yml` can be added later when sample documentation is refreshed.
