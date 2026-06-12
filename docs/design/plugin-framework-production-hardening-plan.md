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
| P0 | 设计与追踪基线 | Done | 设计、规划、验收文档和索引 |
| P1 | 插件包签名与信任链 | Done | SPI、结果模型、WARN 模式、manifest 样例 |
| P2 | 操作/部署/审计持久化 | Done | recorder SPI、文件实现、恢复扫描 |
| P3 | 生命周期并发与资源泄漏验证 | Done | 生命周期锁测试、清理报告、失败注入 |
| P4 | 能力声明与兼容矩阵 | Done | capability manifest、预检、JPA 多数据源声明 |
| P5 | 管理 smoke 与观测闭环 | In Progress | 管理 smoke、Actuator 诊断、metrics |
| P6 | 后续决策专题 | Planned | JPA 运行时刷新和跨数据源事务决策文档 |

## 面向小模型的任务拆分规则

为了让较小模型可以稳定实施，本规划中的任务按“一个任务卡只改变一个清晰边界”执行。除非用户明确要求，否则不要跨阶段混合提交。

### 任务卡模板

每个实施任务开始前，先在回复或提交说明中明确以下信息：

| 字段 | 要求 |
| --- | --- |
| 任务 ID | 使用本规划中的阶段编号，例如 `P4-1a` |
| 输入文件 | 至少列出要先读取的设计、代码和测试文件 |
| 允许修改 | 明确允许修改的模块和包路径 |
| 禁止修改 | 明确禁止修改的模块、默认行为或安全边界 |
| 完成证据 | 需要通过的测试命令、文档检查或 smoke 证据 |
| 回滚口径 | 出错时哪些改动可以直接回退，哪些需要保留诊断记录 |

### 通用执行检查清单

- [ ] 已读取 `docs/constraints/README.md` 和本阶段设计章节。
- [ ] 已确认 `git status --short` 中的未提交改动是否属于当前任务。
- [ ] 已用 `rg` 找到已有接口、配置和测试，不重复造同语义类型。
- [ ] 已先写或更新单元测试，再接入运行时流程。
- [ ] 已保持默认行为兼容历史插件。
- [ ] 已更新中文文档和英文翻译；只有有证据的验收项才标 `Done`。
- [ ] 已运行最小 Gradle 验证；若失败，记录命令、错误摘要和下一步。

### 阶段依赖

| 当前任务 | 依赖 | 说明 |
| --- | --- | --- |
| P1 | P0 | 信任链模型和 manifest 格式必须先冻结 |
| P2 | P0 | 持久化 store 可并行于 P1，但管理接入时要复用 P1 的安全摘要约束 |
| P3 | P1、P2 可选 | 生命周期诊断可独立做，部署失败记录接入需要 P2 |
| P4 | P1 | 能力声明第一阶段复用 trust manifest；如果 P1 未完成，只能先做 API 和解析测试 |
| P5 | P1-P4 | smoke 需要信任、持久化、生命周期和能力预检至少有最小实现 |
| P6 | P0 | P6 是设计专题，不阻塞 P1-P5 编码 |

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

### 小任务卡

| ID | 输入文件 | 允许修改 | 关键步骤 | 完成证据 |
| --- | --- | --- | --- | --- |
| P1-1a | `Pf4bootProperties`、现有 `PluginPackageVerifier` | `pf4boot-api` | 定义 trust request/result/status/root provider/manifest/signature metadata；所有 public 类型补 JavaDoc | `.\gradlew.bat :pf4boot-api:compileJava` |
| P1-1b | `Pf4bootProperties` | `pf4boot-api` | 增加 trust mode、manifest extension、trust roots；null setter 回退默认值 | `.\gradlew.bat :pf4boot-api:compileJava` |
| P1-2a | `Pf4bootPluginManagerImpl`、相关生命周期测试 | `pf4boot-core` | 在 classloader 创建前执行 trust 校验；`ENFORCE` 失败不得创建 plugin classloader | `.\gradlew.bat :pf4boot-core:test --tests "*Pf4bootPluginManagerLifecycleTest*"` |
| P1-2b | `DefaultPluginDeploymentService` | `pf4boot-core` | 在部署预检中加入 trust check，并映射为 `DeploymentCheckResult` | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginDeploymentServiceTest*"` |
| P1-3a | trust manifest loader 测试 | `pf4boot-core` | 支持外置 sidecar manifest，解析失败给安全错误码 | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginTrustManifestLoaderTest*"` |
| P1-4a | management controller/service 测试 | `pf4boot-management-starter` | HTTP 错误只返回安全摘要，不泄露签名、token、堆栈 | `.\gradlew.bat :pf4boot-management-starter:test` |
| P1-5a | 开发指南中英文版 | `docs/design/plugin-developer-guide.md`、英文版 | 增加 manifest 示例、WARN 到 ENFORCE 切换步骤、排错表 | 文档 diff 和 U+FFFD 检查 |

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

