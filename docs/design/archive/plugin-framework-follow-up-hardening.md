# 插件框架后续增强设计

## 背景

P7-P9 已完成离线仓库 dry-run、版本范围预检和 Gradle runtime smoke。当前剩余工作不再是 P7-P9 的缺口，而是下一批可独立推进的增强项：

1. 仓库发布物到真实替换部署的完整执行链。
2. 跨平台 runtime smoke runner 与 JUnit XML 报告。
3. no-jpa/unrelated 示例插件，用于验证依赖插件失败不影响无关插件。

本设计把这些增强收敛为 P10，避免继续扩大已完成阶段的范围。

## 目标

- 让 `offline-index` 仓库 release 可以在通过 dry-run 后执行真实 replace，并复用现有鉴权、幂等、审计、信任校验、能力预检和回滚链路。
- 提供不依赖 PowerShell 的跨平台 smoke runner，保留现有 PowerShell 脚本作为 Windows 快速入口。
- 生成 CI 友好的 `result.json` 和 JUnit XML，失败时保留必要日志，成功时可按配置保留或清理工作目录。
- 在复杂 sample 中增加无 JPA 依赖的无关插件，端到端验证 datasource provider 或 JPA consumer 失败时无关插件仍能启动和提供服务。
- 保持默认行为兼容；所有新能力默认不改变历史部署和 sample 启动路径。

## 非目标

- 不实现公网插件市场、远程 HTTP 下载、对象存储、发布审批流或账号体系。
- 不支持跨数据源原子事务；多数据源 Saga/Outbox/XA 仍需单独设计。
- 不实现 JPA `EntityManagerFactory` 在线热刷新。
- 不引入管理控制台 UI。
- 不把 no-jpa sample 放回根级旧示例模块。

## 现状/已有流程

| 领域 | 当前状态 | P10 需要补齐 |
| --- | --- | --- |
| 仓库治理 | `offline-index` resolver 可解析 release，管理接口支持 plan/dry-run | 真实 replace、staging cache、回滚候选校验、部署记录补充仓库来源 |
| runtime smoke | Gradle `runtimeSmoke` 包装 PowerShell 脚本并输出 `result.json` | 跨平台 runner、JUnit XML、日志保留策略、CI 产物约定 |
| sample 隔离 | 复杂 JPA sample 覆盖跨插件事务和回滚 | no-jpa/unrelated 插件运行态对照，验证无关插件不受 JPA provider 故障影响 |

## 核心约束

- Java 源码继续兼容 Java 8。
- 公共 API 放在 `pf4boot-api`，运行时实现放在 `pf4boot-core`，管理接口放在 `pf4boot-management-starter`，sample 行为放在 `samples/*`。
- 仓库真实 replace 必须默认关闭或显式 dry-run；不能让旧管理请求意外变成仓库部署。
- 所有写操作继续经过现有 token 鉴权、幂等 key、审计和路径校验。
- 仓库 staging cache 必须只使用配置目录或 `build`/运行时工作目录，不写入用户全局目录。
- smoke 报告不得包含 token、私钥、完整堆栈或敏感绝对路径。

## 影响模块

| 模块 | 职责 |
| --- | --- |
| `pf4boot-api` | 如有必要，补充 repository deployment request/result 字段；优先复用现有模型 |
| `pf4boot-core` | 仓库 release 到 staging cache 的复制、校验、replace 接入和清理 |
| `pf4boot-management-starter` | 管理接口允许仓库来源执行真实 replace，并保持 dry-run/幂等/审计语义 |
| `pf4boot-actuator` | 只读暴露最近仓库执行摘要和 smoke/治理状态，不触发写行为 |
| `samples/cross-plugin-jpa` | 增加 no-jpa/unrelated 插件、跨平台 smoke runner、JUnit XML 产物 |
| `docs/design` | 维护 P10 规划和验收记录，中英文同步 |

## 接口设计

### 仓库真实 replace

管理请求继续使用 `PluginDeploymentRequest`。当请求包含 `repositoryVersion`、`repositoryVersionRange` 或 `repositoryRollback=true` 时，管理层构造 `PluginReleaseRequest`，交给 `PluginDeploymentService.planReplacement(PluginReleaseRequest)` 或新增执行方法。

建议第一版新增执行入口时使用追加 API：

