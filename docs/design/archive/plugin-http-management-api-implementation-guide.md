# 插件 HTTP 管理接口实施指南

## 使用方式

本文面向实施模型或开发者，目标是减少自由推断。实现前先阅读：

- [plugin-http-management-api.md](../plugin-http-management-api.md)
- [plugin-http-management-api-plan.md](plugin-http-management-api-plan.md)
- [plugin-http-management-api-acceptance.md](plugin-http-management-api-acceptance.md)
- [../../constraints/README.md](../../constraints/README.md)

本文的类名、包名、文件路径和测试名是建议落地形态。除非实现时发现与现有代码冲突，否则不要改名。

## 固定决策

- 新增独立模块：`pf4boot-management-starter`。
- 不在 `pf4boot-actuator` 中增加写接口。
- 不在 `pf4boot-web-starter` 中恢复旧管理接口能力；旧 `PluginManagerController` 后续应迁移或废弃。
- 管理接口默认关闭：`spring.pf4boot.management.http.enabled=false`。
- 本地模式也必须 token，不提供“无 token 本地调试模式”。
- 发布级热替换只能调用 `PluginDeploymentService.replace(...)`。
- 低层 `reloadPlugin` 只能作为本地/运维兜底接口，并使用独立权限 `pf4boot:plugin:reload`。

## 模块与 Gradle 改动

### `settings.gradle`

新增：

```groovy
include 'pf4boot-management-starter'
```

### `pf4boot-management-starter/build.gradle`

建议内容：

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

如果编译发现 `@ConfigurationProperties` 或 `@ConditionalOnProperty` 不可见，可以参考现有 starter 的依赖方式补充 Spring Boot autoconfigure 依赖，但不要引入 `spring-boot-starter-web`。

### 发布策略

`pf4boot-management-starter` 是正式 starter，不应加入根 `build.gradle` 的 sample 排除列表。

## 包名与文件清单

### `pf4boot-api`

目录：`pf4boot-api/src/main/java/net/xdob/pf4boot/management`

| 类/接口 | 类型 | 说明 |
| --- | --- | --- |
| `PluginAdminResponse<T>` | class | HTTP 统一响应 |
| `PluginManagementMode` | enum | `DISABLED`、`LOCAL_TOKEN`、`REMOTE_DELEGATED` |
| `PluginManagementOperation` | enum | 管理操作类型和权限点 |
| `PluginManagementErrorCode` | enum | `PFM-001` 到 `PFM-009` |
| `PluginManagementRequest` | class | 认证授权输入摘要 |
| `PluginManagementPrincipal` | class | 调用主体 |
| `PluginManagementAuthorizer` | interface | 认证授权 SPI |
| `PluginManagementAuditEvent` | class | 审计事件 |
| `PluginOperationRecord` | class | 幂等操作记录 |
| `PluginOperationStore` | interface | 幂等记录存储 SPI |

### `pf4boot-management-starter`

目录：`pf4boot-management-starter/src/main/java/net/xdob/pf4boot/management/starter`

| 类/接口 | 类型 | 说明 |
| --- | --- | --- |
| `Pf4bootManagementAutoConfiguration` | configuration | 管理接口自动配置 |
| `Pf4bootManagementProperties` | properties | 配置属性 |
| `PluginManagementStartupValidator` | class | 启动配置校验 |
| `PluginManagementController` | rest controller | HTTP 入口 |
| `PluginManagementRequestContext` | class | 单次请求上下文 |
| `PluginManagementRequestFactory` | class | 从 servlet request 构造管理请求 |
| `LocalTokenPluginManagementAuthorizer` | class | 本地 token 模式认证授权 |
| `DelegatingPluginManagementAuthorizer` | class | 远程模式委托 authorizer |
| `PluginManagementIdempotencyService` | class | 幂等校验 |
| `InMemoryPluginOperationStore` | class | 第一阶段内存幂等存储 |
| `PluginManagementAuditRecorder` | interface | 审计记录 SPI |
| `LoggingPluginManagementAuditRecorder` | class | 默认日志审计 |
| `PluginManagementPathValidator` | class | staged 路径校验 |
| `PluginManagementExceptionHandler` | controller advice | 统一错误响应 |