### 小任务卡

| ID | 输入文件 | 允许修改 | 关键步骤 | 完成证据 |
| --- | --- | --- | --- | --- |
| P2-1a | 现有 `PluginOperationStore`、`PluginOperationRecord` | `pf4boot-api` 或现有管理 API 包 | 优先扩展现有 store；补 query、recoverable scan、幂等冲突字段 | `.\gradlew.bat :pf4boot-api:compileJava` 或对应模块 compile |
| P2-2a | `InMemoryPluginOperationStore` 测试 | `pf4boot-management-starter` 或现有 store 所在模块 | 保持内存实现兼容新增接口 | targeted test |
| P2-3a | 文件 store 设计章节 | store 所在模块 | JSON Lines append/read/latest/corrupted-line skip | `.\gradlew.bat :pf4boot-management-starter:test --tests "*FilePluginOperationStoreTest*"` |
| P2-3b | deployment record store | `pf4boot-core` 或 management store 包 | 部署记录跨重启读取，半写不当成功 | targeted test |
| P2-4a | management idempotency service | `pf4boot-management-starter` | 相同 key replay，不同 requestHash 返回 409；store 写失败 fail closed | `.\gradlew.bat :pf4boot-management-starter:test` |
| P2-5a | 本设计和开发指南 | `docs/design`、必要 sample 配置 | 写入恢复扫描、目录清理、故障排查步骤 | 文档检查 |

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

### 小任务卡

| ID | 输入文件 | 允许修改 | 关键步骤 | 完成证据 |
| --- | --- | --- | --- | --- |
| P3-1a | `Pf4bootPluginManagerImpl`、生命周期测试 | `pf4boot-core` | 确认现有锁；如不足，增加按 pluginId 的锁注册表 | `.\gradlew.bat :pf4boot-core:test --tests "*Lifecycle*"` |
| P3-2a | share bean/web/scheduler 管理器 | `pf4boot-api`、`pf4boot-core` | 定义 cleanup report 和 diagnostic，只读收集资源计数 | `.\gradlew.bat :pf4boot-core:test` |
| P3-3a | Web mapping/interceptor 测试 | `pf4boot-web-starter` | stop 后 mapping/interceptor 清理断言 | `.\gradlew.bat :pf4boot-web-starter:test` |
| P3-3b | scheduler/share bean 测试 | `pf4boot-core` | stop 后 scheduler/share bean 清理断言 | `.\gradlew.bat :pf4boot-core:test` |
| P3-4a | deployment service 测试 | `pf4boot-core` | health check fail、新插件启动 fail、rollback fail 状态覆盖 | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginDeploymentServiceTest*"` |
| P3-5a | `samples/cross-plugin-jpa` | sample 模块 | 增加失败演示插件或配置，不影响正常 smoke | sample smoke |

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

### 小任务卡

| ID | 输入文件 | 允许修改 | 关键步骤 | 完成证据 |
| --- | --- | --- | --- | --- |
| P4-1a | `PluginTrustManifest`、manifest loader 测试 | `pf4boot-api`、`pf4boot-core` | 新增 capability model；从 trust manifest 的 `capabilities` 解析 provides/requires | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginTrustManifestLoaderTest*"` |
| P4-1b | `Pf4bootProperties` | `pf4boot-api` | 增加 `pluginCapabilityPrecheckMode`，null 回退 `DISABLED` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P4-2a | capability resolver 测试 | `pf4boot-core` | 合并 host、已启动插件、待部署插件能力；缺 manifest 返回空 descriptor | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginCapabilityResolverTest*"` |
| P4-3a | `DefaultPluginDeploymentService` | `pf4boot-core` | plan/replace 预检加入能力缺失判断；`WARN` 生成 warning，`ENFORCE` 生成 error | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginDeploymentServiceTest*"` |
| P4-4a | `samples/cross-plugin-jpa`、JPA 设计 | sample 模块、必要 JPA 文档 | provider 声明 `jpa.datasource`，consumer 按 datasource 声明 entity/repository 包 | sample 打包或 smoke |
| P4-5a | 本设计、验收文档 | `docs/design` 和英文版 | 写明 Java/PF4Boot/Spring Boot/capability/plugin dependency 矩阵和错误码 | 文档检查 |

