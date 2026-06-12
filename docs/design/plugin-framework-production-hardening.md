# 插件框架生产级完善设计

## 背景

`pf4boot` 已经具备 PF4J 插件加载、Spring Boot 插件上下文、Web/JPA 集成、跨插件 JPA 事务能力、热替换部署编排、只读观测和 HTTP 管理接口等核心能力。当前框架继续完善的重点不再是单点功能补齐，而是把插件框架从“能用”推进到“生产可治理、可审计、可恢复、可演进”。

本设计用于收束下一阶段仍然缺失的框架级能力，并为后续实施规划提供统一边界。它不会替代已有专题设计，而是把签名信任链、部署记录持久化、生命周期并发验证、能力声明、管理 smoke、可观测闭环和兼容矩阵纳入同一条生产化主线。

## 目标

- 插件包在加载、部署和热替换前具备可插拔的完整性、签名和兼容性校验链。
- 热替换部署、管理操作、审计事件和幂等记录具备可恢复的持久化接口。
- 插件生命周期并发、资源清理和热替换失败路径具备可重复验证的测试与 smoke 机制。
- 插件能够声明自身能力、依赖能力、框架兼容范围和运行约束，宿主可在启动和部署前预检。
- 管理接口、Actuator 只读观测、部署编排和样例工程形成可端到端验收的闭环。
- 保持 Java 8、Spring Boot 2.7.x、现有 PF4J 依赖和模块边界兼容。

## 非目标

- 不在本设计中支持跨数据源事务；跨数据源事务继续作为未来独立专题。
- 不把 Spring Security、Flyway、Liquibase、外部 KMS 或具体 CA 体系作为框架强依赖。
- 不承诺类级热替换；热替换单元仍然是插件包。
- 不把管理控制台 UI 纳入本阶段范围；本阶段只定义后端框架能力和示例 smoke。
- 不默认启用管理写接口、签名强校验或持久化存储，历史应用需要显式开启。

## 现状/已有流程

| 领域 | 已有基础 | 仍需完善 |
| --- | --- | --- |
| 插件包校验 | `PluginPackageVerifier`、checksum、系统版本兼容检查 | 签名格式、信任根、吊销、证书轮换、校验结果审计 |
| 热替换部署 | `PluginDeploymentService`、预检、回滚、HTTP 管理入口 | 部署记录持久化、崩溃恢复、跨重启幂等、操作审计持久化 |
| 生命周期 | start/stop/reload/restart/delete、依赖顺序、资源清理 | 并发互斥验收、资源泄漏断言、失败注入、运行时 smoke |
| 观测 | `pf4boot-actuator` 只读快照和基础 metrics | 部署/管理指标、清理泄漏指标、诊断错误码矩阵 |
| JPA | 可选 JPA starter、跨插件单数据源事务、复杂样例 | 多数据源能力声明、运行时刷新专题决策、跨数据源事务边界文档 |
| 管理接口 | `pf4boot-management-starter`、本地 token、远程授权 SPI、幂等 | 持久化 recorder、示例脚本、运行时 smoke 验收 |
| 文档 | 中文设计、英文翻译、开发指南、专题规划 | 统一兼容矩阵、已规划事项状态追踪、生产级验收证据 |

## 核心约束

- 继续保持 Java 8 源码兼容。
- 公共 SPI、DTO、注解和错误码放在 `pf4boot-api`。
- PF4J 运行时、生命周期锁、包校验调用点、部署编排和默认内存实现放在 `pf4boot-core`。
- Spring Boot 自动配置放在 starter 模块；管理 HTTP 能力继续归属 `pf4boot-management-starter`。
- 只读观测继续归属 `pf4boot-actuator`，不得承载启停、重载、部署等写操作。
- JPA 能力继续归属 `pf4boot-jpa*`，不得把 schema 迁移工具作为强依赖。
- 生产增强能力默认关闭或 no-op，宿主显式配置后才启用。
- 所有破坏性默认值调整必须先进入 WARN/兼容模式，再通过规划明确切换窗口。

## 影响模块

