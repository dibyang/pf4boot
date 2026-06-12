# Plugin HTTP Management API Acceptance Checklist

## Scope

This document tracks implementation evidence for [plugin-http-management-api.md](plugin-http-management-api.md) and [plugin-http-management-api-plan.md](plugin-http-management-api-plan.md). It is currently in planning state, so all items are incomplete.

Implementation should also follow [plugin-http-management-api-implementation-guide.md](plugin-http-management-api-implementation-guide.md). Evidence should keep module paths, class names, and test names aligned with the guide where possible.

## Functional Acceptance

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| AC-01 | HTTP mutation APIs are not registered by default | Not Done | TBD |
| AC-02 | Invalid enabled security mode fails startup | Not Done | TBD |
| AC-03 | Local token mode requires loopback and token | Not Done | TBD |
| AC-04 | Remote mode without authorizer fails startup | Not Done | TBD |
| AC-05 | Plugin list/detail APIs are read-only | Not Done | TBD |
| AC-06 | start/stop/restart/enable/disable use semantic HTTP methods | Not Done | TBD |
| AC-07 | reload is documented as low-level fallback, not safe hot replacement | Not Done | TBD |
| AC-08 | Deployment plan does not mutate runtime state | Not Done | TBD |
| AC-09 | Hot replacement calls `PluginDeploymentService.replace` | Not Done | TBD |
| AC-10 | Staged package paths cannot escape configured roots | Not Done | TBD |
| AC-11 | Writes support idempotency keys and do not execute duplicates | Not Done | TBD |
| AC-12 | Same idempotency key with different body returns conflict | Not Done | TBD |
| AC-13 | Audit records cover success, failure, and rejected requests | Not Done | TBD |
| AC-14 | Error responses do not leak tokens, sensitive paths, or full stacks | Not Done | TBD |
| AC-15 | `pf4boot-actuator` remains read-only | Not Done | TBD |

## Security Acceptance

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| SEC-01 | Local write without token returns `401` | Not Done | TBD |
| SEC-02 | Non-loopback request in local mode returns `403` | Not Done | TBD |
| SEC-03 | Remote unauthenticated request returns `401` | Not Done | TBD |
| SEC-04 | Remote unauthorized request returns `403` | Not Done | TBD |
| SEC-05 | Browser writes without CSRF/origin controls are rejected | Not Done | TBD |
| SEC-06 | Write rate limit returns `429` | Not Done | TBD |
| SEC-07 | Public binding without remote authorization fails startup | Not Done | TBD |

## Compatibility Acceptance

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| COMP-01 | Applications without management starter are unchanged | Not Done | TBD |
| COMP-02 | Existing `Pf4bootPluginManager` APIs are unchanged | Not Done | TBD |
| COMP-03 | Non-web applications do not get servlet or management dependencies | Not Done | TBD |
| COMP-04 | Java 8 compilation passes | Not Done | TBD |

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
