# Plugin HTTP Management API - Small-Model Execution Guide

This document is a strict execution guide for limited-context models.

## 1) Scope Boundary (Do not exceed)

Allowed edit scope:
- `pf4boot-api`
- `pf4boot-core`
- `pf4boot-management-starter`
- `docs/design` (design/docs files only)

Do not edit:
- `samples/*` (unless explicitly assigned)
- `README.md` or top-level docs not in scope
- dependency versions or plugin IDs in unrelated modules

## 2) Mandatory Read Before Changes

Open these files first:
- `docs/design/plugin-http-management-api.md`
- `docs/design/plugin-http-management-api-plan.md`
- `docs/design/plugin-http-management-api-acceptance.md`
- `docs/design/en/plugin-http-management-api-implementation-guide.md`
- `docs/design/en/plugin-http-management-api-small-model-guide.md`

## 3) File Checklist

### 3.1 API contract
- `pf4boot-api/src/main/java/net/xdob/pf4boot/management/PluginAdminResponse.java`
- `pf4boot-api/src/main/java/net/xdob/pf4boot/management/PluginManagementMode.java`
- `pf4boot-api/src/main/java/net/xdob/pf4boot/management/PluginManagementOperation.java`
- `pf4boot-api/src/main/java/net/xdob/pf4boot/management/PluginManagementErrorCode.java`
- `pf4boot-api/src/main/java/net/xdob/pf4boot/management/PluginManagementRequest.java`
- `pf4boot-api/src/main/java/net/xdob/pf4boot/management/PluginManagementPrincipal.java`
- `pf4boot-api/src/main/java/net/xdob/pf4boot/management/PluginManagementAuthorizer.java`
- `pf4boot-api/src/main/java/net/xdob/pf4boot/management/PluginManagementAuditEvent.java`

### 3.2 Starter and controller
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/PluginManagementController.java`
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/PluginManagementAutoConfiguration.java` (if exists)
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/PluginManagementWriteSecurityPolicy.java`
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/PluginManagementStartupValidator.java`

### 3.3 Security and safety
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/LocalTokenPluginManagementAuthorizer.java`
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/DelegatingPluginManagementAuthorizer.java`
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/PluginManagementRateLimiter.java`
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/PluginManagementIdempotencyService.java`
- `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/PluginManagementPathValidator.java`

## 4) Execution Order (fixed)

### Phase A: Baseline compile

```powershell
.\gradlew.bat :pf4boot-management-starter:compileJava
.\gradlew.bat :pf4boot-management-starter:compileTestJava
```

### Phase B: Security tests (before full test)

```powershell
.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementWriteSecurityPolicyTest"
.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementControllerSecurityTest"
.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementRateLimiterTest"
```

### Phase C: Functionality tests

```powershell
.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementIdempotencyServiceTest"
.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementPathValidatorTest"
.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementControllerTest"
```

## 5) Strict evidence mapping

Update acceptance immediately after each test run:
- For each passing assertion, mark corresponding `AC-xx / SEC-xx / COMP-xx` as `Done`.
- For follow-up items (e.g., `AC-16` confirm endpoint), keep `Pending`.
- Do not mark `Done` without code-level or test-level evidence.

## 6) Additional deterministic checks

Run:

```powershell
rg --line-number "deployments/.*/confirm|PostMapping\\(\"/deployments/\\{deploymentId\\}/confirm\"\\)" pf4boot-management-starter/src/main/java
.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementControllerSecurityTest.csrfEnabledRequiresOriginForRollbackWrite"
.\gradlew.bat :pf4boot-management-starter:test --tests "net.xdob.pf4boot.management.starter.PluginManagementControllerSecurityTest.rateLimitAppliedBeforeSecondRollback"
```

## 7) Acceptance handoff rule

Before handoff, ensure these docs are aligned:
- `docs/design/plugin-http-management-api-plan.md`
- `docs/design/plugin-http-management-api-acceptance.md`

If runtime smoke is still not executed, set smoke section status as `In Progress` instead of `Done`.
