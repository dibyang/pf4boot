# 生产化完善路线图

## 背景

`pf4boot` 已经具备 PF4J 插件加载、Spring Boot 插件上下文、共享 Bean、动态 MVC、插件 JPA starter、插件打包和 sample 验证等核心能力。近期设计文档也已经覆盖生命周期清理、starter 边界、运行时安全和测试地基。

下一阶段的主要问题不是继续堆叠功能，而是把框架从“功能可用”推进到“生产可治理”：回归验证、运行时观测、插件升级治理、JPA 能力边界和用户文档需要形成稳定闭环。

## 目标

- 建立覆盖生命周期、Web、JPA、打包和 sample smoke 的持续回归验证。
- 暴露面向运维的插件状态、资源计数、错误和生命周期指标。
- 明确 JPA 动态能力边界，并为 schema 管理和运行时刷新提供后续设计入口。
- 补齐插件包完整性、升级、回滚、兼容性检查和发布治理能力。
- 让插件开发者能按文档完成依赖声明、Web/JPA 插件开发、打包和排障。

## 非目标

- 不在本路线图中直接实现运行时 JPA metamodel 热刷新。
- 不引入高于 Java 8 或 Spring Boot 2.7.x 的运行时基线。
- 不把 sample 应用改造成完整插件市场或控制台产品。
- 不默认改变插件生命周期顺序、类加载优先级或现有插件包格式；破坏性调整必须分阶段迁移。

## 现状/已有流程

| 能力 | 当前状态 | 主要依据 |
| --- | --- | --- |
| 插件生命周期 | 已有加载、启动、停止、重载、清理设计和实现 | `docs/design/plugin-lifecycle.md`、`pf4boot-core` |
| 共享 Bean 与动态注册 | 已有 root、platform、application 分层和冲突策略 | `docs/design/context-and-bean-sharing.md`、`DynamicBeanConflictPolicy` |
| Web 集成 | 已有动态 MVC mapping、拦截器和静态资源处理 | `docs/design/web-integration.md`、`pf4boot-web-starter` |
| JPA 集成 | 启动时扫描实体；运行时 metadata sync 明确不支持 | `docs/design/jpa-integration.md`、`DefaultDynamicMetadata` |
| 测试 | 已有少量 core、web-starter、jpa-starter 测试；仍缺端到端和打包验证 | `pf4boot-core/src/test`、`pf4boot-web-starter/src/test`、`pf4boot-jpa-starter/src/test` |
| 构建策略 | 根构建未发现全局禁用 test 的逻辑；部分历史文档仍有旧描述 | `build.gradle`、`docs/design/verification-foundation.md` |

## 核心约束

- 继续保持 `jdkVersion=1.8`，不得使用 Java 9+ API 或语法。
- 公共 API、注解和共享模型放在 `pf4boot-api`。
- PF4J runtime、插件管理器、仓库、加载器、生命周期、classloader 和调度放在 `pf4boot-core`。
- Spring Boot 自动配置按能力归属放在 `pf4boot-starter`、`pf4boot-web-starter`、`pf4boot-jpa-starter`。
- Web 能力留在 `pf4boot-web-*`，JPA/Hibernate 能力留在 `pf4boot-jpa*`。
- 插件模块继续使用 `compileOnlyApi` 表示宿主提供的 API，用 `bundle` 表示插件自带依赖。
- 所有破坏性配置默认值调整必须提供迁移说明、兼容开关和至少一个小版本的过渡路径。

## 接口设计

### 运维观测

| 接口 | 所属模块 | 输出内容 |
| --- | --- | --- |
| `PluginRuntimeSnapshot` | `pf4boot-api` | 插件 ID、版本、状态、启动耗时、最近错误摘要、资源计数 |
| `PluginRuntimeInspector` | `pf4boot-api` | 查询单插件和全量插件运行时快照 |
| Actuator endpoint | 新增独立 `pf4boot-actuator` 模块 | `/actuator/pf4boot` 读取快照，不提供变更操作 |
| Micrometer metrics | 可选条件启用 | 插件数量、启动失败次数、操作耗时、动态资源残留计数 |

### 插件治理

