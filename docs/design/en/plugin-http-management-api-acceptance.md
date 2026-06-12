# Plugin HTTP Management API Acceptance Checklist

## Scope

This document tracks implementation evidence for [plugin-http-management-api.md](plugin-http-management-api.md) and [plugin-http-management-api-plan.md](plugin-http-management-api-plan.md).
As of this pass, core implementation is wired, compile checks pass, and security hardening for local and remote-style write-path checks is covered by unit tests. Full runtime smoke coverage is still pending.

Implementation should also follow [plugin-http-management-api-implementation-guide.md](plugin-http-management-api-implementation-guide.md). Evidence should keep module paths, class names, and test names aligned with the guide where possible.

## Functional Acceptance

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| AC-01 | HTTP mutation APIs are not registered by default | Done | `Pf4bootManagementProperties.enabled`, `Pf4bootManagementAutoConfiguration` condition |
| AC-02 | Invalid enabled security mode fails startup | Done | `PluginManagementStartupValidator.validate()` |
| AC-03 | Local token mode requires loopback and token | Done | `LocalTokenPluginManagementAuthorizer.authenticate()` |
| AC-04 | Remote mode without authorizer fails startup | Done | `PluginManagementStartupValidator.hasCustomAuthorizer()` |
| AC-05 | Plugin list/detail APIs are read-only | Done | `PluginManagementController.plugins`, `PluginManagementController.plugin` |
| AC-06 | start/stop/restart/enable/disable use semantic HTTP methods | Done | `@PostMapping`/`@DeleteMapping` in controller signatures |
| AC-07 | reload is documented as low-level fallback, not safe hot replacement | Done | `reload` endpoint remains dedicated and `replace` uses `PluginDeploymentService.replace` |
| AC-08 | Deployment plan does not mutate runtime state | Done | `PluginManagementController.plan` calls `PluginDeploymentService.planReplacement` flow only |
| AC-09 | Hot replacement calls `PluginDeploymentService.replace` | Done | `PluginManagementController.replace` |
| AC-10 | Staged package paths cannot escape configured roots | Done | `PluginManagementPathValidator.resolveStagedPath(...)` |
| AC-11 | Writes support idempotency keys and do not execute duplicates | Done | `PluginManagementIdempotencyService.begin(...)` |
| AC-12 | Same idempotency key with different body returns conflict | Done | `PluginManagementIdempotencyService.begin` conflict branch |
| AC-13 | Audit records cover success, failure, and rejected requests | Done | `PluginManagementController` + `PluginManagementAuditRecorder` + logging impl |
| AC-14 | Error responses do not leak tokens, sensitive paths, or full stacks | Done | `PluginManagementExceptionHandler` |
| AC-15 | `pf4boot-actuator` remains read-only | Done | `plugin-http-management-api.md` boundary and no actuator mutation endpoints |

## Security Acceptance

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| SEC-01 | Local write without token returns `401` | Done | `LocalTokenPluginManagementAuthorizer.isSameToken` + error code map |
| SEC-02 | Non-loopback request in local mode returns `401` | Done | `LocalTokenPluginManagementAuthorizer.authenticate` loopback branch |
| SEC-03 | Remote unauthenticated request returns `401` | Done | `PluginManagementControllerSecurityTest.remoteUnauthenticatedDelegatedRequestRejectedWith401` |
| SEC-04 | Remote unauthorized request returns `403` | Done | `PluginManagementControllerSecurityTest.remoteUnauthorizedDelegatedRequestRejectedWith403` |
| SEC-05 | Browser writes without CSRF/origin controls are rejected | Done | `PluginManagementWriteSecurityPolicyTest` + `PluginManagementControllerSecurityTest.csrfEnabledRequiresOriginForWriteRequests` |
| SEC-06 | Write rate limit returns `429` | Done | `PluginManagementRateLimiterTest` + `PluginManagementControllerSecurityTest.rateLimitAppliedBeforeSecondWrite` |
| SEC-07 | Public binding without remote authorization fails startup | Done | `PluginManagementStartupValidator` |

## Compatibility Acceptance

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| COMP-01 | Applications without management starter are unchanged | In Progress | Needs module-level integration test |
| COMP-02 | Existing `Pf4bootPluginManager` APIs are unchanged | Done | API surface unchanged; controller uses existing `Pf4bootPluginManager` APIs |
| COMP-03 | Non-web applications do not get servlet or management dependencies | In Progress | Starter remains optional |
| COMP-04 | Java 8 compilation passes | Done | `:pf4boot-management-starter:compileJava` |

## Suggested Verification Commands

```powershell
.\gradlew.bat :pf4boot-api:test
.\gradlew.bat :pf4boot-core:test
.\gradlew.bat :pf4boot-web-starter:test
.\gradlew.bat :pf4boot-management-starter:test
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

If the first implementation is hosted by `pf4boot-web-starter`, skip `:pf4boot-management-starter:test` and cover the cases in `:pf4boot-web-starter:test`.

## Manual Smoke

Add concrete commands after implementation:

- missing token rejected;
- valid token lists plugins;
- valid token starts/stops a plugin;
- staged package precheck;
- hot replacement and deployment record query;
- invalid token, missing permission, path traversal, and idempotency conflict.
