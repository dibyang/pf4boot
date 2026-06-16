# Plugin HTTP Management API Implementation Guide

## How To Use This Guide

This guide is written for implementation agents and developers. It reduces guesswork by fixing module names, packages, class names, file locations, and test names.

Read these first:

- [plugin-http-management-api.md](../plugin-http-management-api.md)
- [plugin-http-management-api-plan.md](plugin-http-management-api-plan.md)
- [plugin-http-management-api-acceptance.md](plugin-http-management-api-acceptance.md)
- [../../../constraints/README.md](../../../constraints/README.md)

Unless an implementation conflict is found, keep the names in this guide.

## Fixed Decisions

- Add a standalone module: `pf4boot-management-starter`.
- Do not add write APIs to `pf4boot-actuator`.
- Do not restore old management APIs in `pf4boot-web-starter`; migrate or deprecate old `PluginManagerController` later.
- Management APIs are disabled by default: `spring.pf4boot.management.http.enabled=false`.
- Local mode still requires a token.
- Release-grade hot replacement must call `PluginDeploymentService.replace(...)`.
- Low-level `reloadPlugin` is only an operational fallback and requires `pf4boot:plugin:reload`.

## Module And Gradle Changes

### `settings.gradle`

Add:

```groovy
include 'pf4boot-management-starter'
```

### `pf4boot-management-starter/build.gradle`

Suggested content:

```groovy
dependencies {
    implementation project(":pf4boot-api")
    implementation project(":pf4boot-core")
    implementation project(":pf4boot-web-support")
    compileOnly 'javax.servlet:javax.servlet-api:3.1.0'
    compileOnly("net.xdob.springframework.boot:spring-boot-autoconfigure:$spring_boot_version")
    testImplementation("net.xdob.springframework:spring-test:$spring_version")
    testImplementation 'javax.servlet:javax.servlet-api:3.1.0'
}
```

If `@ConfigurationProperties` or `@ConditionalOnProperty` is unavailable, follow existing starter dependency patterns. Do not add `spring-boot-starter-web`.

`pf4boot-management-starter` is publishable and must not be added to the root sample exclusion list.

## Packages And Files

### `pf4boot-api`

Directory: `pf4boot-api/src/main/java/net/xdob/pf4boot/management`

| Class / Interface | Kind | Purpose |
| --- | --- | --- |
| `PluginAdminResponse<T>` | class | Unified HTTP response |
| `PluginManagementMode` | enum | `DISABLED`, `LOCAL_TOKEN`, `REMOTE_DELEGATED` |
| `PluginManagementOperation` | enum | Operation and permission mapping |
| `PluginManagementErrorCode` | enum | `PFM-001` to `PFM-009` |
| `PluginManagementRequest` | class | Auth input summary |
| `PluginManagementPrincipal` | class | Caller principal |
| `PluginManagementAuthorizer` | interface | Authn/authz SPI |
| `PluginManagementAuditEvent` | class | Audit event |
| `PluginOperationRecord` | class | Idempotent operation record |
| `PluginOperationStore` | interface | Idempotency store SPI |

### `pf4boot-management-starter`

Directory: `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter`

| Class / Interface | Kind | Purpose |
| --- | --- | --- |
| `Pf4bootManagementAutoConfiguration` | configuration | Auto-configuration |
| `Pf4bootManagementProperties` | properties | Configuration properties |
| `PluginManagementStartupValidator` | class | Startup validation |
| `PluginManagementController` | rest controller | HTTP entrypoint |
| `PluginManagementRequestContext` | class | Per-request context |
| `PluginManagementRequestFactory` | class | Build request model from servlet request |
| `LocalTokenPluginManagementAuthorizer` | class | Local token auth |
| `DelegatingPluginManagementAuthorizer` | class | Remote delegated auth wrapper |
| `PluginManagementIdempotencyService` | class | Idempotency checks |
| `InMemoryPluginOperationStore` | class | Phase-one in-memory store |
| `PluginManagementAuditRecorder` | interface | Audit SPI |
| `LoggingPluginManagementAuditRecorder` | class | Default logging audit |
| `PluginManagementPathValidator` | class | Staged path validation |
| `PluginManagementExceptionHandler` | controller advice | Unified error responses |

Resource:

```text
pf4boot-management-starter/src/main/resources/META-INF/spring.factories
```

