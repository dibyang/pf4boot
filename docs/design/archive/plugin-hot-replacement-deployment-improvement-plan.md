# 插件热替换部署改进实施规划

## 1. 目标与范围

本规划用于跟踪插件热替换部署从“生命周期操作”升级为“可审计、可回滚、可验收部署流程”的实施过程。

范围包括：

- 热替换部署状态机。
- 部署计划与记录。
- 依赖链影响范围计算。
- drain、停止、替包、启动、健康检查、回滚流程。
- 资源清理验证。
- 版本兼容和包校验。
- 管理 API/CLI 使用的统一部署服务。

不包括：

- 分布式流量网关接入。
- 零中断强一致切换。
- JTA/XA 或跨数据源事务迁移。
- 任意 class 级别热替换。

## 2. 里程碑

| 里程碑 | 目标 | 交付物 | 通过条件 |
| --- | --- | --- | --- |
| M1 方案冻结 | 锁定部署状态机和边界 | 改进方案文档、本规划 | 评审通过，确认不改底层 lifecycle 语义 |
| M2 部署记录与预检 | 可生成替换计划但不执行 | `DeploymentPlan`、`DeploymentRecord`、预检逻辑 | 能输出影响范围和兼容性结果 |
| M3 基础替换与回滚 | 实现短暂停机式安全替换 | stop/replace/load/start/rollback 编排 | 新版本失败可恢复旧版本 |
| M4 drain 与资源清理验证 | 防止请求/任务打到半卸载插件 | Web drain、任务暂停、清理检查 | stop 后无 Web/Bean/task 残留 |
| M5 健康检查与可观测 | 替换后自动判断成功 | health probe、日志、metrics | 健康失败自动回滚 |
| M6 JPA/数据库约束 | 对有状态插件增加保护 | JPA drain、schema 约束文档、测试 | provider/consumer 替换顺序可控 |
| M7 文档和验收收口 | 形成可复查部署能力 | 开发指南、验收记录、示例脚本 | 文档、实现、验收一致 |

## 3. 任务拆解

### M1 方案冻结

- [x] 明确 `reloadPlugin` 不是安全热替换部署。
- [x] 明确新增部署编排层，不直接改变底层生命周期契约。
- [x] 明确第一阶段目标是可控短暂停机和自动回滚。
- [x] 明确热替换不混入跨插件事务规划。
- [x] 评审是否接受部署状态机。
- [x] 评审 staged/backup/failed 目录约定。

评审结论：

- 接受 `PLANNED -> PRECHECKED -> APPLYING -> STARTING -> VERIFYING -> SUCCEEDED` 的主路径。
- 接受失败路径统一进入 `FAILED`，可回滚失败进入 `MANUAL_INTERVENTION`。
- `staged/backup/failed` 第一阶段只作为部署服务内部目录，不改变现有插件仓库对外约定。
- `reloadPlugin` 保持低层生命周期操作语义；安全替换由部署编排服务承载。

### M2 部署记录与预检

- [x] 定义 `DeploymentPlan`。
- [x] 定义 `DeploymentRecord`。
- [x] 定义 `DeploymentState`。
- [x] 解析 staged 插件包 descriptor。
- [x] 校验插件 ID、版本、requires、依赖范围和框架版本。
- [x] 计算影响范围：目标插件和所有 dependents。
- [x] 保存 `RollbackSnapshot`。
- [x] 增加预检单元测试。

实现说明：

- 公共模型和入口位于 `pf4boot-api` 的 `net.xdob.pf4boot.deployment` 包。
- 默认只读预检实现位于 `pf4boot-core` 的 `DefaultPluginDeploymentService`。
- `pf4boot-starter` 自动装配 `PluginDeploymentService`，供后续管理 API/CLI 复用。
- `planReplacement` 只解析、校验并生成记录，不调用 `stop/load/start/unload`，满足 AC-01。

### M3 基础替换与回滚

