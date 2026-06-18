# pf4boot 3.3 插件生态路线图

## 背景

`pf4boot` 3.2 已经完成 JPA 运行时刷新、热替换部署、HTTP 管理治理、资源清理诊断和 runtime smoke 的生产化闭环。下一阶段最有价值的工作不应继续扩大运行时能力面，而应降低插件开发、打包、验证、升级和管理接入成本，让第三方插件可以按稳定规则开发和交付。

当前根构建已使用 `net.xdob.pf4boot:pf4boot-plugin:1.7.0`。用户确认此前提出的 `pf4boot-plugin-next-requirements-zh.md` 已被实现，因此 3.3 规划以 `pf4boot-plugin 1.7.0` 作为辅助 Gradle 插件能力基线。

## 版本目标

3.3 版本纳入以下 6 项目标：

| 编号 | 目标 | 3.3 范围 |
| --- | --- | --- |
| E1 | 官方插件开发指南重写 | 先做，形成按插件类型组织的开发指南和迁移路径 |
| E2 | `pf4boot-plugin 1.7.0` 能力基线对齐 | 先做，明确框架文档、sample 和构建脚本如何使用 1.7.0 能力 |
| E3 | 官方模板/复杂 samples 梳理 | 先做，整理官方模板矩阵、sample 分层和验收命令 |
| E4 | 兼容矩阵和打包校验 | 纳入 3.3，总体规划先占位，详细设计后续展开 |
| E5 | 插件仓库/分发设计 | 纳入 3.3，总体规划先占位，详细设计后续展开 |
| E6 | 管理控制台 sample UI | 纳入 3.3，总体规划先占位，详细设计后续展开 |

## 非目标

- 不修改 `pf4boot-plugin` 外部仓库；本仓库只消费 `pf4boot-plugin 1.7.0`。
- 不在 core、starter 或 management starter 中内置管理控制台 UI。
- 不在 3.3 第一阶段实现跨数据源强事务、集群级滚动发布或远程插件市场。
- 不把 sample 模块发布到 Maven 仓库。
- 不改变 3.2 已冻结的 JPA reload、热替换部署和管理接口基础语义。

## 总体约束

- 所有文档以中文为准，英文翻译同步放在 `docs/design/en/`。
- 所有代码和文档仍保持 Java 8、UTF-8、现有 Gradle 多模块结构。
- 3.3 前三项优先落文档、模板和 sample 结构，不先改运行时核心。
- 涉及打包、插件依赖作用域和 sample 结构的变更必须同步更新 `plugin-loading-and-packaging.md` 和 `plugin-developer-guide.md`。
- 所有新模板和 sample 必须有最小可执行验证命令，不能只给静态代码片段。

## E1 官方插件开发指南重写

目标是把当前 `plugin-developer-guide.md` 从“能力集合说明”重写为“开发者任务路径”：

| 章节 | 内容 |
| --- | --- |
| 快速开始 | 最小服务插件从创建、打包到宿主加载 |
| 插件类型 | 普通服务、Web、JPA domain、JPA consumer、workflow、管理客户端 |
| Gradle 作用域 | `compileOnlyApi`、`bundle`、`plugin`、`platformApi` 的判断规则 |
| 插件描述符 | plugin id、version、provider、dependencies、requires 的填写规则 |
| 类加载边界 | 宿主 API、插件内部依赖、插件间 API 的可见性规则 |
| JPA 开发路径 | model/provider/consumer 分层、包扫描、事务管理器绑定 |
| 管理与运维 | package verification、trust manifest、部署、rollback、JPA reload |
| 故障排查 | 常见启动失败、依赖缺失、类冲突、JPA 绑定失败、打包污染 |

## E2 `pf4boot-plugin 1.7.0` 能力基线对齐

目标是让本仓库中的构建脚本、官方文档和 sample 都按 1.7.0 的能力表达插件工程，而不是混用旧写法或手工规约。

基线对齐必须覆盖：

| 能力面 | 对齐要求 |
| --- | --- |
| 插件工程声明 | 所有官方插件 sample 使用 `net.xdob.pf4boot-plugin` |
| 依赖作用域 | 官方文档必须解释 `compileOnlyApi`、`bundle`、`plugin` 的边界和反例 |
| 插件描述符 | 明确优先使用 Gradle DSL 还是 `plugin.properties`，以及二者并存时的规则 |
| 插件包产物 | 固化 zip/jar、`lib/`、descriptor、嵌套依赖和 checksum/trust 旁路文件的期望 |
| 模板生成 | 如果 1.7.0 已提供模板/校验任务，官方指南应优先引用；若能力缺口存在，记录到 E4/E5 |
| 示例验证 | sample 不能依赖未发布或外部仓库的私有任务；验证命令必须可在当前仓库执行 |

