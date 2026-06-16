# 插件框架优先级增强实施规划

## 1. 范围

本文跟踪 [plugin-framework-priority-roadmap.md](plugin-framework-priority-roadmap.md) 的实施任务。优先级固定为：

1. P1 持久化管理/JPA reload 记录；
2. P2 `providerReplacementPath`；
3. P3 Saga/Outbox sample；
4. P4 管理控制台 UI。

## 2. 阶段总览

| 阶段 | 状态 | 目标 | 主要验收 |
| --- | --- | --- | --- |
| P0 设计与规划 | Done | 固化四项优先级和边界 | 中英文设计、规划和索引同步 |
| P1 持久化记录 | Done | 加固管理记录 file store，补 JPA reload 文件记录 | 重启后记录可查、幂等可重放、JPA latest 可恢复 |
| P2 providerReplacementPath | Done | JPA reload 支持 staged provider 包替换 | 成功替换、失败回滚、unrelated 不受影响 |
| P3 Saga/Outbox sample | Planned | 演示跨 domain 最终一致性 | 成功、重复投递、失败重试 runtime smoke |
| P4 管理控制台 UI | Planned | 独立 sample UI 消费 HTTP API/Actuator | 本地 UI smoke、鉴权和幂等展示 |

## 3. P1 持久化记录

### 3.1 影响模块

- `pf4boot-management-starter`
- `pf4boot-jpa`
- `pf4boot-jpa-starter`
- `pf4boot-actuator`
- `samples/cross-plugin-jpa`

### 3.2 任务

1. 复验现有 `FilePluginOperationStore`：
   - 原子写入；
   - 幂等 key 重启后可命中；
   - `scanRecoverableRecords()` 覆盖执行中记录；
   - 读到损坏文件时按 `failClosed` 行为处理。
2. 复验现有 `FilePluginDeploymentRecordStore`：
   - `save/findById/recent`；
   - recent 排序稳定；
   - 不输出敏感绝对路径。
3. 扩展 `JpaDomainReloadRecordRepository`：
   - default `recent(int limit)`；
   - default `scanRecoverableRecords()`；
   - 保持二进制兼容。
4. 新增 `FileJpaDomainReloadRecordRepository`：
   - `save/findById/findByIdempotencyKey/bindIdempotencyKey/findLatest/recent`；
   - 写 `latest.json`；
   - idempotency key 文件名安全编码。
5. 扩展 `Pf4bootJpaProperties.DomainReload`：
   - `recordStore.type`；
   - `recordStore.directory`；
   - `recordStore.failClosed`。
6. 更新 `JpaDomainReloadAutoConfiguration`：
   - `memory` 默认；
   - `file` 时创建文件 repository；
   - 初始化失败按 `failClosed` 处理。
7. Actuator `pf4bootjpareload` 增加记录存储摘要：
   - `recordStoreType`；
   - `recoverableRecordCount`；
   - `latestReloadId`。
8. sample runtime smoke 增加 file store 模式：
   - 启动一次执行管理/JPA reload；
   - 停止后重启；
   - 查询旧 operation/deployment/JPA reload 记录仍存在。

### 3.3 验收标准

| 验收项 | 证据 |
| --- | --- |
| P1-AC1：operation file store 重启后保留幂等记录 | `pf4boot-management-starter:test` |
| P1-AC2：deployment file store recent/findById 稳定 | `pf4boot-management-starter:test` |
| P1-AC3：JPA reload file repository 支持 latest/recent/idempotency | `pf4boot-jpa-starter:test` |
| P1-AC4：损坏记录按 failClosed/failOpen 处理 | 单元测试 |
| P1-AC5：runtime smoke file-store 重启后可查询记录 | `:samples:cross-plugin-jpa:app-run:runtimeSmoke` |
| P1-AC6：文档和英文翻译同步 | `git diff --check`、U+FFFD 扫描 |

### 3.4 实施记录