- [x] 新增 `PluginDeploymentService`。
- [x] 实现替换流程：stop dependents -> stop target -> activate package -> load -> start target -> start dependents。
- [x] 实现回滚流程：stop new -> restore old package -> load old -> start old dependency chain。
- [x] 替换和回滚都写入 `DeploymentRecord`。
- [x] 替换失败进入 `ROLLING_BACK`。
- [x] 回滚失败进入 `MANUAL_INTERVENTION`。
- [x] 增加成功替换、启动失败回滚、包激活失败回滚测试。

实现说明：

- `PluginDeploymentService.replace(...)` 先复用 M2 预检计划，预检失败不执行生命周期动作。
- 成功路径按 `stopOrder` 停止并卸载影响链，再加载 staged 目标包和受影响 dependents，最后按 `startOrder` 启动。
- 回滚路径使用 `RollbackSnapshot` 中的旧包路径和原启动状态恢复旧版本。
- `DeploymentRecord.stateHistory` 保留 `ROLLING_BACK`、`MANUAL_INTERVENTION` 等中间状态，便于审计。
- 本阶段的包激活先以 staged 路径加载实现；`staged/backup/failed` 物理目录归档留给后续部署包管理增强。

### M4 drain 与资源清理验证

- [x] Web 层支持插件级 draining 标记。
- [x] draining 状态下拒绝新请求或返回维护响应。
- [x] 统计并等待插件在途请求归零。
- [x] 暂停目标影响链中的定时任务。
- [x] 等待正在执行任务结束。
- [x] stop 后校验 controller/interceptor/mapping 已清理。
- [x] stop 后校验共享 Bean、extension、scheduled task 已清理。
- [x] stop 后校验插件上下文和 classloader 无明显残留。
- [x] 增加 drain 超时和清理失败测试。

实现说明：

- `PluginTrafficDrainer` 负责在部署停止前摘流并等待在途工作归零。
- `PluginCleanupVerifier` 负责 stop 后、unload 前验证模块级资源已清理，ERROR 会触发回滚。
- `PluginRequestMappingHandlerMapping` 提供 Web draining、503 维护响应、in-flight 计数和 Web mapping/interceptor 清理验证。
- `DefaultScheduledMgr` 在 draining 状态下跳过新定时任务，并等待已运行任务结束。
- `DefaultShareBeanMgr` 参与部署 drain，并验证共享 Bean、extension Bean、scheduled task 无残留。
- 本阶段对 classloader 的验证以“插件上下文已注销、Web/core 资源无 classloader 持有”为主；弱引用/GC 级别检测留给后续可观测增强。

### M5 健康检查与可观测

- [x] 定义 `PluginHealthProbe` 扩展点。
- [x] 支持插件本地 health probe bean。
- [x] 默认 health check 包含插件状态、共享 Bean、Web endpoint、JPA 可用性。
- [x] 输出 deployment id、状态、耗时、影响范围和错误码。
- [x] 增加 metrics。
- [x] 健康检查失败自动回滚。
- [x] 增加 health probe 成功/失败测试。

实现说明：

- `PluginHealthProbe` 位于 `pf4boot-api`，插件可在自身 Spring 上下文暴露本地健康检查 Bean。
- `PluginHealthVerifier` 位于 `pf4boot-api`，core/web/jpa 等框架模块可把自身资源状态纳入默认健康检查。
- `DefaultPluginDeploymentService` 在影响链全部启动后进入 `VERIFYING`，先检查插件状态和启动错误，再执行模块级 verifier 和插件本地 probe。
- 任一健康检查返回 ERROR 或 null 都会触发自动回滚，回滚后也会执行旧版本健康检查。
- `DeploymentRecord` 增加 `durationMillis` 和 `errorCode`，并通过 `stateHistory` 保留 `VERIFYING`。
- `DefaultPluginDeploymentRecorder` 提供第一阶段内存记录和指标快照。
- `pf4boot-actuator` 在可选引入时导出部署总数、回滚数、失败数和最近部署耗时指标。
- `DefaultShareBeanMgr` 和 `PluginRequestMappingHandlerMapping` 已接入模块级健康验证；JPA 可用性 verifier 在 M6 随数据库约束一起实现。

### M6 JPA/数据库约束

