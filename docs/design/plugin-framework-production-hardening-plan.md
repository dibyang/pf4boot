# 插件框架生产级完善实施规划

## 范围

本规划对应 [plugin-framework-production-hardening.md](plugin-framework-production-hardening.md)，用于追踪插件框架下一阶段生产级完善任务。规划只覆盖框架治理能力，不包含管理控制台 UI、跨数据源事务实现和运行时 JPA metamodel 热刷新实现。

## 实施原则

- 先补公共模型和可观测证据，再收紧默认策略。
- 所有新能力默认 no-op、内存实现或 WARN 模式，避免破坏历史应用。
- 每个阶段完成后必须同步更新中文设计、英文翻译、验收记录和必要的开发指南。
- 每个阶段都要有最小可运行 Gradle 验证；涉及运行时行为时补 sample smoke。
- 不引入强绑定的安全、签名、数据库迁移或持久化框架依赖。

## 阶段总览

| 阶段 | 主题 | 状态 | 主要产物 |
| --- | --- | --- | --- |
| P0 | 设计与追踪基线 | Planned | 设计、规划、验收文档和索引 |
| P1 | 插件包签名与信任链 | Planned | SPI、结果模型、WARN 模式、manifest 样例 |
| P2 | 操作/部署/审计持久化 | Planned | recorder SPI、文件实现、恢复扫描 |
| P3 | 生命周期并发与资源泄漏验证 | Planned | 生命周期锁测试、清理报告、失败注入 |
| P4 | 能力声明与兼容矩阵 | Planned | capability manifest、预检、JPA 多数据源声明 |
| P5 | 管理 smoke 与观测闭环 | Planned | 管理 smoke、Actuator 诊断、metrics |
| P6 | 后续决策专题 | Planned | JPA 运行时刷新和跨数据源事务决策文档 |

## P0 设计与追踪基线

### 目标

冻结生产级完善的设计边界，并建立后续实施的规划与验收追踪文档。

### 任务

| ID | 任务 | 影响文件/模块 | 验证 |
| --- | --- | --- | --- |
| P0-1 | 新增生产级完善设计文档 | `docs/design/plugin-framework-production-hardening.md` | 文档自检 |
| P0-2 | 新增英文翻译 | `docs/design/en/plugin-framework-production-hardening.md` | 文档自检 |
| P0-3 | 新增实施规划和验收文档 | `docs/design/*production-hardening-plan.md`、`*acceptance.md` | 文档自检 |
| P0-4 | 更新设计索引 | `docs/design/README.md`、`docs/design/en/README.md` | 链接检查 |

### 退出条件

- 中英文文档均存在。
- 设计索引可找到新文档。
- 验收文档中 P0 条目可填写证据。

## P1 插件包签名与信任链

### 目标

在现有 checksum/verifier 基础上扩展签名与信任链校验，先以 WARN 模式落地，不阻断历史插件。

### 实施步骤

1. 在 `pf4boot-api/src/main/java/net/xdob/pf4boot/trust/` 新增公共模型：
   - `PluginPackageTrustVerifier`
   - `PluginPackageTrustRequest`
   - `PluginPackageTrustResult`
   - `PluginPackageTrustStatus`
   - `PluginTrustRootProvider`
   - `PluginTrustManifest`
   - `PluginSignatureMetadata`
2. 在 `Pf4bootProperties` 增加配置字段：
   - `pluginPackageTrustMode`
   - `pluginPackageTrustManifestExtension`
   - `pluginPackageTrustRoots`
3. 在 `pf4boot-core` 新增默认实现：
   - `DefaultPluginTrustManifestLoader`
   - `DefaultPluginPackageTrustVerifier`
   - `NoopPluginTrustRootProvider`
4. 在 `Pf4bootPluginManagerImpl` 和 `DefaultPluginDeploymentService` 的现有 `PluginPackageVerifier` 调用前后串联 trust verifier。若现有校验链更适合统一处理，可先把 trust verifier 适配成 `PluginPackageVerifier`，但必须保持错误码和结果模型可查询。
5. 在 `pf4boot-starter` 自动装配默认 bean，允许宿主通过 Spring Bean 注入自定义 verifier/root provider。
6. 更新 sample 插件打包输出，至少给一个插件生成 `.pf4boot-trust.json` 示例。
7. 补文档和测试。

### 必测用例