Content:

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
net.xdob.pf4boot.management.starter.Pf4bootManagementAutoConfiguration
```

## Configuration Fields

`Pf4bootManagementProperties` prefix:

```java
public static final String PREFIX = "spring.pf4boot.management.http";
```

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | Register management HTTP APIs |
| `basePath` | `String` | `/pf4boot/admin` | Controller base path |
| `mode` | `PluginManagementMode` | `DISABLED` | Security mode |
| `allowLoopbackOnly` | `boolean` | `true` | Restrict local mode to loopback |
| `token` | `String` | empty | Local management token |
| `tokenHeader` | `String` | `X-PF4Boot-Admin-Token` | Token header |
| `requireIdempotencyKey` | `boolean` | `true` | Require idempotency key for writes |
| `idempotencyHeader` | `String` | `X-Idempotency-Key` | Idempotency header |
| `dryRunDefault` | `boolean` | `true` | Default dry-run behavior |
| `auditEnabled` | `boolean` | `true` | Enable audit |
| `stagingRoot` | `String` | `plugins/staged` | Staged package root |
| `rateLimit.enabled` | `boolean` | `true` | Enable rate limit |
| `rateLimit.writesPerMinute` | `int` | `30` | Write limit per minute |
| `csrf.enabled` | `String` | `auto` | `auto`, `true`, `false` |

## Minimal API Structures

### `PluginAdminResponse<T>`

Fields:

```java
private boolean success;
private String requestId;
private String operationId;
private String code;
private String message;
private T data;
private List<String> warnings;
```

Requirements:

- Static factories: `ok(...)` and `failed(...)`.
- `warnings` defaults to an empty list.
- Do not store exception objects.

### `PluginManagementOperation`

Values:

```java
PLUGIN_READ("pf4boot:plugin:read"),
PLUGIN_START("pf4boot:plugin:lifecycle"),
PLUGIN_STOP("pf4boot:plugin:lifecycle"),
PLUGIN_RESTART("pf4boot:plugin:lifecycle"),
PLUGIN_ENABLE("pf4boot:plugin:lifecycle"),
PLUGIN_DISABLE("pf4boot:plugin:lifecycle"),
PLUGIN_RELOAD("pf4boot:plugin:reload"),
DEPLOYMENT_PLAN("pf4boot:deployment:plan"),
DEPLOYMENT_REPLACE("pf4boot:deployment:replace"),
DEPLOYMENT_CONFIRM("pf4boot:deployment:confirm"),
DEPLOYMENT_ROLLBACK("pf4boot:deployment:rollback");
```

### `PluginManagementErrorCode`

| Enum | code | HTTP |
| --- | --- | --- |
| `INVALID_REQUEST` | `PFM-001` | `400` |
| `UNAUTHENTICATED` | `PFM-002` | `401` |
| `FORBIDDEN` | `PFM-003` | `403` |
| `NOT_FOUND` | `PFM-004` | `404` |
| `CONFLICT` | `PFM-005` | `409` |
| `RATE_LIMITED` | `PFM-006` | `429` |
| `PRECHECK_FAILED` | `PFM-007` | `422` |
| `OPERATION_FAILED` | `PFM-008` | `500` |
| `UNAVAILABLE` | `PFM-009` | `503` |

## Controller Method Draft

Use:

```java
@RequestMapping("${spring.pf4boot.management.http.base-path:/pf4boot/admin}")
```

Draft methods:

```java
@GetMapping("/plugins")
public PluginAdminResponse<List<PluginRuntimeSnapshot>> plugins(HttpServletRequest request)

@GetMapping("/plugins/{pluginId}")
public PluginAdminResponse<PluginRuntimeSnapshot> plugin(@PathVariable String pluginId, HttpServletRequest request)

@PostMapping("/plugins/{pluginId}/start")
public PluginAdminResponse<PluginRuntimeSnapshot> start(@PathVariable String pluginId, HttpServletRequest request)

@PostMapping("/plugins/{pluginId}/stop")
public PluginAdminResponse<PluginRuntimeSnapshot> stop(@PathVariable String pluginId, HttpServletRequest request)

@PostMapping("/plugins/{pluginId}/restart")
public PluginAdminResponse<PluginRuntimeSnapshot> restart(@PathVariable String pluginId, HttpServletRequest request)

@PostMapping("/plugins/{pluginId}/reload")
public PluginAdminResponse<PluginRuntimeSnapshot> reload(@PathVariable String pluginId, HttpServletRequest request)

@PostMapping("/plugins/{pluginId}/enable")
public PluginAdminResponse<PluginRuntimeSnapshot> enable(@PathVariable String pluginId, HttpServletRequest request)

@DeleteMapping("/plugins/{pluginId}/enable")
public PluginAdminResponse<PluginRuntimeSnapshot> disable(@PathVariable String pluginId, HttpServletRequest request)

@PostMapping("/deployments/plan")
public PluginAdminResponse<DeploymentRecord> plan(@RequestBody PluginDeploymentRequest body, HttpServletRequest request)

@PostMapping("/deployments/replace")
public PluginAdminResponse<DeploymentRecord> replace(@RequestBody PluginDeploymentRequest body, HttpServletRequest request)