### 兼容矩阵第一阶段字段

| 字段 | 来源 | 第一阶段处理 | 严格模式处理 |
| --- | --- | --- | --- |
| `javaVersion` | manifest `compatibility.javaVersion` | 与当前 `java.specification.version` 做字符串或最小比较 | 不匹配返回 `PFC-003` 或 compatibility error |
| `pf4bootVersionRange` | manifest | 文档和 DTO 保留字段；可先只记录 warning | 后续接入版本范围 parser |
| `springBootVersionRange` | manifest | 文档和 DTO 保留字段；可先只记录 warning | 后续接入版本范围 parser |
| `capability.versionRange` | requirement | 先匹配 capability name 和 attributes，versionRange 只进入诊断 | 后续阻断版本不匹配 |
| PF4J plugin dependency | `PluginDescriptor` | 继续使用 PF4J 现有依赖解析 | 不由 capability 替代 |

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

### 小任务卡

| ID | 输入文件 | 允许修改 | 关键步骤 | 完成证据 |
| --- | --- | --- | --- | --- |
| P5-1a | sample host build scripts | `samples/cross-plugin-jpa` | 增加一条打包命令，输出待部署插件路径 | sample assemble 命令 |
| P5-1b | sample host 启动脚本/测试 | `samples/cross-plugin-jpa` | 启动 host、轮询 ready、失败时打印日志尾部 | smoke 命令 |
| P5-2a | actuator inspector/endpoint 测试 | `pf4boot-actuator` | 只读暴露 trust/capability/deployment/cleanup 摘要 | `.\gradlew.bat :pf4boot-actuator:test` |
| P5-3a | management metrics 测试 | `pf4boot-management-starter`、`pf4boot-actuator` | 请求数、拒绝数、幂等命中、耗时、回滚计数 | targeted test |
| P5-4a | sample workflows | `samples/cross-plugin-jpa` | 正常交易、事务回滚、缺 datasource、热替换失败回滚 | runtime smoke |
| P5-5a | 开发指南中英文版 | `docs/design/plugin-developer-guide.md`、英文版 | smoke 命令、token/idempotency header、清理和排障 | 文档检查 |

### P5 可直接实施规格

#### P5-2 Actuator 只读治理摘要

建议新增一个独立 endpoint，避免改变现有 `pf4bootplugins` 响应结构。

| 项 | 规格 |
| --- | --- |
| endpoint id | `pf4bootgovernance` |
| 类名 | `Pf4bootGovernanceEndpoint`、`Pf4bootGovernanceSnapshot` |
| 归属模块 | `pf4boot-actuator` |
| 自动配置 | `Pf4bootActuatorAutoConfiguration` 条件注册 |
| 依赖输入 | `PluginRuntimeInspector`、可选 `PluginDeploymentMetricsProvider`、可选 `PluginLifecycleDiagnostic`、可选 `Pf4bootProperties` |
| 只读约束 | 只能调用 snapshot/inspect 方法，不得调用 start/stop/reload/replace/delete |

`Pf4bootGovernanceSnapshot` 第一阶段字段：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `pluginCount` | `int` | 当前 runtime snapshot 数量 |
| `startedPluginCount` | `int` | 状态为 started/running 的插件数量，按现有 snapshot state 字符串判断 |
| `failedPluginCount` | `int` | 状态为 failed 或包含失败摘要的插件数量 |
| `trustMode` | `String` | `Pf4bootProperties.pluginPackageTrustMode`，缺 bean 时为 `UNKNOWN` |
| `capabilityPrecheckMode` | `String` | `Pf4bootProperties.pluginCapabilityPrecheckMode`，缺 bean 时为 `UNKNOWN` |
| `trustManifestExtension` | `String` | 当前 sidecar manifest 后缀，缺 bean 时为空 |
| `deploymentSummary` | `PluginDeploymentMetricsSnapshot` 或等价 DTO | 部署总数、失败数、回滚数、最近耗时 |
| `cleanupReports` | `List<PluginCleanupReport>` | 每个插件只读清理摘要；无法诊断时为空列表并带 warning |
| `warnings` | `List<String>` | 只放安全摘要，例如缺少 provider、诊断不可用 |

实现步骤：