资源文件：

```text
pf4boot-management-starter/src/main/resources/META-INF/spring.factories
```

内容：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
net.xdob.pf4boot.management.starter.Pf4bootManagementAutoConfiguration
```

## 配置类字段

`Pf4bootManagementProperties` 使用前缀：

```java
public static final String PREFIX = "spring.pf4boot.management.http";
```

字段表：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | 是否注册管理 HTTP 写接口 |
| `basePath` | `String` | `/pf4boot/admin` | Controller 基础路径 |
| `mode` | `PluginManagementMode` | `DISABLED` | 安全模式 |
| `allowLoopbackOnly` | `boolean` | `true` | 本地模式是否只允许 loopback |
| `token` | `String` | 空 | 本地管理 token |
| `tokenHeader` | `String` | `X-PF4Boot-Admin-Token` | token header |
| `requireIdempotencyKey` | `boolean` | `true` | 写操作是否强制幂等 key |
| `idempotencyHeader` | `String` | `X-Idempotency-Key` | 幂等 header |
| `dryRunDefault` | `boolean` | `true` | 部署操作默认 dry-run |
| `auditEnabled` | `boolean` | `true` | 是否记录审计 |
| `stagingRoot` | `String` | `plugins/staged` | staged 插件包根目录 |
| `rateLimit.enabled` | `boolean` | `true` | 是否启用限流 |
| `rateLimit.writesPerMinute` | `int` | `30` | 每分钟写操作上限 |
| `csrf.enabled` | `String` | `auto` | `auto`、`true`、`false` |

## API 类最小结构

### `PluginAdminResponse<T>`

字段：

```java
private boolean success;
private String requestId;
private String operationId;
private String code;
private String message;
private T data;
private List<String> warnings;
```

要求：

- 提供静态工厂方法：`ok(...)`、`failed(...)`。
- `warnings` 默认空列表，不能为 `null`。
- 不直接持有异常对象。

### `PluginManagementOperation`

枚举值：

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

枚举值和 HTTP 状态映射：

| 枚举 | code | HTTP |
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

## Controller 方法草案

`PluginManagementController` 基础路径来自配置。若 Spring 注解无法直接引用配置字段，使用：

```java
@RequestMapping("${spring.pf4boot.management.http.base-path:/pf4boot/admin}")
```

方法草案：

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
```

如果当前 `PluginDeploymentRecorder` 还不支持按 deployment id 查询，先让查询和 rollback 返回 `PFM-007` 或 `PFM-009`，并在验收清单中标为未完成，不要伪造记录。

## 请求 DTO

目录：`pf4boot-api/src/main/java/net/xdob/pf4boot/management`

```java
public class PluginDeploymentRequest {
  private String pluginId;
  private String stagedPluginPath;
  private Boolean dryRun;
}
```

约束：

- `pluginId` 必填。
- `stagedPluginPath` 必填，且只能是 staging root 下的相对路径或规范化后仍在 staging root 内的路径。
- `dryRun` 为 `null` 时使用 `Pf4bootManagementProperties.dryRunDefault`。

## 关键实现规则

### 请求处理顺序

所有写操作必须按以下顺序处理：

1. 创建 `requestId`。
2. 构造 `PluginManagementRequest`。
3. 执行来源校验。
4. 执行认证。
5. 执行授权。
6. 执行限流。
7. 执行幂等 key 校验。
8. 校验参数和路径。
9. 调用 manager 或 deployment service。
10. 记录幂等结果。
11. 记录审计。
12. 返回统一响应。

不要在认证授权前访问 staged 包内容。

### 路径校验

`PluginManagementPathValidator` 必须使用 `Path.normalize()` 和 `toAbsolutePath()`，校验规范化后的目标路径仍以规范化后的 staging root 开头。

禁止：

- 接受任意绝对路径；
- 接受 `..` 逃逸；
- 根据用户输入拼接 shell 命令；
- 在 Controller 中直接执行文件移动或删除。