- [x] 替换 JPA domain provider 前停止所有依赖 consumer。
- [x] 等待当前事务完成或超时失败。
- [x] 检查 DataSource/EMF/TM 已关闭或从 platform 注销。
- [x] 文档声明生产环境不依赖 `ddl-auto=update` 做 schema 迁移。
- [x] 给出 expand/contract schema 迁移模板。
- [x] 增加 JPA provider 替换顺序和失败回滚测试。

实现说明：

- JPA domain provider 本质上仍是普通插件，热替换部署沿用 M2/M3 的依赖图：停止顺序为所有依赖 consumer -> provider，启动顺序为 provider -> consumer。
- `PluginTrafficDrainer` 是事务等待的统一扩展点。第一阶段由 Web/定时任务 drain 阻止新的业务入口并等待在途工作归零；JPA 本地事务不再新增单独状态机，避免和 Spring 事务拦截器重复建模。
- `pf4boot-jpa-starter` 增加 `JpaPluginDeploymentVerifier`，宿主引入 JPA starter 后自动参与部署服务。启动后检查插件上下文中的 `DataSource`、`EntityManagerFactory`、`PlatformTransactionManager`；停止后确认这些 JPA 资源没有残留。
- 领域数据源能力插件仍由 `pf4boot-jpa-domain-starter` 负责创建并导出单个事务域。依赖它的业务插件共享同一个事务环境；多个数据源使用多个领域插件，跨数据源事务继续保持不支持。
- 生产环境禁止把 `spring.jpa.hibernate.ddl-auto=update` 当作 schema 迁移方案。插件包预检和健康检查只验证运行资源，不承担建表、改表、数据回填职责。
- 有 schema 变更的插件必须按 expand/contract 发布：先发布兼容旧代码的新结构，再发布使用新结构的新插件，最后在确认无回滚需求后清理旧结构。

expand/contract 模板：

| 阶段 | 数据库动作 | 插件动作 | 回滚要求 |
| --- | --- | --- | --- |
| Expand | 新增可空列、新表、新索引或兼容视图；不删除旧字段 | 旧插件继续运行 | 旧插件能忽略新增结构 |
| Backfill | 后台补齐新字段或新表数据；过程幂等可重入 | 旧插件继续运行或灰度读新结构 | 补数据失败可重复执行，不影响旧读写 |
| Switch | 热替换 provider/consumer，代码开始读写新结构 | 走 `PluginDeploymentService.replace(...)` | 健康失败自动回滚旧插件，旧结构仍可用 |
| Contract | 删除旧列、旧表、旧索引或旧视图 | 所有实例确认新版本稳定后单独执行 | Contract 后不再保证旧插件可回滚 |

约束：

- provider 替换只保证单数据源事务域内的一致性；如果插件依赖多个数据源，需要把实体和 Repository 按数据源包分组，并在插件内为每个数据源显式配置扫描路径。
- schema 迁移应由运维脚本、Flyway/Liquibase 或宿主约定的迁移流程执行；pf4boot 第一阶段只提供替换编排、drain、健康检查和清理验证。
- 回滚窗口内不得执行破坏旧版本兼容性的 Contract 操作。

### M7 文档和验收收口

- [x] 更新 `plugin-lifecycle.md`，说明 lifecycle 操作与部署编排的关系。
- [x] 更新 `plugin-loading-and-packaging.md`，说明 staged/backup/failed 包管理。
- [x] 更新 `context-and-bean-sharing.md`，说明热替换清理验证。
- [x] 更新 `web-integration.md`，说明 draining 和 mapping 摘除。
- [x] 新增热替换验收文档。
- [x] 同步英文翻译。
- [x] 补齐示例命令和运维脚本说明。

实现说明：

- `plugin-lifecycle.md` 明确 `reloadPlugin`、`restartPlugin`、`upgradePlugin` 是生命周期原语，发布级安全热替换应使用 `PluginDeploymentService.replace(...)`。
- `plugin-loading-and-packaging.md` 明确第一阶段不改变公开插件仓库格式，`staged/backup/failed` 是部署流程约定，物理目录归档由运维流程负责。
- `context-and-bean-sharing.md` 明确 `DefaultShareBeanMgr` 在 drain、cleanup verifier、health verifier 中的职责。
- `web-integration.md` 明确 Web drain 503、in-flight 计数、mapping/interceptor 残留检查。
- `plugin-hot-replacement-deployment-acceptance.md` 作为独立验收记录，列出 AC-01 到 AC-12 的证据、推荐验证命令和手工 smoke 脚本。

