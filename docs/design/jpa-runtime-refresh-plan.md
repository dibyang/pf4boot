# JPA 运行时刷新实施规划

## 1. 范围

本文跟踪 [jpa-runtime-refresh.md](jpa-runtime-refresh.md) 的实施任务。目标是分阶段交付 JPA domain 的运行时刷新能力：先可观测、再可规划、最后在显式配置下执行重启式刷新。

## 2. 阶段总览

| 阶段 | 状态 | 目标 |
| --- | --- | --- |
| R0 文档和现状对齐 | Done | 设计、规划、验收文档完整，旧验收状态不误导后续实施 |
| R1 公共模型和配置 | Done | 新增 reload 公共模型、配置属性和禁用态行为 |
| R2 PLAN_ONLY 影响范围识别 | Done | 能输出 provider、consumer、unrelated、顺序和阻断项 |
| R3 Binding registry 和精确 consumer 识别 | Done | shared consumer 启动时登记绑定，停止时清理 |
| R4 管理接口和 Actuator 只读观测 | Done | 提供 plan/reload 查询入口，默认不执行变更 |
| R5 执行模式：停止 consumers 并重启 provider | Done | 在显式配置下完成一个 domain 的安全刷新 |
| R6 Runtime smoke 和样例扩展 | Done | 端到端验证 plan、成功刷新、失败隔离和报告 |
| R7 文档、迁移指南和验收收口 | Done | 更新开发指南、示例 README 和验收记录 |

## 2.1 V1 开发边界

V1 采用重启式刷新，分为 `V1-Plan` 和 `V1-Execute`：

- `V1-Plan` 必须先完成：公共模型、配置、binding registry、PLAN_ONLY、管理 plan 接口、Actuator 只读摘要。
- `V1-Execute` 在 `V1-Plan` 稳定后完成：执行模式、provider 重启、consumer stop/start、record、runtime smoke。

V1 不实现：

- provider 内部 EMF 原地重建；
- provider 包替换；
- 跨数据源或多 domain 原子刷新；
- 持久化 reload record；
- 生产无停顿刷新承诺。

## 3. R0 文档和现状对齐

### 任务

1. 新增设计文档 `docs/design/jpa-runtime-refresh.md` 和英文翻译。
2. 新增实施规划 `docs/design/jpa-runtime-refresh-plan.md` 和英文翻译。
3. 新增验收清单 `docs/design/jpa-runtime-refresh-acceptance.md` 和英文翻译。
4. 更新 `docs/design/README.md` 和 `docs/design/en/README.md` 索引。
5. 将复杂 JPA 示例中 provider 隔离的旧“部分通过”记录同步为 P10 已完成。

### 验证

```powershell
git diff --check
rg -n "U\+FFFD" docs/design docs/design/en
```

## 4. R1 公共模型和配置

### 影响模块

- `pf4boot-jpa`
- `pf4boot-jpa-starter`
- `pf4boot-actuator`

### 任务

1. 在 `pf4boot-jpa` 新增 `net.xdob.pf4boot.jpa.reload` 包。
2. 新增 `JpaDomainReloadMode`：`DISABLED`、`PLAN_ONLY`、`STOP_CONSUMERS_AND_REBUILD`。
3. 新增 `JpaDomainReloadState`：覆盖 planned、draining、stop/start、health、success、failed、manual intervention。
4. 新增 `JpaDomainReloadFailureCode`，至少包含设计文档中的 blocker code 和执行失败 code。
5. 新增 `JpaDomainReloadRequest`，包含 domain、mode、幂等键、reason、timeout 和 V1 不支持的 `providerReplacementPath` 字段。
6. 新增 `JpaDomainReloadPlan`，包含 provider、descriptor、consumers、unrelated、orders、warnings、blockers、executable。
7. 新增 `JpaDomainReloadRecord`，包含 reloadId、planId、state、request、plan、transitions、failure、rollback summary。
8. 新增 `JpaDomainConsumer` 和 detection 枚举。
9. 新增 `JpaDomainReloadService` 和 `JpaDomainReloadPlanService` 接口。
10. 在 starter 配置中新增 `pf4boot.plugin.jpa.domain-reload.*`。
11. 默认 `mode=DISABLED`，禁用态不注册执行服务，或者服务只返回明确的 `RELOAD_DISABLED` blocker。
12. 单元测试覆盖默认配置、枚举解析、request 校验和 Java 8 编译。