### token 比较

本地 token 比较使用常量时间比较：

```java
MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))
```

注意空值先转换为空字符串或提前拒绝，避免 NPE。

### loopback 判断

本地模式从 `HttpServletRequest.getRemoteAddr()` 读取来源，使用 `InetAddress.getByName(remoteAddr).isLoopbackAddress()` 判断。测试覆盖：

- `127.0.0.1`
- `::1`
- `localhost` 如果容器返回主机名
- 非 loopback 地址

### 幂等记录

`InMemoryPluginOperationStore` 使用 `ConcurrentHashMap`。

key：

```text
principalId + ":" + operation.name() + ":" + pluginId + ":" + idempotencyKey
```

record 字段：

- `requestHash`
- `state`
- `responseCode`
- `responseBodySummary`
- `createdAt`
- `updatedAt`

第一阶段可以不做 TTL 清理，但必须在类注释中说明这是内存实现，生产持久化后续替换。

## JSON 示例

### 启动插件成功

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

### token 缺失

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

### 幂等冲突

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

### 预检失败

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

## 测试文件清单

### `pf4boot-api`

目录：`pf4boot-api/src/test/java/net/xdob/pf4boot/management`

| 测试类 | 用例 |
| --- | --- |
| `PluginAdminResponseTest` | `okResponseDefaultsWarningsToEmptyList`、`failedResponseDoesNotExposeThrowable` |
| `PluginManagementErrorCodeTest` | `mapsErrorCodeToHttpStatus` |
| `PluginManagementOperationTest` | `mapsOperationToPermission` |

### `pf4boot-management-starter`

目录：`pf4boot-management-starter/src/test/java/net/xdob/pf4boot/management/starter`

| 测试类 | 用例 |
| --- | --- |
| `Pf4bootManagementAutoConfigurationTest` | `disabledByDefaultDoesNotRegisterController`、`localTokenModeRequiresToken`、`remoteModeRequiresAuthorizer` |
| `LocalTokenPluginManagementAuthorizerTest` | `rejectsMissingToken`、`rejectsInvalidToken`、`acceptsValidLoopbackToken`、`rejectsNonLoopbackAddress` |
| `PluginManagementPathValidatorTest` | `acceptsPathUnderStagingRoot`、`rejectsPathTraversal`、`rejectsAbsolutePathOutsideStagingRoot` |
| `PluginManagementIdempotencyServiceTest` | `replaysSameRequest`、`rejectsSameKeyDifferentRequest`、`doesNotExecuteDuplicateOperation` |
| `PluginManagementControllerTest` | `listPluginsIsReadOnly`、`startPluginRequiresPost`、`stopPluginRequiresPost`、`planDoesNotMutateRuntime`、`replaceUsesDeploymentService` |
| `PluginManagementExceptionHandlerTest` | `doesNotExposeStackTrace`、`doesNotExposeToken` |

## 禁止事项

- 不要在 `pf4boot-actuator` 中新增 `@WriteOperation`、`@DeleteOperation` 或任何生命周期写入口。
- 不要使用 GET 修改插件状态。
- 不要在 Controller 中直接调用 `reloadPlugin` 作为热替换。
- 不要接受用户提交的任意绝对路径。
- 不要在 HTTP 层拼接 shell 命令。
- 不要把 token、完整异常堆栈、环境变量、系统绝对敏感路径写入响应。
- 不要引入 Spring Security 作为核心强依赖；如需 adapter，作为可选后续模块。
- 不要使用 Java 9+ API、record、var、Stream.toList。

## 最小实施步骤

1. 新增模块和 `spring.factories`，确认默认不装配。
2. 新增 `pf4boot-api` 管理模型和测试。
3. 新增配置类、启动校验和本地 token authorizer。
4. 新增 Controller 的只读接口。
5. 新增 start/stop/restart/enable/disable。
6. 新增部署 plan/replace。
7. 新增路径校验、幂等和审计。
8. 新增 sample 配置和 README。
9. 更新验收清单证据。

每一步完成后至少运行对应模块测试，不要等全部实现后才验证。
