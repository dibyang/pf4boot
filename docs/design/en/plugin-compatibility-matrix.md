# Plugin Compatibility Matrix And Package Verification

## Background

A plugin framework must not only build plugin packages, but also determine whether a package fits the current host. In 3.3, compatibility matrix and package verification become pre-delivery checks: development-time sample acceptance, and production-time integration with trust manifests, offline repository, and deployment precheck.

## Compatibility Matrix

Current 3.3 baseline:

| Dimension | Version / Range | Notes |
| --- | --- | --- |
| `pf4boot` | `[3.3.0,3.4.0)` | 3.3 plugin ecosystem version |
| `pf4boot-plugin` | `[1.7.0,1.8.0)` | current helper Gradle plugin baseline |
| Spring Boot | `[2.7.0,2.8.0)` | project currently uses 2.7.x |
| Spring Framework | `[5.3.0,5.4.0)` | follows Spring Boot 2.7.x |
| PF4J | `[3.15.0,3.16.0)` | current `pf4j_version=3.15.0` |
| JDK | `[1.8,1.9)` | source and plugin packages stay Java 8 compatible |
| Package format | `1` | zip root contains `plugin.properties`; dependencies live under `lib/` |
| Descriptor source | `plugin.properties` | manifest descriptors remain loader-compatible, but official samples currently use properties |

## Package Check Rules

| Rule | Level | Current Sample Behavior |
| --- | --- | --- |
| `DESCRIPTOR_REQUIRED` | ERROR | `plugin.properties` must exist and contain `plugin.id`, `plugin.version`, and `plugin.class` |
| `NO_HOST_API_BUNDLED` | ERROR | `lib/` must not contain host APIs such as `pf4boot-api`, `pf4boot-core`, `pf4boot-jpa`, or starters |
| `CHECKSUM_PRESENT` | WARN | missing `.sha256` sidecar warns but does not block the sample |
| `TRUST_MANIFEST_PRESENT` | WARN | missing `.pf4boot-trust.json` sidecar warns but does not block the sample |

WARN rules keep historical packages compatible. Production environments can move them to ENFORCE in later versions.

## E4.1 Production Compatibility Precheck

The sample-level `verifySamplePluginPackages` task catches common build-time packaging mistakes. Production compatibility is enforced by `PluginDeploymentService.planReplacement(...)`, and applies to both staged path deployment and repository release deployment.

### Trust Manifest Fields

`.pf4boot-trust.json` may declare these version range fields:

| Field | Compared Against | Default Actual Value |
| --- | --- | --- |
| `pf4bootVersionRange` | `spring.pf4boot.plugin-compatibility-pf4boot-version`; falls back to `systemVersion` when empty | `0.0.0` |
| `springBootVersionRange` | `spring.pf4boot.plugin-compatibility-spring-boot-version`; falls back to Spring Boot runtime version | current Spring Boot |
| `pf4jVersionRange` | `spring.pf4boot.plugin-compatibility-pf4j-version` | `3.15.0` |
| `pf4bootPluginVersionRange` | `spring.pf4boot.plugin-compatibility-pf4boot-plugin-version` | `1.7.0` |
| `jdkVersionRange` | `spring.pf4boot.plugin-compatibility-jdk-version` | `1.8` |
| `packageFormatVersionRange` | `spring.pf4boot.plugin-compatibility-package-format-version` | `1` |

Example:

```json
{
  "pluginId": "sample-workflow",
  "pluginVersion": "3.3.0",
  "packageSha256": "replace-with-lowercase-sha256",
  "pf4bootVersionRange": "[3.3.0,3.4.0)",
  "pf4bootPluginVersionRange": "[1.7.0,1.8.0)",
  "springBootVersionRange": "[2.7.0,2.8.0)",
  "pf4jVersionRange": "[3.15.0,3.16.0)",
  "jdkVersionRange": "[1.8,1.9)",
  "packageFormatVersionRange": "[1,2)"
}
```

### Precheck Mode

`spring.pf4boot.plugin-compatibility-precheck-mode` controls behavior:

| Mode | Behavior |
| --- | --- |
| `DISABLED` | ignore compatibility ranges from the trust manifest |
| `WARN` | version mismatches are written as `DeploymentCheckResult(WARN)` and the plan remains executable |
| `ENFORCE` | version mismatches are written as `DeploymentCheckResult(ERROR)` and the plan is not executable |

Stable rule codes:

| Rule Code | Meaning |
| --- | --- |
| `PF4BOOT_VERSION_RANGE_MISMATCH` | pf4boot version mismatch |
| `SPRING_BOOT_VERSION_RANGE_MISMATCH` | Spring Boot version mismatch |
| `PF4J_VERSION_RANGE_MISMATCH` | PF4J version mismatch |
| `PF4BOOT_PLUGIN_VERSION_RANGE_MISMATCH` | pf4boot-plugin version mismatch |
| `JDK_VERSION_RANGE_MISMATCH` | JDK version mismatch |
| `PACKAGE_FORMAT_VERSION_RANGE_MISMATCH` | package format version mismatch |
| `PFC-004` | manifest parsing failed or version range expression is invalid |

Production deployments should start with `WARN`, observe results, and then move to `ENFORCE`:

```yaml
spring:
  pf4boot:
    plugin-compatibility-precheck-mode: WARN
    plugin-compatibility-pf4boot-version: 3.3.0
    plugin-compatibility-pf4boot-plugin-version: 1.7.0
    plugin-compatibility-spring-boot-version: 2.7.22
    plugin-compatibility-pf4j-version: 3.15.0
    plugin-compatibility-jdk-version: 1.8
    plugin-compatibility-package-format-version: 1
```

## Machine-Readable Report

`samples:cross-plugin-jpa:demo-host:assembleSamplePlugins` automatically runs `verifySamplePluginPackages` after assembling packages and writes:

```text
samples/cross-plugin-jpa/demo-host/build/reports/plugin-package-verification/result.json
```

Report shape:

```json
{
  "schemaVersion": 1,
  "generatedAt": 1781786835637,
  "state": "PASSED",
  "packages": [
    {
      "schemaVersion": 1,
      "pluginId": "sample-workflow",
      "pluginVersion": "3.3.0-SNAPSHOT",
      "packagePath": "samples/cross-plugin-jpa/demo-host/build/sample-plugins/plugin-workflow-3.3.0-SNAPSHOT.zip",
      "state": "PASSED",
      "bundledLibs": ["plugin-workflow-3.3.0-SNAPSHOT.jar"],
      "rules": []
    }
  ]
}
```

Any ERROR-level failure fails the task; WARN entries are recorded but do not block the sample.

## Verification Command

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

This command verifies:

- `pf4boot-plugin 1.7.0` can generate plugin zips.
- sample plugin descriptors are readable.
- sample plugins do not bundle host APIs accidentally.
- the package verification report is available for CI or later smoke checks.

Production compatibility precheck verification:

```powershell
.\gradlew.bat :pf4boot-core:test
```

This command covers WARN/ENFORCE behavior for pf4boot, Spring Boot, PF4J, pf4boot-plugin, JDK, and package format ranges in the trust manifest.