## 4. 验收清单

| 编号 | 验收项 | 通过标准 |
| --- | --- | --- |
| AC-01 | 预检不改运行态 | 只生成计划和记录，不 stop/load/start |
| AC-02 | 影响范围准确 | dependents 列表与 PF4J 依赖图一致 |
| AC-03 | 停止顺序正确 | 先停 dependents，再停 target |
| AC-04 | 启动顺序正确 | 先启动 target，再启动 dependents |
| AC-05 | 新包失败可回滚 | 加载/启动/健康失败均恢复旧版本 |
| AC-06 | 回滚失败可诊断 | 进入人工介入状态并保留错误记录 |
| AC-07 | Web drain 生效 | draining 后新请求不进入目标插件 |
| AC-08 | 在途请求可等待 | 超时前归零则继续，超时则失败回滚 |
| AC-09 | 资源清理可验证 | stop 后 mapping/Bean/task 无残留 |
| AC-10 | JPA provider 顺序安全 | provider 替换前 consumer 已停止，恢复时顺序相反，JPA 资源健康失败可回滚 |
| AC-11 | 健康检查可扩展 | 插件自定义 health probe 能参与决策 |
| AC-12 | 旧 API 兼容 | 原有 start/stop/reload/delete 行为不变 |

## 5. 推荐验证命令

生命周期与核心编译：

```powershell
.\gradlew.bat :pf4boot-core:compileJava :pf4boot-starter:compileJava
```

Web 清理相关：

```powershell
.\gradlew.bat :pf4boot-web-starter:compileJava :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

JPA 插件相关：

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test
```

插件包验证：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

手工 smoke 建议：

```text
1. 启动 sample demo host。
2. 访问 plugin controller，确认旧版本服务正常。
3. 提交 staged 新插件包。
4. 执行 plan，确认影响范围。
5. 执行 replace，观察 drain/stop/replace/start/health。
6. 访问 plugin controller，确认新版本服务正常。
7. 使用故障包重复 replace，确认自动回滚旧版本。
```

## 6. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| drain 无法准确统计在途请求 | 替换时仍有请求进入旧插件 | 第一阶段先 Web mapping 摘除 + 超时等待，后续增强精确计数 |
| classloader 泄漏难以自动判定 | 长期运行内存泄漏 | 先做可观测统计和弱引用检测，不把 GC 结果作为唯一判断 |
| 回滚遇到 schema 不兼容 | 旧版本无法启动 | 有数据库变更的插件必须走 expand/contract 和预检 |
| 包目录切换非原子 | 激活过程中仓库看到半成品 | staged 完整校验后再移动到 active，失败恢复 backup |
| 健康检查误判 | 错误回滚或错误放行 | 支持默认检查 + 插件自定义检查 + 超时/重试配置 |

## 7. 未决问题处理建议

### Q1：部署记录存在哪里

建议：第一阶段使用本地文件 JSON 或轻量持久化目录，随宿主部署目录保存；后续再考虑数据库或管理面存储。

### Q2：是否改造现有 `reloadPlugin`

建议：不改语义。保留 `reloadPlugin` 作为低层操作，新建 `PluginDeploymentService.replace(...)` 作为安全热替换入口。

### Q3：是否强制所有插件提供 health probe

建议：不强制。没有自定义 probe 时使用默认检查；生产插件推荐实现。

### Q4：是否第一阶段就做零中断

建议：不做。先实现可控短暂停机、自动回滚和可观测，等状态机和清理验证稳定后再评估零中断能力。

### Q5：staged/backup/failed 是否立即改变现有插件仓库

建议：先由部署服务内部管理 staged/backup/failed，不立即改变仓库对外约定；稳定后再沉淀为标准目录。

## 8. 状态追踪

- 计划开始日期：2026-06-11
- 当前状态：M7 已完成，第一阶段收口
- 负责人：Codex
- 阻塞项：无