| 模块 | 职责 |
| --- | --- |
| `pf4boot-api` | 新增或稳定插件包签名校验、能力声明、部署记录、管理审计、持久化 recorder SPI 的公共类型 |
| `pf4boot-core` | 调用校验链、生命周期互斥、默认内存 recorder、部署恢复入口、资源泄漏诊断 |
| `pf4boot-starter` | 注册默认 no-op/内存实现，暴露生产化配置属性 |
| `pf4boot-actuator` | 暴露只读诊断、校验状态、部署摘要和资源计数指标 |
| `pf4boot-management-starter` | 接入持久化幂等、审计和部署记录；提供本地 smoke 入口 |
| `pf4boot-jpa*` | 声明 JPA 数据源能力、保持单数据源跨插件事务边界，补充多数据源约束 |
| `samples/cross-plugin-jpa` | 承载复杂 JPA、管理接口、热替换和 smoke 示例 |

## 接口设计

### 插件包信任链

```java
public interface PluginPackageTrustVerifier {
  PluginPackageTrustResult verify(PluginPackageTrustRequest request);
}
```

| 类型 | 归属 | 说明 |
| --- | --- | --- |
| `PluginPackageTrustRequest` | `pf4boot-api` | 插件 ID、版本、包路径、descriptor、checksum、签名元数据、宿主信任配置 |
| `PluginPackageTrustResult` | `pf4boot-api` | `PASS`、`WARN`、`FAIL`、错误码、可审计消息 |
| `PluginTrustRootProvider` | `pf4boot-api` | 提供信任根、证书链、吊销清单或离线信任材料 |

第一阶段不强制选择 JAR 原生签名或外置 manifest。实现时先定义 SPI 和结果模型，再提供可选的外置 manifest 方案作为默认样例。

### 能力声明

```java
public interface PluginCapabilityDescriptor {
  String getPluginId();

  List<PluginCapability> provides();

  List<PluginCapabilityRequirement> requires();
}
```

能力声明用于预检，而不是替代 PF4J 插件依赖解析。典型能力包括：

| 能力 | 示例 |
| --- | --- |
| `web.mvc` | 插件提供动态 MVC endpoint |
| `jpa.datasource` | 插件提供命名 JPA 数据源和事务环境 |
| `jpa.consumer` | 插件消费某个命名数据源 |
| `management.local` | 插件或宿主启用本地管理入口 |
| `scheduler` | 插件注册定时任务 |

### 持久化记录

```java
public interface PluginOperationRecorder {
  void saveOperation(PluginOperationRecord record);

  PluginOperationRecord findOperation(String operationId);

  List<PluginOperationRecord> findRecentOperations(PluginOperationQuery query);
}
```

```java
public interface PluginIdempotencyStore {
  PluginIdempotencyRecord get(String key);

  void putIfAbsent(PluginIdempotencyRecord record);

  void complete(String key, PluginAdminResponse<?> response);
}
```

默认实现使用内存，文件实现作为第一阶段生产化选项，数据库实现只定义 SPI，不绑定具体 ORM 或 migration 工具。

### 生命周期诊断

```java
public interface PluginLifecycleDiagnostic {
  PluginCleanupReport inspectAfterStop(String pluginId);

  PluginConcurrencyReport inspectLifecycleLocks();
}
```

诊断接口只读，用于测试、Actuator 和管理 smoke，不负责直接修复运行时状态。

## 数据结构

### `PluginOperationRecord`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `operationId` | `String` | 管理操作或部署操作 ID |
| `operationType` | `String` | `START`、`STOP`、`RELOAD`、`DEPLOY_REPLACE`、`ROLLBACK` |
| `pluginId` | `String` | 目标插件 |
| `principal` | `String` | 操作主体摘要，不保存敏感凭证 |
| `requestHash` | `String` | 请求摘要，用于幂等冲突判断 |
| `state` | `String` | 操作状态 |
| `resultCode` | `String` | 统一错误码或 `OK` |
| `message` | `String` | 安全摘要 |
| `createdAt` | `long` | 创建时间 |
| `updatedAt` | `long` | 更新时间 |

### `PluginCapability`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `name` | `String` | 能力名 |
| `version` | `String` | 能力版本 |
| `scope` | `String` | `HOST`、`PLUGIN`、`DATASOURCE` |
| `attributes` | `Map<String, String>` | 数据源名、包扫描路径、事务管理器名等扩展属性 |