- 已加固 management operation/deployment 记录的 recent 稳定排序，并补充损坏记录跳过测试。
- 已新增 `FileJpaDomainReloadRecordRepository`，支持 JSON Lines 记录、`latest.json`、幂等索引、fail-open/fail-closed。
- 已新增 `pf4boot.plugin.jpa.domain-reload.record-store.*` 配置，默认 `memory`，配置为 `file` 时启用文件仓库。
- 已增强 `/actuator/pf4bootjpareload`，输出 `recordStoreType`、`latestReloadId`、`recentRecordCount`、`recoverableRecordCount`。
- 已增强 `samples/cross-plugin-jpa` runtime smoke，覆盖 JPA reload 成功后宿主重启仍可读 latest reload。

## 4. P2 `providerReplacementPath`

### 4.1 影响模块

- `pf4boot-jpa`
- `pf4boot-jpa-starter`
- `pf4boot-core`
- `pf4boot-management-starter`
- `samples/cross-plugin-jpa`

### 4.2 任务

1. 扩展 failure/blocker code：
   - `PROVIDER_REPLACEMENT_MISMATCH`；
   - `PROVIDER_REPLACEMENT_VERIFY_FAILED`；
   - `PROVIDER_REPLACEMENT_ACTIVATION_FAILED`；
   - `PROVIDER_REPLACEMENT_ROLLBACK_FAILED`。
2. plan 阶段解析 `providerReplacementPath`：
   - 路径白名单在 staging root 内；
   - 包 descriptor pluginId 必须等于当前 provider；
   - 版本、checksum、trust manifest 走现有 verifier；
   - dry-run 输出 replacement 摘要。
3. 接入 provider replacement adapter：
   - 复用 `PluginDeploymentService` 包校验、drain、影响链停启和回滚能力；
   - 不让 core 依赖 JPA 类型；
   - JPA reload service 负责将部署结果映射到 reload record，并在替换后校验 JPA descriptor ready。
4. execute 阶段：
   - 委托 `PluginDeploymentService.replace(...)` 完成 drain、停启影响链、激活 staged provider 和回滚；
   - 等待并校验 descriptor ready；
   - 写入 replacement 摘要。
5. 失败恢复：
   - staged provider 启动失败时恢复旧 provider；
   - 恢复旧 provider 成功后启动 consumers；
   - 恢复失败进入 `MANUAL_INTERVENTION_REQUIRED`；
   - unrelated 插件保持可用。
6. 管理接口响应和 record 展示 replacement 摘要：
   - staged 包文件名；
   - staged 包摘要；
   - old/new provider version；
   - rollback status。
7. runtime smoke：
   - 成功 provider replacement；
   - staged 包 pluginId 不匹配；
   - staged provider 启动失败并回滚；
   - unrelated 仍 200。

### 4.3 验收标准

| 验收项 | 证据 |
| --- | --- |
| P2-AC1：非 staging root 路径被拒绝 | 单元测试 |
| P2-AC2：provider pluginId 不匹配生成 blocker | plan service 测试 |
| P2-AC3：成功替换后 descriptor ready 且 consumers 恢复 | service 测试 + runtime smoke |
| P2-AC4：替换失败回滚旧 provider | service 测试 |
| P2-AC5：unrelated 插件不受影响 | runtime smoke |
| P2-AC6：record 持久化 replacement 摘要 | repository 测试 |

### 4.4 实施记录

- 已新增 provider replacement 失败码和 `JpaProviderReplacementSummary`。
- 已让 JPA reload plan 在存在 `providerReplacementPath` 时调用 `PluginDeploymentService.planReplacement(...)` 做 staged 包预检。
- 已让 JPA reload execute 在存在 `providerReplacementPath` 时调用 `PluginDeploymentService.replace(...)`，并把部署结果写入 reload record。
- 已让管理 HTTP 入口复用 staging-root 路径白名单，避免 JPA reload 绕过部署路径治理。
- 已增强 `samples/cross-plugin-jpa` runtime smoke，覆盖 `SMOKE_JPA_PROVIDER_REPLACEMENT_PATH`。

## 5. P3 Saga/Outbox Sample

### 5.1 影响模块

- `samples/saga-outbox/*`
- `settings.gradle`
- `docs/design`

### 5.2 任务