1. 先读 `Pf4bootPluginsEndpoint`、`Pf4bootMetrics`、`Pf4bootActuatorAutoConfiguration` 和现有测试。
2. 新增 DTO 时使用普通 Java 8 POJO，不使用 Lombok。
3. endpoint 构造函数接收依赖，所有可选依赖允许为 null。
4. `@ReadOperation` 方法返回快照对象；出现单个插件诊断异常时记录 warning，不让整个 endpoint 失败。
5. 新增 `Pf4bootGovernanceEndpointTest` 覆盖：无可选 provider、带 deployment metrics、diagnostic 抛异常仍返回 warning。
6. 运行 `.\gradlew.bat :pf4boot-actuator:test`。

#### P5-3 管理 metrics

建议把写入点放在 management starter 内，把读取和 Micrometer 注册放在 actuator 内，避免 actuator 反向影响管理接口。

| 类型 | 位置 | 说明 |
| --- | --- | --- |
| `PluginManagementMetricsSnapshot` | `pf4boot-api` 的 `net.xdob.pf4boot.management` | 只读计数快照 |
| `PluginManagementMetricsProvider` | `pf4boot-api` | actuator 读取 SPI |
| `DefaultPluginManagementMetricsRecorder` | `pf4boot-management-starter` | 使用 `AtomicLong` 记录请求、拒绝、幂等命中 |
| `Pf4bootMetrics` 扩展 | `pf4boot-actuator` | 条件注册 management metrics gauge |

计数规则：

| 指标 | 名称 | 计数时机 |
| --- | --- | --- |
| 管理请求数 | `pf4boot.management.request.total` | Controller 每个 HTTP endpoint 入口进入后加 1，包括读接口 |
| 管理拒绝数 | `pf4boot.management.rejected.total` | 鉴权失败、CSRF/origin 失败、参数校验失败、预检拒绝、store fail-closed 时加 1 |
| 幂等命中数 | `pf4boot.management.idempotency.hit.total` | 相同 idempotency key 且 requestHash 相同时 replay 加 1 |
| 部署耗时 | 复用 `pf4boot.deployment.last.duration.millis` | 由 deployment metrics provider 提供 |
| 回滚次数 | 复用 `pf4boot.deployment.rollback.total` | 由 deployment metrics provider 提供 |

实现步骤：

1. 先读 `PluginManagementController`、`PluginManagementIdempotencyService`、`Pf4bootManagementAutoConfiguration` 和 `Pf4bootMetrics`。
2. 在 management starter 自动配置中注册 `DefaultPluginManagementMetricsRecorder`，同时以 `PluginManagementMetricsProvider` 暴露。
3. Controller 构造函数增加 recorder 参数；测试 helper 统一传入 recorder，不能用 null 绕过。
4. 所有 public endpoint 入口调用 `recordRequest()`；已有私有公共校验方法可集中记录，但不得漏掉读接口。
5. 鉴权/校验/预检失败路径调用 `recordRejected()`；如果失败被全局异常处理捕获，需要在抛出前记录。
6. 幂等 replay 路径调用 `recordIdempotencyHit()`，冲突不算 hit，但算 rejected。
7. `Pf4bootMetrics` 增加三项 management gauge，并保留旧构造函数兼容测试。
8. 新增/更新测试：recorder 计数单测、Controller 请求/拒绝/幂等命中测试、Actuator metrics 注册测试。

#### P5-4 runtime smoke 脚本

smoke 可以先用 PowerShell 脚本落地，后续再抽成 Gradle task。脚本放在 `samples/cross-plugin-jpa/demo-host/src/smoke/` 或 `samples/cross-plugin-jpa/scripts/`，不要放在仓库根目录。

脚本必须按以下顺序执行：

1. 调用 `:samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`。
2. 准备独立 work 目录，写入测试 token、端口、operation store 路径。
3. 启动 demo host，日志写入 `build/tmp/<smoke-name>/runtime.log`。
4. 轮询 readiness 或一个稳定业务 endpoint，最多等待 120 秒。
5. 调用正常 JPA workflow，断言 HTTP 200 和响应字段。
6. 调用失败 workflow，断言失败后 summary 数据未部分提交。
7. 调用管理 plan/replace/start/stop 中至少一个写接口，必须带 token 和 idempotency key。
8. 重复同一个幂等请求，断言 replay 或已有 operation id。
9. 调用 actuator governance endpoint 和 metrics endpoint，断言关键字段存在。
10. 触发一个失败部署或缺能力预检，断言返回错误码或 warning。
11. finally 中关闭进程、释放端口、清理临时文件；失败时打印日志尾部和 HTTP 响应。

脚本输出至少包含：