### `PluginCleanupReport`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pluginId` | `String` | 插件 ID |
| `passed` | `boolean` | 是否无残留 |
| `remainingBeans` | `int` | 未清理 bean 数 |
| `remainingMappings` | `int` | 未清理 MVC mapping 数 |
| `remainingSchedulers` | `int` | 未清理定时任务数 |
| `classLoaderReachable` | `boolean` | classloader 是否仍可被强引用到 |
| `messages` | `List<String>` | 诊断摘要 |

## 状态机

### 操作记录状态

| 状态 | 说明 | 可迁移到 |
| --- | --- | --- |
| `RECEIVED` | 请求已接收 | `VALIDATING`、`REJECTED` |
| `VALIDATING` | 权限、幂等、参数和预检中 | `EXECUTING`、`REJECTED` |
| `EXECUTING` | 正在执行生命周期或部署操作 | `SUCCEEDED`、`FAILED`、`ROLLING_BACK` |
| `ROLLING_BACK` | 正在回滚 | `ROLLED_BACK`、`MANUAL_INTERVENTION` |
| `SUCCEEDED` | 操作成功 | 终态 |
| `FAILED` | 操作失败且无自动回滚 | 终态 |
| `ROLLED_BACK` | 操作失败但已恢复旧状态 | 终态 |
| `MANUAL_INTERVENTION` | 自动恢复失败，需要人工介入 | 终态 |
| `REJECTED` | 校验失败，未改变运行时 | 终态 |

### 信任校验状态

| 状态 | 说明 |
| --- | --- |
| `NOT_CONFIGURED` | 未配置签名或信任链，按模式决定 WARN 或 PASS |
| `CHECKSUM_PASS` | 摘要校验通过 |
| `SIGNATURE_PASS` | 签名校验通过 |
| `TRUST_PASS` | 信任根、吊销和有效期校验通过 |
| `WARN` | 非阻断问题 |
| `FAIL` | 阻断加载或部署 |

## 时序流程

### 插件部署前预检

1. 管理入口接收部署计划请求。
2. 执行认证、授权、幂等和 staging 路径校验。
3. 解析插件 descriptor 和能力声明。
4. 执行 checksum、签名、信任根和兼容矩阵校验。
5. 计算依赖影响范围和数据源能力需求。
6. 生成 `DeploymentPlan` 与 `PluginOperationRecord`。
7. 返回 dry-run 结果；默认不改变运行时状态。

### 插件停止后诊断

1. 生命周期服务完成 `stopPlugin`。
2. 框架触发 `PluginLifecycleDiagnostic.inspectAfterStop(pluginId)`。
3. 检查动态 bean、Web mapping、interceptor、定时任务、JPA 资源和 classloader 引用。
4. 将报告写入 operation/deployment record，并通过 Actuator 暴露摘要。
5. 若处于部署流程且诊断失败，进入回滚或人工介入状态。

## 异常处理

| 异常 | 对外行为 | 记录要求 |
| --- | --- | --- |
| 签名缺失 | `WARN` 或 `FAIL`，取决于配置模式 | 记录插件、版本、校验模式和缺失项 |
| 信任根无效 | 阻断部署 | 不输出证书私钥、token、完整系统路径 |
| 幂等冲突 | 返回 `409` | 记录 key、principal 摘要和 request hash |
| 持久化不可用 | 默认阻断写操作，可配置降级到内存但必须告警 | 记录 recorder 类型和失败原因 |
| 资源清理失败 | 部署流程进入回滚或人工介入 | 记录残留资源计数 |
| 能力缺失 | 预检失败，不改变运行时 | 记录缺失能力和要求来源 |

## 幂等性

- 管理写操作使用 `principal + operation + pluginId + requestHash` 作为幂等作用域。
- 部署操作额外绑定 `deploymentId` 和目标包 checksum。
- 相同 key 与相同请求返回已有结果。
- 相同 key 但请求摘要不同返回冲突。
- 持久化 store 启用后，宿主重启后仍应能识别已完成、执行中和需要恢复的操作。

## 回滚策略