1. 新增独立 multi-module sample：
   - `demo-host`；
   - `app-run`；
   - `model-order`；
   - `model-billing`；
   - `plugin-order-domain`；
   - `plugin-billing-domain`；
   - `plugin-order-service`；
   - `plugin-billing-service`；
   - `plugin-outbox-dispatcher`。
2. 设计两套 JPA domain：
   - `order` domain；
   - `billing` domain。
3. 实体分组：
   - order：`OrderRecord`、`OutboxEvent`；
   - billing：`BillingAccount`、`InboxEvent`。
4. 业务接口：
   - 创建订单；
   - 查询订单状态；
   - 查询 billing 状态；
   - 触发 dispatcher tick；
   - 注入 billing 临时失败。
5. Outbox dispatcher：
   - 读取待发送事件；
   - 调用 billing service；
   - 成功标记 SENT；
   - 失败增加 retry count；
   - 超限进入 DEAD。
6. Inbox 幂等：
   - eventId 唯一；
   - 重复消费返回已处理，不重复扣款。
7. runtime smoke：
   - 成功路径；
   - 重复投递；
   - 失败重试；
   - 明确跨 datasource 原子事务不支持。
8. README 写清楚这是业务模式，不是框架事务能力。

### 5.3 验收标准

| 验收项 | 证据 |
| --- | --- |
| P3-AC1：sample 能 assemble runtime | Gradle task |
| P3-AC2：成功路径完成订单和账务 | runtime smoke |
| P3-AC3：重复投递不重复扣款 | runtime smoke |
| P3-AC4：失败后可重试成功 | runtime smoke |
| P3-AC5：文档明确非原子事务 | README + 设计文档 |

## 6. P4 管理控制台 UI

### 6.1 影响模块

- `samples/plugin-management-console/*`
- `samples/cross-plugin-jpa`
- `docs/design`

### 6.2 任务

1. 先输出 API contract 清单：
   - 插件列表；
   - 启停；
   - deployment plan/replace/rollback/confirm；
   - JPA reload plan/reload/record/current；
   - Actuator snapshot；
   - metrics。
2. 新增独立 sample UI：
   - 不进入 `pf4boot-*` 发布模块；
   - 可以选择轻量静态页面或独立前端工程；
   - 默认连接本地 `127.0.0.1:7791`。
3. UI 视图：
   - 插件列表；
   - 部署记录；
   - JPA reload；
   - 治理/Actuator；
   - 操作结果。
4. 写操作：
   - 必填 token；
   - 自动生成 `X-Idempotency-Key`；
   - 先 plan 后 confirm/replace；
   - 展示 blockers/warnings。
5. 本地 smoke：
   - 打开 UI；
   - 读取插件列表；
   - 执行 dry-run plan；
   - 鉴权失败展示；
   - 幂等 replay 展示。

### 6.3 验收标准

| 验收项 | 证据 |
| --- | --- |
| P4-AC1：UI 不被任何发布模块依赖 | Gradle/module 检查 |
| P4-AC2：UI 只调用 HTTP API/Actuator | API mock/contract 测试 |
| P4-AC3：写操作携带 token 和 idempotency key | UI 测试 |
| P4-AC4：plan blockers/warnings 可见 | UI 测试 |
| P4-AC5：本地 sample smoke 可运行 | Browser/Playwright 或等价 smoke |

## 7. 文档要求

每个阶段完成时必须更新：

- 本规划状态；
- 对应设计文档；
- 英文翻译；
- sample README；
- 验收记录。

文档检查：

- `git diff --check`
- 实际 U+FFFD 替换字符扫描

## 8. 推荐提交切分

| 提交 | 内容 |
| --- | --- |
| P0 | 设计和规划文档 |
| P1a | 管理 file store 复验和测试加固 |
| P1b | JPA reload file repository |
| P1c | runtime smoke 持久化记录 |
| P2a | providerReplacementPath plan/blocker |
| P2b | provider replacement execute/rollback |
| P2c | sample smoke |
| P3a | Saga/Outbox sample 模块骨架 |
| P3b | Outbox/Inbox 业务闭环 |
| P3c | sample smoke 和文档 |
| P4a | API contract 和 UI 骨架 |
| P4b | 主要视图和操作 |
| P4c | UI smoke 和文档 |