| 测试类 | 用例 |
| --- | --- |
| `DefaultPluginPackageTrustVerifierTest` | `disabledModeIgnoresMissingManifest`、`warnModeRecordsMissingManifest`、`enforceModeRejectsMissingManifest` |
| `DefaultPluginTrustManifestLoaderTest` | `loadsSidecarManifest`、`rejectsInvalidJson`、`rejectsPluginIdMismatch` |
| `DefaultPluginDeploymentServiceTest` | `planReplacementIncludesTrustWarnings`、`replaceRejectsUntrustedPackageInEnforceMode` |
| `Pf4bootPluginManagerLifecycleTest` | `loadPluginRejectsUntrustedPackageBeforeClassLoaderCreation` |

### 禁止事项

- 不要在 P1 引入 BouncyCastle、KMS SDK、Spring Security 或外部 CA 客户端。
- 不要把签名强校验设为默认。
- 不要把完整签名值、证书内容、私钥路径或异常堆栈写入 HTTP 响应。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P1-1 | 定义 `PluginPackageTrustVerifier`、request/result、trust root SPI | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P1-2 | 在插件加载/部署预检前串联 trust verifier | `pf4boot-core`、`pf4boot-management-starter` | `.\gradlew.bat :pf4boot-core:test` |
| P1-3 | 增加外置 manifest 样例格式，支持 checksum + signature metadata | `pf4boot-core`、`samples/*` | sample 打包检查 |
| P1-4 | 增加 `DISABLED/WARN/ENFORCE` 配置和安全错误摘要 | `pf4boot-starter`、`pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P1-5 | 补开发指南：如何给插件包补 manifest、如何从 WARN 切到 ENFORCE | `docs/design/plugin-developer-guide.md` 和英文版 | 文档自检 |

### 设计约束

- 不把 KMS、CA、JAR signing 工具作为强依赖。
- 默认配置必须兼容无签名历史插件。
- `ENFORCE` 模式下失败信息不得泄露私钥路径、token、完整堆栈和敏感环境变量。

### 退出条件

- 历史插件在默认配置下仍可加载。
- WARN 模式能记录缺签名、签名无效和信任根缺失。
- ENFORCE 模式能阻断不可信插件包。

## P2 操作/部署/审计持久化

### 目标

让管理操作、部署记录、审计事件和幂等记录可跨宿主重启恢复。

### 实施步骤

1. 先复核并复用已有 `net.xdob.pf4boot.management.PluginOperationStore`、`PluginOperationRecord`、`InMemoryPluginOperationStore`。
2. 若现有 store 缺少查询或完成态记录字段，优先扩展现有接口；只有语义明显不匹配时才新增 `PluginOperationRecorder`。
3. 在 `pf4boot-api` 或 `pf4boot-core` 增加文件 store 配置模型，实际配置字段放入 `Pf4bootProperties`。
4. 在 `pf4boot-management-starter` 自动装配 store：
   - `type=memory` 时继续使用 `InMemoryPluginOperationStore`。
   - `type=file` 时使用文件 store。
   - 文件 store 初始化失败且 `fail-closed=true` 时，管理写接口启动失败或写操作拒绝。
5. 部署记录 store 优先复用 `PluginDeploymentRecordStore`；如果该接口目前只在 starter 内部，应评估是否提升到 `pf4boot-api` 或 `pf4boot-core` 可复用包。
6. 增加恢复扫描入口，至少提供显式方法 `scanRecoverableOperations()`，不要在构造函数里执行复杂恢复逻辑。
7. 更新管理接口 idempotency 逻辑，使已完成记录可以跨重启 replay。

### 必测用例

| 测试类 | 用例 |
| --- | --- |
| `FilePluginOperationStoreTest` | `appendAndReadLatestRecord`、`skipCorruptedLine`、`detectIdempotencyConflict`、`recoverExecutingRecord` |
| `PluginManagementIdempotencyServiceTest` | `replaysPersistedResult`、`rejectsSameKeyDifferentRequestAfterRestart` |
| `PluginManagementControllerTest` | `writeFailsClosedWhenStoreUnavailable`、`auditRecordDoesNotContainToken` |
| `DefaultPluginDeploymentServiceTest` | `recoverReplacementRecordAfterRestart` |

### 禁止事项

- 不要把文件持久化默认开启。
- 不要在 JSON Lines 中保存 token、完整绝对敏感路径、完整堆栈或请求原文。
- 不要在 store 写失败后继续执行部署替换。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P2-1 | 定义 `PluginOperationRecorder`、`PluginIdempotencyStore` 和查询模型 | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P2-2 | 提供默认内存实现并保持当前行为 | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test` |
| P2-3 | 提供文件 recorder，实现原子写入和恢复扫描 | `pf4boot-core` 或独立 support 包 | targeted test |
| P2-4 | 管理接口接入持久化幂等、审计和部署记录 | `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P2-5 | 增加崩溃恢复文档和样例配置 | `docs/design`、`samples/*` | 文档和 sample 检查 |

### 设计约束

- 写操作默认 fail closed；recorder 不可用时不能悄悄执行危险操作。
- 文件 recorder 必须避免半写记录被当成成功结果。
- 数据库 recorder 只定义 SPI，不在本阶段引入具体实现。

### 退出条件

- 相同幂等 key 在重启后仍能返回已有结果或冲突。
- `EXECUTING` 记录在重启后能被恢复扫描识别。
- 管理接口审计记录不包含 token 和敏感路径。

## P3 生命周期并发与资源泄漏验证

### 目标

补齐生命周期互斥、依赖链热替换、stop 后资源清理和失败注入的测试闭环。

### 实施步骤

1. 先梳理 `Pf4bootPluginManagerImpl` 中 start/stop/reload/delete/upgrade 的入口，确认当前是否已有锁。
2. 若无统一互斥，新增按 `pluginId` 维度的生命周期锁组件，例如 `PluginLifecycleLockRegistry`，放在 `pf4boot-core`。
3. 锁策略：
   - 同一插件的 mutating 操作串行。
   - 不同插件默认可并行。
   - 涉及依赖链部署时，对影响范围排序后获取锁，避免死锁。
4. 新增 `PluginLifecycleDiagnostic` 和 `PluginCleanupReport`，先只读收集已有管理器能拿到的资源计数。
5. Web/JPA/scheduler 的深度清理检查不要一次做满；每加一种资源计数都要有对应 stop 后测试。
6. 失败注入优先通过 fake plugin、fake verifier、fake health probe 实现，不要在生产代码里加入测试开关。
7. 对 classloader 泄漏检查使用弱引用和有限 GC 尝试，测试只做 best-effort，不作为不稳定的硬断言，除非已经证明稳定。

### 必测用例

| 测试类 | 用例 |
| --- | --- |
| `PluginLifecycleLockRegistryTest` | `samePluginOperationsAreSerialized`、`differentPluginsCanProceed`、`dependencyScopeLocksInStableOrder` |
| `Pf4bootPluginManagerLifecycleTest` | `concurrentStartDoesNotDuplicateResources`、`stopAfterFailedStartCleansPartialResources` |
| `DefaultShareBeanMgrTest` | `stopRemovesSharedBeansForPlugin` |
| `DefaultScheduledMgrTest` | `stopCancelsPluginScheduledTasks` |
| `DefaultPluginDeploymentServiceTest` | `rollbackWhenHealthCheckFails`、`manualInterventionWhenRollbackFails` |

### 禁止事项

- 不要用全局大锁覆盖所有插件操作，除非测试证明依赖链锁无法正确实现。
- 不要使用 `Thread.stop`、强制关闭正在执行的 JDBC 事务或吞掉 stop 失败。
- 不要让 Actuator 调用任何会改变生命周期状态的方法。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P3-1 | 梳理并锁定 start/stop/reload/delete 的互斥策略 | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test` |
| P3-2 | 增加资源清理诊断模型和测试 helper | `pf4boot-api`、`pf4boot-core` | targeted test |
| P3-3 | 补 Web mapping、interceptor、scheduler、share bean 的 stop 后断言 | `pf4boot-web-starter`、`pf4boot-core` | targeted test |
| P3-4 | 增加热替换失败注入：加载失败、启动失败、health check 失败、回滚失败 | `pf4boot-core`、`pf4boot-management-starter` | targeted test |
| P3-5 | 在复杂样例中增加可触发失败的演示插件 | `samples/cross-plugin-jpa` | sample smoke |

### 设计约束

- 不通过 `Thread.stop` 或强杀 JDBC 事务来实现排空。
- Java 8 下 classloader 泄漏检测以测试辅助为主，运行时只暴露弱诊断。
- 生命周期锁不能破坏现有 PF4J 依赖顺序语义。

### 退出条件

- 同一插件并发 start/stop/reload 不会重复注册资源。
- stop 后动态资源计数归零或输出明确残留报告。
- 热替换失败能进入已知失败/回滚状态。

## P4 能力声明与兼容矩阵

### 目标

让插件在部署前声明能力和依赖能力，宿主能提前判断是否满足运行条件。

### 实施步骤

1. 在 `pf4boot-api/src/main/java/net/xdob/pf4boot/capability/` 新增：
   - `PluginCapability`
   - `PluginCapabilityRequirement`
   - `PluginCapabilityDescriptor`
   - `PluginCapabilityResolver`
   - `PluginCapabilityPrecheckResult`
2. 能力 manifest 第一阶段从 `.pf4boot-trust.json` 的 `capabilities` 字段读取，避免新增第二个旁路文件。
3. `DefaultPluginCapabilityResolver` 负责合并：
   - 当前宿主内置能力。
   - 已启动插件提供的能力。
   - 待部署插件声明的能力。
4. `PluginCapabilityPrecheck` 负责判断 required capability 是否满足，支持 `DISABLED/WARN/ENFORCE`。
5. JPA 数据源插件能力约定：
   - provider 声明 `jpa.datasource`，attributes 至少包含 `datasource`、`transactionManager`。
   - consumer 声明 `jpa.consumer`，attributes 至少包含 `datasource`、`entityPackages`、`repositoryPackages`。
6. 多数据源插件必须按 datasource 分组声明包扫描路径；禁止把多个数据源的 entity/repository 混在同一个包声明里。
7. 兼容矩阵先写文档和预检模型，不要求第一阶段实现复杂 version range parser；可以复用 PF4J 已有版本匹配能力或做最小比较。

### 多数据源能力声明示例

```json
{
  "capabilities": {
    "requires": [
      {
        "name": "jpa.datasource",
        "versionRange": "[1,2)",
        "required": true,
        "attributes": {
          "datasource": "orderDs",
          "entityPackages": "com.example.order.domain",
          "repositoryPackages": "com.example.order.repository"
        }
      },
      {
        "name": "jpa.datasource",
        "versionRange": "[1,2)",
        "required": true,
        "attributes": {
          "datasource": "billingDs",
          "entityPackages": "com.example.billing.domain",
          "repositoryPackages": "com.example.billing.repository"
        }
      }
    ]
  }
}
```

### 必测用例

| 测试类 | 用例 |
| --- | --- |
| `DefaultPluginCapabilityResolverTest` | `readsCapabilitiesFromTrustManifest`、`mergesHostAndStartedPluginCapabilities` |
| `PluginCapabilityPrecheckTest` | `warnsWhenRequiredCapabilityMissing`、`rejectsWhenRequiredCapabilityMissingInEnforceMode`、`matchesDatasourceByName` |
| `DefaultPluginDeploymentServiceTest` | `planReplacementReportsMissingDatasourceCapability` |
| sample smoke | 多数据源 consumer 缺少一个 datasource provider 时预检失败或 WARN |

### 禁止事项

- 不要把 capability 当成 PF4J dependency 的替代品。
- 不要默认阻断没有 capability manifest 的历史插件。
- 不要实现跨数据源事务。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P4-1 | 定义 capability manifest 模型和解析规则 | `pf4boot-api`、`pf4boot-core` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P4-2 | 支持插件 descriptor 或独立 manifest 声明能力 | Gradle plugin/packaging 相关模块 | sample 打包检查 |
| P4-3 | 管理部署预检接入能力缺失判断 | `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P4-4 | JPA 数据源插件声明 `jpa.datasource`，消费者声明 `jpa.consumer` | `pf4boot-jpa*`、`samples/cross-plugin-jpa` | JPA sample smoke |
| P4-5 | 建立框架版本、Java 版本、PF4Boot 能力版本的兼容矩阵 | `docs/design` | 文档自检 |

### 设计约束

- 能力声明不替代 PF4J 依赖关系，只作为预检和诊断补充。
- 历史插件没有 manifest 时默认 WARN，不直接阻断。
- 多数据源可声明多个命名数据源，但跨数据源事务继续不支持。

### 退出条件

- 缺少 JPA 数据源能力时，消费者插件部署预检失败或 WARN。
- 插件依赖多个数据源时，可以按包扫描路径声明实体和 Repository 归属。
- 兼容矩阵能解释为什么某个插件在当前宿主不可部署。

## P5 管理 smoke 与观测闭环

### 目标

用 sample host 验证管理接口、只读观测、部署记录、JPA 示例和热替换流程可以端到端工作。

### 实施步骤

1. 在 `samples/cross-plugin-jpa` 下新增 smoke 文档和脚本，脚本可以是 Gradle task、PowerShell 或 Java 测试，优先选择仓库已有风格。
2. smoke 必须固定端口或从启动日志读取端口，避免和开发者本机服务冲突时无提示失败。
3. 启动 host 后先轮询 readiness endpoint，再调用业务/JPA endpoint。
4. 管理接口调用必须带 `X-PF4Boot-Admin-Token` 和 `X-Idempotency-Key`。
5. Actuator 检查只读 endpoint，验证插件列表、部署摘要、trust/capability warning 和 cleanup report 摘要存在。
6. 失败路径至少覆盖一个：
   - 缺少 trust manifest。
   - 缺少 datasource provider。
   - health check 失败导致回滚。
7. smoke 结束必须清理进程、临时插件包、operation store 和测试数据库文件。

### 必测用例

| 场景 | 验收 |
| --- | --- |
| 正常启动 | host ready，业务 endpoint 返回 200 |
| 本地管理 start/stop | token 和 idempotency key 生效，重复请求 replay |
| JPA 事务回滚 | 业务失败后两个插件内数据一致回滚 |
| 缺少能力 | 部署预检返回 `PFC-002` 或 warning |
| 观测检查 | Actuator 只读响应包含插件状态、operation/deployment 摘要 |
| 失败清理 | smoke 退出后无残留进程和临时目录 |

### 禁止事项

- 不要为了 smoke 把管理 token 校验关掉。
- 不要依赖外部网络、私有 Maven 仓库或人工浏览器操作。
- 不要让 smoke 修改开发者全局环境变量或用户目录配置。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P5-1 | 增加 sample host 管理 smoke 脚本或 Gradle task | `samples/cross-plugin-jpa` | sample smoke |
| P5-2 | Actuator 暴露信任校验摘要、部署摘要和清理报告摘要 | `pf4boot-actuator` | `.\gradlew.bat :pf4boot-actuator:test` |
| P5-3 | 管理 metrics 覆盖请求数、拒绝数、幂等命中、部署耗时和回滚次数 | `pf4boot-management-starter`、`pf4boot-actuator` | targeted test |
| P5-4 | 复杂样例覆盖正常交易、事务回滚、依赖数据源缺失、热替换失败回滚 | `samples/cross-plugin-jpa` | runtime smoke |
| P5-5 | 文档补齐 smoke 启动、调用和排障步骤 | `docs/design/plugin-developer-guide.md` 和英文版 | 文档自检 |

### 设计约束

- smoke 脚本不得依赖外部商业服务或私有凭证。
- 本地管理接口仍需 token；不能为了 smoke 关闭安全保护。
- Actuator endpoint 继续只读。

### 退出条件

- 一个命令能打包复杂样例插件。
- 一个 smoke 流程能启动 host、调用管理接口、验证 JPA 示例、执行观测检查并关闭进程。
- 失败时能输出可定位的日志、HTTP 响应和部署/操作记录。

## P6 后续决策专题

### 目标

把暂不实现但会影响架构边界的问题形成决策文档，避免后续实现时重新拉扯范围。

### 任务

| ID | 任务 | 影响范围 | 产物 |
| --- | --- | --- | --- |
| P6-1 | JPA 运行时刷新/EntityManagerFactory 重建方案评估 | `pf4boot-jpa*` | 独立设计文档 |
| P6-2 | 跨数据源事务策略评估：禁止、Saga、Outbox、XA 可选模块 | JPA/事务能力 | 独立设计文档 |
| P6-3 | 插件市场/仓库治理评估：远程仓库、签名发布、灰度分发 | packaging/management | 独立设计文档 |
| P6-4 | 控制台 UI 边界评估 | management | 独立设计文档 |

### 退出条件

- 每个专题都有明确推荐方案、非目标、兼容影响和是否进入实现规划的结论。

## 推荐执行顺序

1. 完成 P0 并提交。
2. P1 和 P2 先做，因为它们是后续 ENFORCE、审计和恢复的基础。
3. P3 紧随其后，避免在没有测试闭环的情况下继续扩展热替换能力。
4. P4 和 P5 可以并行推进，但必须在 sample smoke 中汇合。
5. P6 只做设计决策，不阻塞 P1-P5 的工程落地。

## 每阶段完成定义

- 代码实现已完成并保持 Java 8 兼容。
- 中英文设计或指南同步更新。
- 验收文档中的对应条目从 `Planned` 更新为 `Done` 或 `Blocked`，并填写证据。
- 已运行规划中列出的最小验证命令；若无法运行，记录具体原因。
- 本地提交包含单一阶段的清晰提交信息。