```text
SMOKE_HOST_READY port=...
SMOKE_PLUGIN_ZIPS count=...
SMOKE_WORKFLOW_OK status=200
SMOKE_WORKFLOW_ROLLBACK status=...
SMOKE_MANAGEMENT_OPERATION operationId=...
SMOKE_IDEMPOTENCY_REPLAY operationId=...
SMOKE_ACTUATOR_GOVERNANCE status=200
SMOKE_FAILURE_CASE code=...
SMOKE_CLEANUP_OK
```

#### P5 文档与验收更新要求

P5 任一子任务完成后都要同步：

- `plugin-framework-production-hardening-acceptance.md`：只更新已验证条目。
- `plugin-developer-guide.md` 和英文版：涉及用户命令、配置、HTTP header、排障时必须更新。
- sample README：涉及 sample 命令或目录结构时必须更新。
- 本规划：若实现中类名或 endpoint id 与本节不同，必须先更新规划说明差异。

### smoke 必须输出的证据

| 证据 | 最低要求 |
| --- | --- |
| 启动日志 | host ready 时间、端口、插件列表 |
| HTTP 响应 | 成功和失败场景的 status、error code、operation/deployment id |
| 持久化记录 | operation/deployment 最新记录摘要 |
| Actuator | 插件状态、trust/capability warning、cleanup summary |
| 清理结果 | 进程已退出、临时目录或测试数据库已删除/可复用 |

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

### 小任务卡

| ID | 输入文件 | 允许修改 | 关键步骤 | 完成证据 |
| --- | --- | --- | --- | --- |
| P6-1a | JPA 相关设计和实现 | `docs/design/*jpa*`、英文版 | 对比禁止刷新、重建 EMF、重启 domain plugin 三种路径，给推荐方案 | 独立设计文档 |
| P6-2a | 跨插件事务设计 | `docs/design/*transaction*`、英文版 | 对比禁止、Saga、Outbox、可选 XA；明确本阶段不支持跨数据源本地事务 | 独立设计文档 |
| P6-3a | trust/packaging/management 设计 | `docs/design`、英文版 | 插件仓库、签名发布、灰度分发、回滚治理边界 | 独立设计文档 |
| P6-4a | management API 设计 | `docs/design`、英文版 | 明确是否做 UI、UI 与 HTTP API/Actuator 的边界 | 独立设计文档 |

### P6 决策文档规格

P6 只做设计决策，不直接修改生产代码。每个专题必须新增一份中文设计和一份英文翻译，文件名建议如下：

| 专题 | 中文文件 | 英文文件 |
| --- | --- | --- |
| JPA 运行时刷新 | `docs/design/jpa-runtime-refresh-decision.md` | `docs/design/en/jpa-runtime-refresh-decision.md` |
| 跨数据源事务 | `docs/design/cross-datasource-transaction-decision.md` | `docs/design/en/cross-datasource-transaction-decision.md` |
| 插件市场/仓库治理 | `docs/design/plugin-repository-governance-decision.md` | `docs/design/en/plugin-repository-governance-decision.md` |
| 控制台 UI 边界 | `docs/design/plugin-management-console-boundary.md` | `docs/design/en/plugin-management-console-boundary.md` |

每份决策文档必须包含：

| 章节 | 必须回答的问题 |
| --- | --- |
| 背景 | 当前代码和现有文档已经支持什么，缺口是什么 |
| 备选方案 | 至少列 3 个方案；对不可行方案也要写清不可行原因 |
| 推荐结论 | 明确推荐、暂缓或拒绝；不能只写“后续考虑” |
| 模块影响 | 哪些模块会新增 API、runtime、starter、sample 或文档 |
| 兼容影响 | 默认行为是否变化，历史插件是否受影响 |
| 验证方式 | 若未来实施，需要哪些单元测试、集成测试和 smoke |
| 进入规划条件 | 满足什么条件才把专题转成实施任务 |
| 非目标 | 明确哪些看似相关但本专题不做 |

P6 默认推荐方向：

- JPA 运行时刷新：优先评估“重启 datasource domain plugin 并重建局部事务环境”，暂不承诺在线刷新 Hibernate metamodel。
- 跨数据源事务：继续禁止本地跨数据源事务；优先把 Saga/Outbox 作为业务层模式，XA 仅作为未来可选模块评估。
- 插件市场/仓库治理：先做离线包仓库、签名发布和灰度策略设计，不引入远程中心服务强依赖。
- 控制台 UI：保持后端 API 与 actuator 边界稳定后再评估 UI；UI 不应成为 core/starter 依赖。

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
