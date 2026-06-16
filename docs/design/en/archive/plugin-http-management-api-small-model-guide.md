# Plugin HTTP Management API Small-Model Execution Guide

This is a compact, deterministic guide for models that need strict execution steps.

## Purpose

- Add a complete management HTTP surface with minimal ambiguity.
- Keep behavior compatible with existing plugin manager and deployment service contracts.
- Avoid missing security basics in the first phase.

## What Exists in Current Branch

- `pf4boot-management-starter` module has been added.
- Core controller and starter wiring are present.
- This guide only documents how to finish, verify, and handoff reliably.

## Phase 0: Preconditions

1. Confirm only the following modules are edited:
   - `pf4boot-api`
   - `pf4boot-starter`
   - `pf4boot-management-starter`
   - `docs/design/en`
2. Confirm no unrelated files are deleted.
3. Keep edits UTF-8 and Java 8 compatible.

## Phase 1: Verify Baseline

Run:

```powershell
.\gradlew.bat :pf4boot-management-starter:compileJava
.\gradlew.bat :pf4boot-management-starter:compileTestJava
```

If compile passes, continue. If compile fails:

- Fix missing imports/beans first.
- Re-run baseline compile.

## Phase 2: File-by-File Completion Checklist

### 2.1 Module and auto-config skeleton

- `settings.gradle`
  - Confirm: `include 'pf4boot-management-starter'`.
- `pf4boot-management-starter/build.gradle`
  - Confirm compile deps: `pf4boot-api`, `pf4boot-core`, `pf4boot-web-support`.
- `pf4boot-management-starter/src/main/java/.../Pf4bootManagementAutoConfiguration.java`
  - Confirm controller and exception handler are exported only when `spring.pf4boot.management.http.enabled=true`.

### 2.2 `pf4boot-api` contract classes

Files expected:

- `PluginAdminResponse.java`
- `PluginManagementMode.java`
- `PluginManagementOperation.java`
- `PluginManagementErrorCode.java`
- `PluginManagementRequest.java`
- `PluginManagementPrincipal.java`
- `PluginManagementAuthorizer.java`
- `PluginManagementAuditEvent.java`
- `PluginOperationRecord.java`
- `PluginOperationStore.java`

Checks:

- Operations map to permissions (`PLUGIN_READ`, `PLUGIN_START`, `PLUGIN_RESTART`, `DEPLOYMENT_PLAN`, etc.).
- Error codes cover 9 values at least.
- Response model has empty warning list by default.

### 2.3 Properties and startup validation

- `Pf4bootManagementProperties.java`
  - Prefix: `spring.pf4boot.management.http`.
  - Defaults: `enabled=false`, `mode=DISABLED`, `allowLoopbackOnly=true`.
- `PluginManagementStartupValidator.java`
  - `enabled=true` + `mode=DISABLED` -> startup fail.
  - `mode=LOCAL_TOKEN` without token (and without local SPI override) -> startup fail.
  - `mode=REMOTE_DELEGATED` without external `PluginManagementAuthorizer` -> startup fail.

### 2.4 Request pipeline

- `PluginManagementRequestContext.java`
- `PluginManagementRequestFactory.java`

Checks:

- request id exists for all responses.
- captures remote address, path, method, idempotency key, and token header.
- remote call with missing idempotency key is rejected when enabled.

### 2.5 Security baseline

- `LocalTokenPluginManagementAuthorizer.java`
  - Fixed-time token comparison.
  - Loopback enforcement when `allowLoopbackOnly=true`.
  - Return principal and permissions when valid.
- `DelegatingPluginManagementAuthorizer.java`
  - Use provided SPI authn/z for remote mode.

### 2.6 Management controller

- `PluginManagementController.java`
  - Read endpoints:
    - `GET /pf4boot/admin/plugins`
    - `GET /pf4boot/admin/plugins/{pluginId}`
    - `GET /pf4boot/admin/deployments/{deploymentId}`
    - `GET /pf4boot/admin/deployments`
  - Lifecycle endpoints:
    - `POST /plugins/{pluginId}/start|stop|restart|reload|enable`
    - `DELETE /plugins/{pluginId}/enable` (disable)
  - Deployment endpoints:
    - `POST /deployments/plan`
    - `POST /deployments/replace`
    - `POST /deployments/{deploymentId}/rollback`
    - `POST /deployments/{deploymentId}/confirm` (executes a prechecked record)
  - Mutations must run manager/deployment flows, not manual low-level duplication.