### 验证

```powershell
.\gradlew.bat :pf4boot-jpa:compileJava :pf4boot-jpa-starter:compileJava
```

## 5. R2 PLAN_ONLY 影响范围识别

### 影响模块

- `pf4boot-jpa-starter`
- `pf4boot-core`
- `samples/cross-plugin-jpa`

### 任务

1. 实现 `DefaultJpaDomainReloadPlanService`。
2. 从平台上下文读取 `domain.{domainId}.descriptor`。
3. descriptor 不存在时返回 `DOMAIN_NOT_FOUND`。
4. descriptor `ready=false` 时返回 `DOMAIN_NOT_READY`。
5. 校验 provider plugin 运行状态，失败时返回 `PROVIDER_NOT_RUNNING`。
6. 通过 PF4J 依赖图识别 provider 的直接和间接依赖者。
7. 结合 binding registry 精确识别 shared consumer。
8. 无法精确识别但依赖 provider 的候选插件标记为 `INFERRED_DEPENDENCY`。
9. 计算停止顺序：依赖链下游优先停止。
10. 计算启动顺序：provider ready 后，consumer 按依赖上游优先启动。
11. 输出 unrelated 插件列表及原因。
12. 输出 blockers 和 warnings。
13. 输出稳定排序，避免测试和 UI 抖动。
14. 为 sample 增加 plan-only 单元或集成测试。

### 验证

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test
```

## 6. R3 Binding registry 和精确 consumer 识别

### 影响模块

- `pf4boot-jpa-starter`

### 任务

1. 新增 `JpaPluginBindingRegistry`，按 pluginId 登记 `JpaPluginBinding`。
2. Registry 必须线程安全，支持 register、remove、findByPluginId、findByDomainId、snapshot。
3. `PluginJPAStarter` 在 shared 模式绑定校验成功后注册绑定。
4. 插件停止或 context 关闭时移除绑定，避免旧绑定泄漏。
5. `JpaDomainConsumerResolver` 优先使用 registry 精确识别 `mode=SHARED` 且 `domainId` 匹配的 consumer。
6. 保留依赖图 fallback，并把 fallback 标为 `INFERRED_DEPENDENCY`。
7. `mode=LOCAL` 或绑定其它 domain 的插件必须被排除。
8. 测试 provider 缺失、consumer 停止清理、多个 domain、local mode 不纳入 plan。

### 验证

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-core:test
```

## 7. R4 管理接口和 Actuator 只读观测

### 影响模块

- `pf4boot-management-starter`
- `pf4boot-actuator`
- `pf4boot-jpa`

### 任务

1. 管理接口在 `JpaDomainReloadService` Bean 存在时条件注册。
2. 新增 `POST /pf4boot/admin/jpa/domains/{domainId}/reload/plan`。
3. 新增 `POST /pf4boot/admin/jpa/domains/{domainId}/reload`，V1-Plan 阶段返回 `PLAN_ONLY_MODE` 或 `RELOAD_DISABLED`，不得执行。
4. 新增 `GET /pf4boot/admin/jpa/reloads/{reloadId}`。
5. 新增 `GET /pf4boot/admin/jpa/domains/{domainId}/reload/current`。
6. Actuator 增加只读摘要，不提供执行入口。
7. 复用现有管理接口鉴权、安全治理、审计和 idempotency 约束。
8. 测试默认禁用态、PLAN_ONLY、未注册服务时接口不可见或返回能力未启用。

### 验证

```powershell
.\gradlew.bat :pf4boot-management-starter:test :pf4boot-actuator:test
```

## 8. R5 执行模式：停止 consumers 并重启 provider

### 影响模块

- `pf4boot-jpa-starter`
- `pf4boot-jpa-domain-starter`
- `pf4boot-core`
- `pf4boot-management-starter`

### 任务