| 能力 | 设计方向 |
| --- | --- |
| 完整性校验 | 第一阶段使用 SHA-256 checksum 和可插拔 verifier，加载前校验 |
| 签名校验 | 签名格式、信任根和证书轮换后续单独设计；第一阶段不绑定 JAR 签名或自定义签名格式 |
| 兼容性检查 | 在加载前检查 PF4Boot 版本、Java 版本、宿主能力、插件依赖范围 |
| 回滚 | reload/upgrade 失败时保留上一版本插件包和状态，允许回退 |
| 灰度 | 支持按配置只启用指定插件、指定版本或指定插件组 |

## 数据结构

### `PluginRuntimeSnapshot`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pluginId` | `String` | 插件 ID |
| `version` | `String` | 当前加载版本 |
| `state` | `String` | PF4J 插件状态 |
| `enabled` | `boolean` | 是否启用 |
| `startedAt` | `Long` | 最近启动时间，毫秒时间戳 |
| `lastOperation` | `String` | 最近一次生命周期操作 |
| `lastError` | `String` | 最近错误摘要，不包含敏感堆栈 |
| `resourceCounts` | `Map<String, Integer>` | 动态 bean、MVC mapping、interceptor、scheduled task 等资源计数 |

### `PluginPackageVerification`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pluginId` | `String` | 插件 ID |
| `version` | `String` | 插件版本 |
| `checksum` | `String` | 插件包摘要 |
| `signatureStatus` | `String` | `NOT_CONFIGURED`、`VALID`、`INVALID` |
| `compatibilityStatus` | `String` | `PASS`、`WARN`、`FAIL` |
| `messages` | `List<String>` | 校验说明和失败原因 |

## 状态机

| 状态 | 说明 | 允许迁移 |
| --- | --- | --- |
| `DISCOVERED` | 插件包已发现但未加载 | `VERIFYING`、`REJECTED` |
| `VERIFYING` | 正在做完整性、签名和兼容性检查 | `VERIFIED`、`REJECTED` |
| `VERIFIED` | 校验通过，可交给 PF4J 加载 | `LOADED`、`REJECTED` |
| `LOADED` | 已加载但未启动 | `STARTING`、`DISABLED`、`UNLOADING` |
| `STARTING` | 正在启动插件上下文和共享资源 | `STARTED`、`FAILED` |
| `STARTED` | 已启动并对外提供能力 | `STOPPING`、`RELOADING` |
| `STOPPING` | 正在停止并释放动态资源 | `STOPPED`、`FAILED` |
| `STOPPED` | 已停止但仍可再次启动 | `STARTING`、`UNLOADING` |
| `RELOADING` | 正在执行停止、卸载、重新加载和启动 | `STARTED`、`FAILED_ROLLBACK_REQUIRED` |
| `FAILED_ROLLBACK_REQUIRED` | 升级或重载失败，需要回滚或人工处理 | `ROLLING_BACK`、`REJECTED` |
| `ROLLING_BACK` | 正在恢复上一版本包和状态 | `STARTED`、`STOPPED`、`FAILED` |
| `REJECTED` | 校验失败或策略拒绝 | 终态，除非外部替换插件包 |

## 时序流程

### 插件升级/重载

1. 发现新插件包或用户触发 reload。
2. 执行完整性、签名和兼容性检查。
3. 复制当前版本包和状态为回滚点。
4. 停止当前插件并释放动态资源。
5. 卸载旧 classloader，加载新插件包。
6. 启动新插件并校验资源计数、状态和 smoke check。
7. 任一步失败时进入回滚流程，恢复上一版本或停留在可诊断失败态。

## 异常处理

| 异常类别 | 对外行为 | 日志/观测 | 补偿 |
| --- | --- | --- | --- |
| 包校验失败 | 拒绝加载或升级 | 记录 checksum/signature/compatibility 原因 | 保留旧版本 |
| 启动失败 | 插件进入失败态或回滚 | 记录启动阶段、最近异常摘要、资源计数 | 调用清理链路 |
| 停止失败 | 返回失败并保留诊断信息 | 记录未释放资源计数 | 允许重试 stop/release |
| 回滚失败 | 进入人工处理态 | 高优先级日志和指标 | 保留失败现场 |

## 幂等性