### 2.7 Idempotency / audit / response safety

- `PluginManagementPathValidator.java`
  - Reject absolute path / `..` leakage outside staging root.
- `PluginManagementIdempotencyService.java`
  - Same key + same hash -> replay cached result.
- `InMemoryPluginOperationStore.java` and `InMemoryPluginDeploymentRecordStore.java`
  - In-memory only for phase one.
- `PluginManagementAuditRecorder.java`, `LoggingPluginManagementAuditRecorder.java`
  - Record every success/failure/reject path.
- `PluginManagementExceptionHandler.java`
  - No stack traces in HTTP response.

## Phase 3: Verification Matrix

Run and collect:

```powershell
.\gradlew.bat :pf4boot-management-starter:compileJava
.\gradlew.bat :pf4boot-management-starter:compileTestJava
.\gradlew.bat :pf4boot-management-starter:test
``` 

Then perform code-check assertions:

- confirm `reload` maps to `@PostMapping` and does **not** call `PluginDeploymentService.replace`.
- confirm plan endpoint does not call `deploymentService.replace`.
  - confirm `/deployments/{deploymentId}/confirm` exists, requires write authz, and only executes `PRECHECKED` records.
- confirm startup validator blocks bad modes.
- confirm security policy for write operations in `PluginManagementWriteSecurityPolicy`.

Concrete test checkpoints:

- `PluginManagementWriteSecurityPolicyTest` (mode `auto`/`true`/`false` origin flow)
   - `PluginManagementControllerSecurityTest` (remote unauth/authz and rate limit for write/confirm endpoints)
- `PluginManagementRateLimiterTest` (normal/over-limit and disabled scenarios)
- `PluginManagementIdempotencyServiceTest` + `PluginManagementPathValidatorTest` (idempotency and path guard)

## Phase 3.5: Compatibility and Boundary Handoff (Small-Model Mandatory)

Before handoff, run these exact checks to avoid hidden coupling:

```powershell
rg --line-number "pf4boot-management-starter|javax.servlet:javax.servlet-api" pf4boot-starter/build.gradle pf4boot-web-starter/build.gradle
.\gradlew.bat :pf4boot-starter:test
```

Expected result:

- `pf4boot-starter` tests include `Pf4bootStarterCompatibilityTest`.
- The test class proves:
  - No management auto-configuration class on non-management classpath.
  - `javax.servlet.Servlet` is not loadable from `pf4boot-starter` test classpath.
- `build.gradle` check shows no optional/hidden transitive dependency leak.

If the environment cannot run Gradle due repository/network access, still update acceptance with a clear note:

- command blocked (network/buildscript dependency fetch blocked);
- only file-level and test-level checks already in git are considered completed.

## Phase 4: Acceptance Handoff

Keep these two docs as handoff source:

- `plugin-http-management-api-plan.md`
- `plugin-http-management-api-acceptance.md`

Do not claim `Not Done` for items that are implemented but untested.
Use status `In Progress` for runtime smoke that is not yet executed.

## Phase 5: Small-Model Execution Template

If context is limited, execute by this strict sequence:

1. `rg --line-number "plugin-management-starter|PluginManagementController|PluginManagementStartupValidator" docs/design/en docs/design plugin-management-starter pf4boot-management-starter pf4boot-api`
2. `.\gradlew.bat :pf4boot-management-starter:compileJava`
3. `.\gradlew.bat :pf4boot-management-starter:compileTestJava`
4. Run tests in this order:
   - `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementWriteSecurityPolicyTest"`
   - `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementControllerSecurityTest"`
   - `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementRateLimiterTest"`
   - `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementIdempotencyServiceTest"`
   - `.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementPathValidatorTest"`
5. Update acceptance rows immediately after each test result:
   - Add `Done` for each checked SEC/AC item.
   - Add `In Progress` for not-yet-ran smoke only.
6. Confirm follow-up behavior:
   - `rg --line-number "deployments/.*/confirm|PostMapping(\"/deployments/\\{deploymentId\\}/confirm\")" pf4boot-management-starter/src/main/java`
   - If endpoint is missing or does not enforce `PRECHECKED` state, keep `AC-16` as `In Progress` and record remediation.