## E3 官方模板/复杂 samples 梳理

目标是把 sample 从“功能证明”整理成“模板来源和回归用例”。

推荐模板矩阵：

| 模板 | 来源 sample | 主要展示 |
| --- | --- | --- |
| `service-plugin` | `samples/cross-plugin-jpa:plugin-unrelated-service` | 无 JPA 依赖的普通服务插件 |
| `web-plugin` | 后续补充或从现有 Web 支持拆出 | Controller、静态资源、动态 mapping 清理 |
| `jpa-domain-plugin` | `plugin-demo-jpa-domain` | 单 domain provider，只导出 DataSource/EMF/TM/descriptor |
| `jpa-consumer-plugin` | `plugin-user-book-service` | Repository 包扫描、共享事务管理器 |
| `workflow-plugin` | `plugin-workflow` | 跨插件服务组合、事务回滚、audit |
| `management-client` | `samples/plugin-management-console` | 只消费 `/pf4boot/admin/**` 和 Actuator |

复杂 sample 梳理必须满足：

- `samples/cross-plugin-jpa` 继续作为 JPA domain、consumer、workflow、reload 和热替换主样例。
- `samples/saga-outbox` 继续作为跨数据源非强事务补偿样例。
- `samples/plugin-management-console` 继续保持 sample 或独立工具边界，不进入发布模块。
- 每个 sample 的 README 必须说明它对应的模板、运行命令、验证命令和不适合复制的 demo-only 代码。
- 构建产物、历史 jar、签名文件和 runtime 输出不得作为模板内容被误读。

## E4 兼容矩阵和打包校验

3.3 纳入该目标，但前 3 项完成后再展开详细设计。

初始方向：

- 建立 `pf4boot`、`pf4boot-plugin`、Spring Boot、PF4J、JDK、插件包格式之间的兼容矩阵。
- 为官方 sample 增加打包校验，检查 host API 是否被误打入插件包。
- 为旧插件包提供 WARN 模式兼容检查和明确拒绝原因。

## E5 插件仓库/分发设计

3.3 纳入该目标，但前 3 项完成后再展开详细设计。

初始方向：

- 固化 offline-index 仓库格式、checksum/trust manifest、release request 和 cache 边界。
- 让 repository release 最终复用现有 `PluginDeploymentService` 的 plan/replace/rollback。
- 不在首版引入中心化远程市场服务。

## E6 管理控制台 sample UI

3.3 纳入该目标，但前 3 项完成后再展开详细设计。

初始方向：

- UI 作为 sample 或独立多模块项目存在。
- 只调用 `/pf4boot/admin/**` 和只读 Actuator。
- 覆盖插件列表、部署计划、replace、rollback、JPA reload、审计和错误展示。

## 分阶段实施计划

| 阶段 | 范围 | 输出 | 验收 |
| --- | --- | --- | --- |
| P1 | E1-E3 设计冻结 | 3.3 路线图、开发体验设计、实施规划与英文翻译 | 文档链接进入 `docs/design/README.md` |
| P2 | E1 开发指南重写 | 重写 `plugin-developer-guide.md` 和英文翻译 | 指南覆盖 6 类插件路径和故障排查 |
| P3 | E2 1.7.0 基线对齐 | 梳理构建脚本、sample 写法和文档术语 | `rg` 检查无旧术语误导，sample 构建命令通过 |
| P4 | E3 模板/sample 整理 | sample README、模板矩阵、验证命令 | `cross-plugin-jpa`、`saga-outbox`、console 文档闭环 |
| P5 | E4 兼容矩阵和打包校验设计 | 详细设计和计划 | 明确校验任务、错误码和兼容矩阵格式 |
| P6 | E5 插件仓库/分发设计 | 详细设计和计划 | 明确 offline-index、release request 和 cache 规则 |
| P7 | E6 管理控制台 sample UI 设计 | 详细设计和计划 | 明确 UI 边界、API 映射和验收 |

## 完成定义

3.3 第一阶段完成需满足：

1. E1-E3 的设计和规划文档已完成并同步英文翻译。
2. 现有开发指南的重写范围、目标结构和验收要求明确。
3. `pf4boot-plugin 1.7.0` 在本仓库中的消费边界明确。
4. 官方模板矩阵和 sample 职责边界明确。
5. E4-E6 已进入 3.3 路线图，但不会阻塞前 3 项实施。

