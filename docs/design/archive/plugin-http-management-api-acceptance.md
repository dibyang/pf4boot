# Plugin HTTP Management API Acceptance Checklist

## Scope

This document tracks implementation evidence for:
- [plugin-http-management-api.md](../plugin-http-management-api.md)
- [plugin-http-management-api-plan.md](plugin-http-management-api-plan.md)
- [plugin-http-management-api-implementation-guide.md](plugin-http-management-api-implementation-guide.md)

As of this pass, the HTTP management core is implemented, module wiring is in place, and security hardening on write APIs (including rollback) is covered in tests.

## Functional Acceptance

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| AC-01 | HTTP management APIs are not enabled by default | Done | `Pf4bootManagementProperties.enabled`, `Pf4bootManagementAutoConfiguration` condition |
| AC-02 | Invalid startup mode fails fast | Done | `PluginManagementStartupValidator.validate()` |
| AC-03 | Local token mode requires loopback + token | Done | `LocalTokenPluginManagementAuthorizer.authenticate()` |
| AC-04 | Remote mode requires custom authorizer | Done | `PluginManagementStartupValidator.hasCustomAuthorizer()` |
| AC-05 | Plugin list and detail APIs are read-only | Done | `PluginManagementController.plugins`, `PluginManagementController.plugin` |
| AC-06 | Lifecycle endpoints use semantic methods | Done | `@PostMapping` / `@DeleteMapping` in controller |
| AC-07 | `reload` is documented as low-level fallback, not hot replacement | Done | `reload` endpoint remains dedicated; `replace` uses `PluginDeploymentService.replace` |
| AC-08 | Deployment plan does not mutate runtime state | Done | `PluginManagementController.plan` -> `PluginDeploymentService.planReplacement` |
| AC-09 | Hot replacement calls deployment service | Done | `PluginManagementController.replace` |
| AC-10 | Staged plugin path is root-safe | Done | `PluginManagementPathValidator.resolveStagedPath(...)` |
| AC-11 | Idempotency key deduplicates duplicate writes | Done | `PluginManagementIdempotencyService.begin(...)` |
| AC-12 | Duplicate key + different request body returns conflict | Done | `PluginManagementIdempotencyService.begin` conflict branch |
| AC-13 | Audit records success / failure / reject paths | Done | `PluginManagementController` + `PluginManagementControllerTest.writeSecurityRejectionIsAudited` |
| AC-14 | Error responses do not leak token/path/stack details | Done | `PluginManagementExceptionHandlerTest` |
| AC-15 | `pf4boot-actuator` remains mutation-free | Done | API doc boundary in `plugin-http-management-api.md` and implementation scope |
| AC-16 | Manual confirm endpoint is implemented and enforces write policies | Done | `PluginManagementController.confirm` and tests `PluginManagementControllerTest.confirmEndpointExecutesReplacementForPrecheckedRecord`, `PluginManagementControllerSecurityTest.csrfEnabledRequiresOriginForConfirmWrite`, `PluginManagementControllerSecurityTest.confirmRequiresDedicatedConfirmPermission` |

## Security Acceptance

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| SEC-01 | Missing/invalid token returns `401` | Done | `LocalTokenPluginManagementAuthorizer.isSameToken` + error mapping |
| SEC-02 | Non-loopback local request returns `401/403` | Done | `LocalTokenPluginManagementAuthorizer.authenticate` loopback branch |
| SEC-03 | Remote unauthenticated request returns `401` | Done | `PluginManagementControllerSecurityTest.remoteUnauthenticatedDelegatedRequestRejectedWith401` |
| SEC-04 | Remote unauthorized request returns `403` | Done | `PluginManagementControllerSecurityTest.remoteUnauthorizedDelegatedRequestRejectedWith403` |
| SEC-05 | Browser write request without CSRF/origin is rejected | Done | `PluginManagementWriteSecurityPolicyTest`, `PluginManagementControllerSecurityTest.csrfEnabledRequiresOriginForWriteRequests` |
| SEC-06 | Write endpoint returns `429` when rate limit exceeded | Done | `PluginManagementRateLimiterTest`, `PluginManagementControllerSecurityTest.rateLimitAppliedBeforeSecondWrite` |
| SEC-07 | Rollback endpoint also enforces CSRF/origin policy | Done | `PluginManagementControllerSecurityTest.csrfEnabledRequiresOriginForRollbackWrite` |
| SEC-08 | Rollback endpoint also enforces write-rate limiting | Done | `PluginManagementControllerSecurityTest.rateLimitAppliedBeforeSecondRollback` |
| SEC-09 | Startup rejects public binding without remote authorization | Done | `PluginManagementStartupValidator` |

## Compatibility Acceptance

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| COMP-01 | Applications without management starter remain unchanged | Done | `Pf4bootStarterCompatibilityTest` asserts missing auto-config class |
| COMP-02 | Existing `Pf4bootPluginManager` API unchanged | Done | No API surface changes; controller calls existing methods |
| COMP-03 | Non-web app does not expose servlet/management dependencies | Done | `Pf4bootStarterCompatibilityTest.nonWebStarterDoesNotExposeServletApi`; `pf4boot-starter/build.gradle` |
| COMP-04 | Java 8 compilation passes | Done | `:pf4boot-management-starter:compileJava` |

## Verification Commands

```powershell
.\gradlew.bat :pf4boot-starter:test
.\gradlew.bat :pf4boot-api:test
.\gradlew.bat :pf4boot-core:test
.\gradlew.bat :pf4boot-web-starter:test
.\gradlew.bat :pf4boot-management-starter:test
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

If the first implementation is hosted by `pf4boot-web-starter`, skip `:pf4boot-management-starter:test` and validate in `:pf4boot-web-starter:test`.

## Manual Smoke

- `curl -I -H "X-PF4Boot-Admin-Token: sample-token" http://127.0.0.1:7791/pf4boot/admin/plugins`
- `curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" http://127.0.0.1:7791/pf4boot/admin/plugins/sample-workflow/start`
- `curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" -H "Content-Type: application/json" -d '{"pluginId":"sample-workflow","stagedPluginPath":"build/sample-plugins/plugin-workflow-3.0.0-SNAPSHOT.zip","dryRun":true}' http://127.0.0.1:7791/pf4boot/admin/deployments/plan`
- `curl -X GET -H "X-PF4Boot-Admin-Token: sample-token" http://127.0.0.1:7791/pf4boot/admin/deployments`
- `curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" -H "Content-Type: application/json" -d '{"deploymentId":"D-rollback","idempotencyKey":"idem-key-1"}' http://127.0.0.1:7791/pf4boot/admin/deployments/{id}/rollback` (replace `{id}` with valid id)
- `curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" http://127.0.0.1:7791/pf4boot/admin/deployments/{id}/confirm` (replace `{id}` with a `PRECHECKED` deployment id returned by plan)
- `curl -H "X-PF4Boot-Admin-Token: sample-token" -X POST http://127.0.0.1:7791/pf4boot/admin/plugins/sample-workflow/enable` (run twice to validate idempotency semantics)
- `curl -H "X-PF4Boot-Admin-Token: sample-token" -X DELETE http://127.0.0.1:7791/pf4boot/admin/plugins/sample-workflow/enable` (run twice to validate idempotency semantics)
- Local-token negative test: omit token and verify `401`
- Remote-delegated negative tests for unauth and insufficient permission cases

- Rollback CSRF check smoke (browser path): set `X-PF4Boot-Write-Enabled=true` and missing browser `Origin` header should return `403` for rollback endpoint.

## Reference

- `samples/cross-plugin-jpa/README.md`