```java
DeploymentRecord replaceFromRepository(PluginReleaseRequest request, PluginDeploymentOptions options);
```

规则：

| 规则 | 行为 |
| --- | --- |
| `dryRun=true` | 只执行解析、校验和 plan，不替换运行时插件 |
| `dryRun=false` 且仓库部署未启用 | 返回明确错误，不回退到 staged path |
| release 校验失败 | 阻断 staging，记录安全错误摘要 |
| staging 成功但 replace 失败 | 复用现有 rollback 编排 |
| 幂等 replay | 返回第一次操作的记录，不重复替换 |

### staging cache

仓库 release 进入 replace 前必须复制到受控 staging cache：

```text
{pluginRepositoryCacheDirectory}/operations/{operationId}/{pluginId}-{version}.zip
```

要求：

- `operationId` 来自管理操作记录或请求 hash。
- 复制后重新计算 sha256。
- cache 路径不写入 HTTP 响应，只返回相对摘要或文件名。
- 操作完成后可按配置保留最近 N 次或按 TTL 清理。

### 跨平台 smoke runner

推荐新增 Java runner，放在 sample 专用源码或 Gradle build logic 中，不作为框架 API：

```text
samples/cross-plugin-jpa/app-run/src/smoke/java/.../RuntimeSmokeRunner.java
```

Gradle task：

```text
:samples:cross-plugin-jpa:app-run:runtimeSmoke
```

参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `-Ppf4bootSmokePort=7791` | `7791` | host 端口 |
| `-Ppf4bootSmokeKeepWorkDir=true` | `false` | 成功后是否保留工作目录 |
| `-Ppf4bootSmokeResultPath=...` | build reports 路径 | `result.json` 输出路径 |
| `-Ppf4bootSmokeJUnitPath=...` | build test-results 路径 | JUnit XML 输出路径 |

### no-jpa/unrelated sample

在 `samples/cross-plugin-jpa` 下新增无 JPA 依赖插件，例如：

```text
plugin-unrelated-api
plugin-unrelated-service
```

插件能力：

- 不依赖 datasource provider。
- 不声明 JPA entity/repository 包。
- 提供一个简单 HTTP endpoint 或 exported service。
- 在 datasource provider 缺失、provider 启动失败、JPA consumer 失败时仍能启动并响应 smoke 检查。

## 数据结构

### 仓库部署记录摘要

部署记录中追加安全摘要字段，避免暴露完整路径：

| 字段 | 说明 |
| --- | --- |
| `repositoryId` | release 来源仓库 |
| `releaseVersion` | 选中的 release 版本 |
| `releaseSha256` | 包摘要 |
| `rollbackCandidate` | 是否存在可回滚 release |
| `stagingRef` | operation 内部 staging 引用，不是绝对路径 |

### smoke JUnit XML

每个 smoke check 映射为一个 testcase：

| check | testcase |
| --- | --- |
| `hostReady` | `RuntimeSmoke.hostReady` |
| `workflowOk` | `RuntimeSmoke.workflowOk` |
| `workflowRollback` | `RuntimeSmoke.workflowRollback` |
| `managementIdempotency` | `RuntimeSmoke.managementIdempotency` |
| `unrelatedPluginAlive` | `RuntimeSmoke.unrelatedPluginAlive` |

## 状态机

### 仓库 replace

```text
REQUESTED
  -> AUTHORIZED
  -> RELEASE_RESOLVED
  -> PACKAGE_VERIFIED
  -> STAGED_TO_CACHE
  -> PLAN_READY
  -> REPLACE_RUNNING
  -> REPLACE_SUCCEEDED / REPLACE_FAILED
  -> ROLLBACK_RUNNING
  -> ROLLBACK_SUCCEEDED / MANUAL_INTERVENTION
```

非法转换：

- `PACKAGE_VERIFIED` 失败不得进入 `STAGED_TO_CACHE`。
- `dryRun=true` 不得进入 `REPLACE_RUNNING`。
- 幂等 replay 不得再次进入 `REPLACE_RUNNING`。

## 时序流程

### 仓库真实部署