- `startPlugin` 对已启动插件保持幂等，返回当前状态而不是重复注册资源。
- `stopPlugin` 对已停止或未加载插件保持幂等，不能抛出会阻断批量停止的非预期异常。
- `reloadPlugin` 以插件 ID 和目标版本作为操作去重 key；同一插件的重载必须被生命周期锁串行化。
- 资源注册记录以插件 ID、scope、group、beanName 或 mapping key 标识，停止时按记录释放，避免重新扫描半关闭上下文。
- metrics 可以重复记录尝试次数，但资源状态不能因为重试而重复注册。

## 回滚策略

- 插件升级前保留上一版本 zip 和解析后的 descriptor 摘要，初期只使用内存状态和文件级回滚点。
- 新版本启动失败时，默认尝试恢复上一版本；若上一版本恢复失败，插件保持 stopped/failed 状态并暴露诊断。
- 对公共 API 的破坏性调整必须先新增 API，再弃用旧 API，最后在下一个破坏性窗口移除。
- 文档与构建策略不一致时，先修正文档或补验证，不把历史描述继续作为事实传播。

## 兼容性

- 新增 SPI 默认 no-op，不能强制应用引入 Actuator 或 Micrometer。
- Actuator 能力拆成独立 `pf4boot-actuator` 模块，并保持只读，不承载插件启停、重载等管理操作。
- metrics 能力应条件启用，避免非 Web 或轻量应用被动增加依赖。
- 插件包完整性校验初期应允许 `WARN` 模式，避免历史插件无法一次性迁移。
- JPA schema 管理由宿主或插件显式接入迁移工具，框架只说明边界和示例，不绑定 Flyway 或 Liquibase。
- JPA 运行时同步能力若未来实现，应作为独立设计，不改变当前 `DynamicMetadata.sync()` 明确失败的语义。

## 灰度/迁移

| 阶段 | 默认行为 | 迁移动作 | 退出条件 |
| --- | --- | --- | --- |
| P0 验证闭环 | 默认不启用校验，不改历史插件加载行为 | 补测试、校正文档和构建策略描述；插件包校验 SPI 已落地 | 核心路径有可重复验证 |
| P1 观测闭环 | 独立引入 `pf4boot-actuator` 后启用只读 endpoint | 运维接入状态和指标；只读插件快照 endpoint 已落地 | 能定位插件失败和资源残留 |
| P2 治理增强 | 校验先 WARN 后 ENFORCE | 插件包补 checksum/signature | 新插件包加载前可校验 |
| P3 JPA 边界 | 插件 JPA 默认 `ddl-auto=none` | 明确 schema 由宿主或插件迁移工具管理；默认 DDL 测试已落地 | 插件开发者不会误用自动改表默认值 |

## 测试方案

- `pf4boot-core`：生命周期并发、依赖链启停、失败清理、classloader 释放、共享 Bean 幂等。
- `pf4boot-web-starter`：动态 mapping/interceptor 注册、注销、重复注册和冲突处理。
- `pf4boot-jpa-starter`：JPA 默认关闭、包扫描边界、DDL 默认策略、运行时 sync 失败语义。
- `samples/cross-plugin-jpa`：插件依赖、bundle/compileOnlyApi 边界、Web/JPA sample smoke。
- 文档一致性：核验 `docs/design` 中关于测试策略、JPA 边界、插件打包和观测边界的描述与代码一致。

最小验证命令按阶段选择：