- 信任链、能力声明和兼容矩阵校验失败时，不改变运行时，无需回滚。
- 部署执行前必须保存旧包路径、旧 descriptor 摘要、旧启动状态和受影响插件列表。
- 新版本启动或 health check 失败时，优先恢复旧包和旧启动状态。
- 若持久化 recorder 记录 `EXECUTING` 且宿主异常退出，重启后进入恢复扫描：能确认未激活新包则标记失败；已激活新包但未完成 health check 则进入人工介入或按配置回滚。
- 任何回滚失败不得吞掉现场信息，必须保留失败记录和诊断摘要。

## 兼容性

- 新 SPI 默认 no-op 或内存实现，不影响历史宿主启动。
- 签名校验默认不强制；生产环境通过配置从 `DISABLED` 迁移到 `WARN`，再迁移到 `ENFORCE`。
- 能力声明缺失时，历史插件按未知能力处理；只有配置启用严格预检后才阻断。
- `pf4boot-actuator` 保持只读，不改变已有管理接口安全边界。
- JPA 单数据源跨插件事务能力继续保持；跨数据源事务明确不支持。

## 灰度/迁移

| 阶段 | 默认行为 | 迁移动作 |
| --- | --- | --- |
| P0 文档冻结 | 不改变运行时 | 固化设计、规划和验收清单 |
| P1 信任链 WARN | 不阻断历史包 | 插件包补 checksum/signature manifest |
| P2 持久化 recorder | 默认内存 | 宿主可显式开启文件 recorder |
| P3 严格预检 | 默认 WARN | 生产环境逐步启用能力声明和兼容矩阵 |
| P4 ENFORCE | 仅新应用建议启用 | 阻断未签名或能力缺失插件 |

## 测试方案

- API 编译：`.\gradlew.bat :pf4boot-api:compileJava`
- 生命周期并发与清理：`.\gradlew.bat :pf4boot-core:test`
- 管理接口幂等、审计、安全摘要：`.\gradlew.bat :pf4boot-management-starter:test`
- 只读观测：`.\gradlew.bat :pf4boot-actuator:test`
- 复杂样例打包：`.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`
- 运行时 smoke：启动 sample host 后调用管理接口、Actuator endpoint 和跨插件 JPA 示例接口，验证部署记录、审计记录和资源清理报告。

## 风险点

| 风险 | 严重度 | 缓解 |
| --- | --- | --- |
| 签名体系过早绑定具体实现 | 高 | 先定义 SPI 与外置 manifest 样例，不强依赖 CA/KMS |
| 持久化 store 失败导致管理操作不可预测 | 高 | 写操作默认 fail closed，允许显式配置降级 |
| 能力声明与实际运行时不一致 | 中 | 增加启动后诊断和 smoke 验证 |
| 资源泄漏诊断误报 | 中 | 初期只作为 WARN 和测试断言，逐步收紧 |
| 文档规划多但状态不可追踪 | 中 | 独立维护规划和验收文档，每项完成后补证据 |

## 分阶段实施计划

具体追踪以 [plugin-framework-production-hardening-plan.md](plugin-framework-production-hardening-plan.md) 和 [plugin-framework-production-hardening-acceptance.md](plugin-framework-production-hardening-acceptance.md) 为准。

1. P0：冻结设计、同步中英文文档和索引。
2. P1：落地签名/信任链 SPI 与 WARN 模式样例。
3. P2：落地持久化 operation/deployment/idempotency recorder。
4. P3：补生命周期并发、资源泄漏和失败注入验证。
5. P4：落地能力声明与兼容矩阵预检。
6. P5：补管理 smoke、Actuator 诊断和样例闭环。
7. P6：补 JPA 运行时刷新与跨数据源事务的后续决策文档。

## 未决问题

- 默认签名 manifest 采用插件 zip 外置文件还是 zip 内 `META-INF` 文件，需要在 P1 实施前做小设计确认。
- 文件 recorder 的落盘格式采用 JSON Lines 还是单记录 JSON 文件，需要结合恢复扫描复杂度确认。
- classloader 泄漏检测在 Java 8 下只能做弱引用和 GC 辅助断言，是否进入默认运行时诊断需要先在测试中验证稳定性。