1. 管理接口接收 repository replace 请求。
2. 鉴权、幂等、请求 hash 和参数校验通过。
3. 解析 offline index 并选择 release。
4. 校验 release 路径、sha256、trust manifest、版本范围和 capability。
5. 将包复制到 staging cache 并重新计算摘要。
6. 生成 deployment plan。
7. `dryRun=false` 时执行现有 replace。
8. 失败时按现有策略 rollback。
9. 记录 operation、deployment record、repository release 摘要和 cleanup 结果。

### no-jpa 隔离 smoke

1. 启动 host，加载 datasource provider、JPA consumer 和 unrelated 插件。
2. 检查 unrelated endpoint 正常。
3. 触发 JPA rollback 场景，确认 unrelated endpoint 仍正常。
4. 运行 provider 缺失或 provider 失败场景，确认 JPA consumer 失败被记录，无关插件仍启动。
5. 写入 `result.json` 和 JUnit XML。

## 异常处理

| 场景 | 行为 |
| --- | --- |
| repository replace 未启用 | 返回安全错误，提示只允许 dry-run |
| staging cache 写入失败 | fail closed，不执行 replace |
| cache 摘要与索引不一致 | 删除 cache 文件，返回校验失败 |
| replace 成功但记录写入失败 | 按现有审计策略 fail closed 或标记 manual intervention |
| unrelated 插件失败 | smoke 失败，提示隔离能力未达标 |
| JUnit XML 写入失败 | smoke task 失败，但仍尽量保留 `result.json` |

## 幂等性

- repository replace 必须复用管理接口幂等 key。
- 相同 key + 相同 request hash：返回原操作结果。
- 相同 key + 不同 request hash：返回冲突。
- 已执行 replace 的 replay 不得再次调用 PF4J replace。

## 回滚策略

- P10 所有能力通过配置启用；关闭后回到 P7-P9 行为。
- 仓库 replace 失败时复用现有 rollback。
- staging cache 可安全删除；删除不得影响仓库源文件。
- Java smoke runner 出问题时仍可使用 PowerShell 脚本排障。

## 兼容性

- 新 API 只追加，不删除或改变现有方法签名。
- 旧的 staged path plan/replace 请求继续可用。
- `runtimeSmoke` task 名称保持不变；内部 runner 可替换。
- no-jpa sample 只加入 `samples/*`，不影响发布模块。

## 灰度/迁移

1. 先实现 no-jpa sample 和 smoke check，建立隔离验收基线。
2. 再实现 Java smoke runner 和 JUnit XML，保持 PowerShell 脚本可用。
3. 最后开放 repository real replace，先在 sample 中验证，再进入生产使用。

## 测试方案

| 层级 | 覆盖 |
| --- | --- |
| 单元测试 | staging cache 路径、摘要复核、幂等 replay、错误摘要 |
| core 测试 | repository release 执行 replace、失败 rollback、cache 清理 |
| management 测试 | dry-run 与 real replace 鉴权、幂等、冲突和脱敏 |
| sample 测试 | no-jpa 插件正常启动，JPA provider 故障不影响 unrelated 插件 |
| smoke 测试 | Windows/非 Windows runner、`result.json`、JUnit XML、失败保留日志 |

## 风险点

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 仓库 replace 误执行 | 运行时插件被意外替换 | 显式配置开关、dry-run 默认、幂等和审计 |
| staging cache 泄露路径 | 暴露部署目录 | 响应只返回摘要和 stagingRef |
| Java smoke runner 与 PowerShell 行为分叉 | CI 和本地结果不一致 | 共用 check 定义和报告 schema |
| no-jpa sample 过度复杂 | sample 维护成本上升 | 只验证隔离，不承载业务域复杂性 |

## 分阶段实施计划

详细任务和验收状态维护在：

- [plugin-framework-follow-up-hardening-plan.md](plugin-framework-follow-up-hardening-plan.md)
- [plugin-framework-follow-up-hardening-acceptance.md](plugin-framework-follow-up-hardening-acceptance.md)

## 开放问题

- 仓库 real replace 的默认配置名：建议使用 `spring.pf4boot.plugin-repository-replace-enabled=false` 或复用 repository enabled 后增加执行模式。
- staging cache 清理策略：建议第一阶段支持按 operation 结束清理和 `keepWorkDir` 两种，不先做 TTL 后台任务。
- Java smoke runner 放置位置：建议放在 sample 内，避免变成框架公共 API。