- `.\gradlew.bat :pf4boot-core:test`
- `.\gradlew.bat :pf4boot-web-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

## 风险点

| 风险 | 严重度 | 发现方式 | 缓解 |
| --- | --- | --- | --- |
| 观测依赖污染轻量应用 | 中 | 依赖树检查 | 使用条件自动配置和可选模块 |
| 插件升级回滚不完整 | 高 | 故障注入测试 | 保留旧包、状态快照和失败态 |
| JPA 动态刷新误导使用者 | 高 | 文档和测试 | 继续保持 `sync()` 明确失败，单独设计增强 |
| 文档与代码再次漂移 | 中 | 文档一致性检查 | 变更时同步中英文设计和 README |

## 分阶段实施计划

### 阶段 1：验证闭环

- 清理历史文档中关于 test 任务的旧描述，确认当前 Gradle 策略。
- 补齐 core/web/jpa 的失败路径和并发测试。
- 为 `samples/cross-plugin-jpa` 增加插件包结构和 smoke 验证。
- 建立最小 CI 命令集，区分快速验证和完整验证。

### 阶段 2：观测与诊断

- 已定义 `PluginRuntimeSnapshot` 和 `PluginRuntimeInspector`。
- 已新增独立 `pf4boot-actuator` 模块，暴露只读 actuator endpoint：`pf4bootplugins`。
- 已保持默认 starter 不依赖 Actuator，宿主只有显式引入 `pf4boot-actuator` 时才注册观测能力。
- 已条件接入 Micrometer 指标：引入 `pf4boot-actuator` 后注册插件总数、已启动数、失败数 gauge。
- 已将最近错误、资源计数占位和启动耗时纳入插件状态查询。
- 验收口径：依赖现有 starter 的应用不会被动引入 Actuator/Micrometer；引入 `pf4boot-actuator` 后只能读取插件快照，不能通过观测入口执行启停、重载等变更操作。

### 阶段 3：JPA 能力治理

- 已保持当前启动时扫描和 `DynamicMetadata.sync()` 失败语义。
- 已补充 schema 管理边界：插件 JPA 默认 `ddl-auto=none`，插件或宿主显式配置迁移工具；文档只说明边界和示例，不绑定 Flyway 或 Liquibase。
- 已补测试锁定 `HibernateDefaultDdlAutoProvider` 默认 `none`，避免 embedded 数据库误走 `create-drop`。
- 单独设计运行时 JPA 刷新或 EntityManagerFactory 重建方案。
- 在 sample 中明确展示 JPA 插件的推荐配置。
- 验收口径：JPA 文档明确 `ddl-auto=none` 默认策略、schema 迁移责任边界和示例接入方式；框架依赖树不新增 Flyway/Liquibase 强依赖。

### 阶段 4：插件包治理和回滚

- 已落地插件包校验基础能力：新增 `PluginPackageVerifier` SPI，宿主可通过 Spring Bean 注入自定义校验器。
- 已落地默认 SHA-256 sidecar 校验器，支持 `DISABLED`、`WARN`、`ENFORCE` 三种模式；默认 `DISABLED` 保持兼容。
- 已落地加载前校验调用点：插件描述符解析后、ClassLoader 创建前执行校验，`ENFORCE` 失败时阻断插件加载。
- 已新增 `upgradePlugin(pluginId, newPluginPath, rollbackPluginPath)`，升级失败时尝试从上一版本路径重新加载并恢复原启动态。
- 已新增加载前系统版本兼容性检查，支持 `DISABLED`、`WARN`、`ENFORCE` 三种模式。
- 兼容性边界已覆盖系统版本检查、PF4J 依赖解析和自定义 verifier 扩展；签名格式和持久操作历史不纳入本轮落地。
- 插件依赖版本继续沿用 PF4J 依赖解析；宿主能力可通过自定义 `PluginPackageVerifier` 扩展。
- 验收口径：插件包缺失或校验失败时能输出明确诊断；checksum 可从 WARN 平滑切换到 ENFORCE；升级失败时能基于上一版本文件回滚；不引入持久化操作历史。

### 后续专题：插件签名

- 单独设计签名格式、信任根配置、证书轮换、撤销策略和离线校验流程。
- 评估 JAR 原生签名、外部 manifest 和框架自定义清单的兼容性、实现成本和运维成本。
- 本专题不阻塞阶段 4 的 checksum/verifier 落地。

### 阶段 5：开发者文档和示例完善

- 已编写插件开发指南，覆盖依赖作用域、包校验、只读观测、JPA 和升级回滚。
- 已更新 sample 示例配置，展示 `system-version`、包校验和兼容性校验开关。
- 已将中英文设计文档索引保持同步。
- 常见错误先通过明确异常消息和 actuator 快照暴露；错误码体系后续可作为独立文档增强。

## 决策建议 / 后续专题

- Actuator 能力拆成独立 `pf4boot-actuator` 模块，避免现有 starter 被动引入 Actuator/Micrometer 依赖。
- 观测接口保持只读，不承载插件启停、重载等管理操作。
- 插件包治理第一阶段采用 SHA-256 checksum 和可插拔 verifier；签名格式、信任根和证书轮换作为后续专题单独设计。
- 插件升级初期只保留内存状态和文件级回滚点，暂不引入持久化操作历史。
- JPA schema 管理由宿主或插件显式接入迁移工具；框架文档只说明边界和示例，不绑定 Flyway 或 Liquibase。