1. 新增 per-domain reload 锁，并默认全局串行执行 reload。
2. 实现内存 `JpaDomainReloadRecordRepository`，支持 ring buffer 和 `idempotencyKey -> reloadId`。
3. 接入 idempotency gate，同一个幂等键返回同一 record。
4. 构建 plan 并校验 `executable=true`。
5. 非空 `providerReplacementPath` 在 V1 阶段返回 `UNSUPPORTED_REPLACEMENT_PATH`；后续 P2 已改为接入 `PluginDeploymentService`，见 `plugin-framework-priority-roadmap-plan.md`。
6. 执行 drain；V1 如果没有 drain SPI，则记录 warning，不阻塞。
7. 按 stopOrder 停止 consumer 插件。
8. 停止 provider 插件。
9. 校验旧 descriptor、EMF、TM、DataSource 导出 Bean 已注销。
10. 启动 provider 插件。
11. 等待新 descriptor ready，并校验 bean names 和 ready 状态。
12. 按 startOrder 启动 consumers。
13. 执行健康检查。
14. 记录 state transitions 和 failure code。
15. 失败时按设计文档执行恢复或标记人工介入。

### 验证

```powershell
.\gradlew.bat :pf4boot-core:test :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-management-starter:test
```

## 9. R6 Runtime smoke 和样例扩展

### 影响模块

- `samples/cross-plugin-jpa`

### 任务

1. sample host 增加 domain reload 配置开关。
2. runtime smoke 增加 `jpaReloadPlanOnly` 检查。
3. runtime smoke 增加 `jpaReloadDisabledNoMutation` 检查。
4. runtime smoke 增加 `jpaReloadSuccess` 检查。
5. runtime smoke 增加 `jpaReloadIdempotency` 检查。
6. runtime smoke 增加 provider reload 失败注入，确认 unrelated 插件仍返回 200。
7. `result.json` 和 JUnit XML 增加 reload 检查项。
8. README 增加 JPA 运行时刷新配置和 HTTP 示例。
9. 报告不得输出敏感路径、完整堆栈或 token。

### 验证

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

## 10. R7 文档、迁移指南和验收收口

### 任务

1. 更新 [plugin-developer-guide.md](plugin-developer-guide.md) 的 JPA 章节。
2. 更新 [jpa-integration.md](jpa-integration.md) 的 shared domain 运行时刷新边界。
3. 更新 [plugin-http-management-api.md](plugin-http-management-api.md) 和实施指南中的 JPA reload 接口。
4. 同步英文翻译。
5. 根据实际测试结果更新 [jpa-runtime-refresh-acceptance.md](jpa-runtime-refresh-acceptance.md)。
6. 明确剩余后续方向：跨数据源事务、持久化 reload 记录、provider 内部热重建。

### 验证

```powershell
git diff --check
rg -n "U\+FFFD" docs/design docs/design/en samples/cross-plugin-jpa
.\gradlew.bat build
```

说明：根构建当前会禁用名字包含 `test` 的任务，不能把 `build` 等同于完整测试。涉及行为变更时必须同时运行上述窄范围 test 和 runtime smoke。

## 11. 实施顺序建议

V1 已按以下顺序完成：

1. R1 公共模型和配置。
2. R3 binding registry。
3. R2 PLAN_ONLY plan service。
4. R4 管理 plan 接口和 Actuator 只读摘要。
5. R5 record repository、锁和 execute service。
6. R6 runtime smoke 和 sample README。
7. R7 文档和验收收口。

每批提交前至少运行对应模块的窄范围测试。R5 以后必须运行 runtime smoke。

## 12. 回滚策略

1. R1-R4 都是新增能力，默认禁用；发现问题可关闭 `pf4boot.plugin.jpa.domain-reload.mode`。
2. R5 执行能力必须在默认禁用下发布；生产启用前先运行 `PLAN_ONLY`。
3. 若执行模式出现恢复问题，保留 `PLAN_ONLY` 和只读观测，禁用执行模式。
4. 对 sample 或管理接口的失败，不影响已有 shared JPA 业务能力。

## 13. V1 后续演进

1. 引入持久化 `JpaDomainReloadRecordRepository`，支持重启后查询历史 reload。
2. 增加 drain SPI，让业务插件可以声明正在处理的请求、事务或后台任务。
3. 支持 provider 包替换和更完整的回滚策略。
4. 评估 provider 内部 EMF 原地重建，但必须先证明 Hibernate/Spring Data JPA 代理和事务边界可安全切换。
5. 评估跨 domain/跨数据源事务，但 V1 明确不支持。