@GetMapping("/deployments/{deploymentId}")
public PluginAdminResponse<DeploymentRecord> deployment(@PathVariable String deploymentId, HttpServletRequest request)

@PostMapping("/deployments/{deploymentId}/rollback")
public PluginAdminResponse<DeploymentRecord> rollback(@PathVariable String deploymentId, HttpServletRequest request)

@PostMapping("/deployments/{deploymentId}/confirm")
public PluginAdminResponse<DeploymentRecord> confirm(@PathVariable String deploymentId, HttpServletRequest request)
```

If `PluginDeploymentRecorder` cannot query by deployment id yet, return a clear `PFM-007` or `PFM-009` and leave the acceptance item incomplete.

## Request DTO

Directory: `pf4boot-api/src/main/java/net/xdob/pf4boot/management`

```java
public class PluginDeploymentRequest {
  private String pluginId;
  private String stagedPluginPath;
  private Boolean dryRun;
}
```

Rules:

- `pluginId` is required.
- `stagedPluginPath` is required and must stay under the staging root after normalization.
- `dryRun == null` means use `Pf4bootManagementProperties.dryRunDefault`.

## Critical Implementation Rules

### Request Processing Order

All writes must follow this order:

1. Create `requestId`.
2. Build `PluginManagementRequest`.
3. Check source address.
4. Authenticate.
5. Authorize.
6. Apply rate limit.
7. Check idempotency key.
8. Validate parameters and paths.
9. Call the manager or deployment service.
10. Store idempotency result.
11. Record audit event.
12. Return unified response.

Do not inspect staged package content before authentication and authorization.

### Path Validation

Use `Path.normalize()` and `toAbsolutePath()`. The normalized target path must still start with the normalized staging root.

Forbidden:

- arbitrary absolute paths;
- `..` traversal;
- shell command construction from user input;
- file move/delete directly inside the controller.

### Token Comparison

Use fixed-time comparison:

```java
MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))
```

Reject nulls before comparison or normalize to empty strings.

### Loopback Check

Use `HttpServletRequest.getRemoteAddr()` and `InetAddress.getByName(remoteAddr).isLoopbackAddress()`.

Test:

- `127.0.0.1`
- `::1`
- `localhost` if returned by the servlet container
- non-loopback address

### Idempotency Store

`InMemoryPluginOperationStore` uses `ConcurrentHashMap`.

Key:

```text
principalId + ":" + operation.name() + ":" + pluginId + ":" + idempotencyKey
```

Record fields:

- `requestHash`
- `state`
- `responseCode`
- `responseBodySummary`
- `createdAt`
- `updatedAt`

Phase one may omit TTL cleanup, but class docs must state this is an in-memory implementation.

## JSON Examples

### Start Success

```json
{
  "success": true,
  "requestId": "req-001",
  "operationId": "op-001",
  "code": "OK",
  "message": "Plugin started",
  "data": {
    "pluginId": "plugin-user-book-service",
    "state": "STARTED"
  },
  "warnings": []
}
```

### Missing Token

```json
{
  "success": false,
  "requestId": "req-002",
  "operationId": null,
  "code": "PFM-002",
  "message": "Management token is missing or invalid",
  "data": null,
  "warnings": []
}
```

### Idempotency Conflict

```json
{
  "success": false,
  "requestId": "req-003",
  "operationId": "op-previous",
  "code": "PFM-005",
  "message": "Idempotency key was reused with a different request",
  "data": null,
  "warnings": []
}
```

### Precheck Failure

```json
{
  "success": false,
  "requestId": "req-004",
  "operationId": "op-004",
  "code": "PFM-007",
  "message": "Deployment precheck failed",
  "data": {
    "state": "REJECTED"
  },
  "warnings": [
    "Target plugin id does not match staged package descriptor"
  ]
}
```

## Tests

### `pf4boot-api`

Directory: `pf4boot-api/src/test/java/net/xdob/pf4boot/management`

| Test Class | Cases |
| --- | --- |
| `PluginAdminResponseTest` | `okResponseDefaultsWarningsToEmptyList`, `failedResponseDoesNotExposeThrowable` |
| `PluginManagementErrorCodeTest` | `mapsErrorCodeToHttpStatus` |
| `PluginManagementOperationTest` | `mapsOperationToPermission` |

### `pf4boot-management-starter`

Directory: `pf4boot-management-starter/src/test/java/net/xdob/pf4boot/management/starter`

| Test Class | Cases |
| --- | --- |
| `Pf4bootManagementAutoConfigurationTest` | `disabledByDefaultDoesNotRegisterController`, `localTokenModeRequiresToken`, `remoteModeRequiresAuthorizer` |
| `LocalTokenPluginManagementAuthorizerTest` | `rejectsMissingToken`, `rejectsInvalidToken`, `acceptsValidLoopbackToken`, `rejectsNonLoopbackAddress` |
| `PluginManagementPathValidatorTest` | `acceptsPathUnderStagingRoot`, `rejectsPathTraversal`, `rejectsAbsolutePathOutsideStagingRoot` |
| `PluginManagementIdempotencyServiceTest` | `replaysSameRequest`, `rejectsSameKeyDifferentRequest`, `doesNotExecuteDuplicateOperation` |
| `PluginManagementControllerTest` | `listPluginsIsReadOnly`, `startPluginRequiresPost`, `stopPluginRequiresPost`, `planDoesNotMutateRuntime`, `replaceUsesDeploymentService` |
| `PluginManagementExceptionHandlerTest` | `doesNotExposeStackTrace`, `doesNotExposeToken` |

## Forbidden Changes

- Do not add `@WriteOperation`, `@DeleteOperation`, or lifecycle write APIs to `pf4boot-actuator`.
- Do not use GET for state changes.
- Do not call `reloadPlugin` as hot replacement from the controller.
- Do not accept arbitrary absolute paths from users.
- Do not construct shell commands in the HTTP layer.
- Do not return tokens, full stack traces, environment variables, or sensitive absolute paths.
- Do not add Spring Security as a mandatory core dependency.
- Do not use Java 9+ APIs, record, var, or `Stream.toList`.

## Minimal Implementation Steps

1. Add the module and `spring.factories`; verify default disabled behavior.
2. Add `pf4boot-api` management models and tests.
3. Add properties, startup validation, and local token authorizer.
4. Add read-only controller APIs.
5. Add start/stop/restart/enable/disable.
6. Add deployment plan/replace.
7. Add path validation, idempotency, and audit.
8. Add sample configuration and README.
9. Update acceptance evidence.

Run the relevant module tests after each step.

## Small-Model Execution Checklist (Recommended)

When work is handed to a smaller model, use this exact sequence:

1. Confirm repository write target
   - `settings.gradle`: module include list.
   - `pf4boot-management-starter/build.gradle`: compile/runtime dependencies.

2. Add APIs first (no business logic)
   - `pf4boot-api/src/main/java/net/xdob/pf4boot/management`.
   - Add or validate these files exist:
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

3. Scaffold auto-configuration wiring
   - `pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter/Pf4bootManagementAutoConfiguration.java`
   - Add `META-INF/spring.factories`.
   - Wire beans with `@ConditionalOnProperty(prefix="spring.pf4boot.management.http", name="enabled", havingValue="true")`.

4. Implement startup guardrails
   - `PluginManagementStartupValidator.java`: fail fast for invalid startup combination.
   - `Pf4bootManagementProperties.java`: ensure property defaults and prefix.

5. Implement security baseline
   - `LocalTokenPluginManagementAuthorizer.java`:
     - fixed-time token compare
     - loopback check for local mode
     - return read/lifecycle/replace/deploy permissions
   - `DelegatingPluginManagementAuthorizer.java`: merge injected SPI authorizers for remote mode.
   - `PluginManagementRequestFactory.java`: parse request id, method, path, headers, origin, token.

6. Implement controller core
   - `PluginManagementController.java`:
     - read APIs: `GET /plugins`, `GET /plugins/{pluginId}`.
     - lifecycle: `POST` start/stop/restart/reload, `DELETE` disable.
     - deployment: `POST /deployments/plan`, `POST /deployments/replace`, `GET /deployments/{id}`, `POST /deployments/{id}/rollback`, `POST /deployments/{id}/confirm`.
   - Keep `reload` as low-level path and never use it as replacement flow.

7. Add safe operation primitives
   - `PluginManagementPathValidator` for staged path traversal guard.
   - `PluginManagementIdempotencyService` and `InMemoryPluginOperationStore` for idempotent replays.
   - `PluginManagementAuditRecorder` + `LoggingPluginManagementAuditRecorder`.
   - `PluginManagementExceptionHandler` for unified errors and no stacktrace leak.

8. Verify and package
   - `.\gradlew.bat :pf4boot-management-starter:compileJava`
   - `.\gradlew.bat :pf4boot-management-starter:compileTestJava`
   - `.\gradlew.bat :pf4boot-management-starter:processResources` (to verify spring.factories resources are packaged).

9. Sync plan/acceptance
   - Update `plugin-http-management-api-plan.md` and `plugin-http-management-api-acceptance.md` with
     milestone and AC status before handoff.

### Known follow-up items

- `POST /deployments/{deploymentId}/confirm` is implemented as "execute precheck plan" in this phase.

### Common mistakes to avoid

- Do not expose GET for start/stop/restart/disable APIs.
- Do not call `reloadPlugin` for safe replacement.
- Do not validate staged package path after auth; validate after loopback/token/permission checks.
- Do not store raw token or absolute filesystem path in response bodies.
