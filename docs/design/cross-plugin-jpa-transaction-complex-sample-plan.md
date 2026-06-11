# 跨插件 JPA 复杂示例实施规划

## 1. 目标与范围

本规划用于追踪复杂 JPA 示例从当前过渡状态迁移到独立多模块 sample 的过程。

范围包括：

- 新增独立复杂 sample 的模块结构。
- 从数据源能力插件中移出实体模型。
- 补齐跨插件服务编排、事务边界和失败场景演示。
- 更新中文设计文档、英文翻译、sample README 和验收记录。

不包括：

- 跨数据源原子事务。
- 插件热替换部署能力实现。
- 对 `pf4boot-jpa-domain-starter` 的大规模重构。

## 2. 里程碑

| 里程碑 | 目标 | 交付物 | 通过条件 |
| --- | --- | --- | --- |
| M1 设计冻结 | 锁定 sample 拆分边界 | 复杂示例设计文档和本实施规划 | 职责、依赖、验证命令明确 |
| M2 sample 骨架 | 建立独立多模块示例 | `samples/cross-plugin-jpa/*` 模块 | 只含空骨架即可编译 |
| M3 model/provider 拆分 | 实体从数据源插件移出 | `model-*` 与 `plugin-demo-jpa-domain` | provider 不定义 entity/repository/service |
| M4 service/workflow 落地 | 补齐复杂业务演示 | service 插件、workflow 插件、controller | 正常路径和失败路径可演示 |
| M5 验收与文档收口 | 形成可复查记录 | sample README、验收文档、命令记录 | 编译、打包、HTTP smoke 通过；剩余隔离验收有独立后续项 |

## 3. 任务拆解

### M1 设计冻结

- [x] 明确 `domain-model`、provider、service、workflow 的职责边界。
- [x] 明确 entity 不放在数据源能力插件里。
- [x] 明确跨插件访问走导出 service，不直接注入其它插件 Repository。
- [x] 明确 `REQUIRES_NEW` 必须通过独立 bean 触发。
- [x] 决定 sample 是否接入根 `settings.gradle`。
- [x] 决定 model jar 的运行时提供方式。

### M2 sample 骨架

- [x] 新建 `samples/cross-plugin-jpa` 目录。
- [x] 新建 `demo-host`。
- [x] 新建 `model-user-book`。
- [x] 新建 `model-workflow-audit`。
- [x] 新建 `plugin-demo-jpa-domain`。
- [x] 新建 `plugin-user-book-service`。
- [x] 新建 `plugin-workflow`。
- [x] 新建 `app-run` 运行时打包项目。
- [x] 补齐 sample 级 README 和最小启动说明。

### M3 model/provider 拆分

- [x] 将 `UserAccount`、`Book`、`WorkflowAudit` 等 entity 放入 model 模块。
- [x] provider 插件仅依赖 model 模块并配置 `entity-packages`。
- [x] provider 插件仅 bundle model jar；框架/JPA starter 当前由 host classpath 提供。
- [x] provider 插件不包含 Repository、Controller、业务 service。
- [x] 校验 provider 插件包中不出现业务 Repository。

### M4 service/workflow 落地

- [x] `plugin-user-book-service` 定义 Repository 包和 `UserBookService`。
- [x] `plugin-user-book-service` 使用 `@EnableJpaRepositories` 显式绑定 `domain.demo.*`。
- [x] `plugin-workflow` 定义 audit Repository。
- [x] `plugin-workflow` 通过 `UserBookService` 编排用户/图书业务。
- [x] `plugin-workflow` 用独立 writer bean 演示 `REQUIRES_NEW`。
- [x] `plugin-workflow` 提供正常、失败、查询三类 HTTP 演示接口。

### M5 验收与文档收口

- [x] 执行 sample compile 验证。
- [x] 执行 sample 插件 pf4boot 打包验证。
- [x] 启动 sample host 并执行 HTTP smoke。
- [x] 增加 sample 运行时打包项目，产出可运行目录和 zip 分发包。
- [ ] 验证 provider 缺失时依赖插件失败、无关插件不受影响；当前 sample 没有无关插件对照，后续补 no-jpa/unrelated 插件或生命周期测试。
- [x] 验证插件包内容符合职责边界。
- [x] 更新中文和英文设计文档。
- [x] 形成验收记录。

## 4. 建议目录与任务命名

如果接入根工程，建议 Gradle path 使用：

```text
:samples:cross-plugin-jpa:demo-host
:samples:cross-plugin-jpa:model-user-book
:samples:cross-plugin-jpa:model-workflow-audit
:samples:cross-plugin-jpa:plugin-demo-jpa-domain
:samples:cross-plugin-jpa:plugin-user-book-service
:samples:cross-plugin-jpa:plugin-workflow
:samples:cross-plugin-jpa:app-run
```

如果保持独立工程，则 sample 内部自行维护 `settings.gradle`，根工程只在文档中引用。

## 5. 验收清单

| 编号 | 验收项 | 通过标准 |
| --- | --- | --- |
| AC-01 | provider 职责单一 | provider 插件源码中无 `@Entity`、Repository、Controller、业务 service |
| AC-02 | entity 来源清晰 | 所有 entity 来自 `model-*` 模块 |
| AC-03 | 共享事务可用 | service/workflow 插件均绑定同一个 `domain.demo.transactionManager` |
| AC-04 | Repository 不跨插件直连 | workflow 不注入 service 插件内部 Repository |
| AC-05 | 事务代理有效 | `REQUIRES_NEW` 放在独立 bean 并由外部 bean 调用 |
| AC-06 | 插件包边界正确 | provider 包含 model class 或可见依赖，不包含 consumer Repository |
| AC-07 | 失败场景可演示 | workflow 强制异常时 user/book 随主事务回滚，`REQUIRES_NEW` audit 独立提交 |
| AC-08 | 依赖失败隔离 | provider 失败只影响依赖链，不影响无关插件；当前 sample 暂无无关插件对照 |
| AC-09 | 运行时包可生成 | `app-run` 能组装 host 依赖、配置、脚本和插件 zip，并产出分发 zip |

## 6. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| model jar 运行时不可见 | provider 创建 EMF 失败 | 先锁定 model jar 由 provider 插件打包或 host platform 提供 |
| sample 接入根构建导致主工程变慢 | 开发反馈变慢 | sample 可先独立构建，成熟后再决定是否接入根构建 |
| 复杂示例污染根工程 | 根工程维护成本升高 | 根级旧示例已删除，复杂业务放 `samples/cross-plugin-jpa` |
| `REQUIRES_NEW` 示例被误读为推荐业务模式 | 团队误用事务边界 | 文档明确它是演示事务代理边界，不是默认业务推荐 |
| 多 domain 场景被误认为已支持跨库事务 | 一致性预期错误 | 文档继续声明不支持跨 domain 原子事务 |

## 7. 未决问题处理建议

### Q1：sample 是否接入根 `settings.gradle`

结论：接入根 `settings.gradle`，便于复用当前 Gradle 插件、统一编译和打包验证；发布配置中排除 sample 模块，避免污染正式发布物。

### Q2：model jar 由谁提供

结论：复杂 sample 第一版由 provider 插件直接携带 `model-*` jar；框架/JPA starter 当前由 host classpath 提供，避免 Spring Boot、PF4J、`pf4boot-api` 在插件私有 classloader 中形成类型冲突。

### Q3：当前根 demo 的过渡复杂代码怎么处理

结论：复杂 sample 落地后删除根级旧示例项目，后续示例统一放在 `samples/cross-plugin-jpa` 或新的 `samples/*` 模块中。

### Q4：是否把热替换部署一起做

建议：不要混入本规划。热替换涉及依赖顺序、流量静默、事务排空、健康检查、回滚和 classloader 清理，应单独立设计和验收。

## 8. 推荐验证命令

第一阶段只做编译和打包：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:plugin-demo-jpa-domain:compileJava `
  :samples:cross-plugin-jpa:plugin-user-book-service:compileJava `
  :samples:cross-plugin-jpa:plugin-workflow:compileJava
```

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:plugin-demo-jpa-domain:pf4boot `
  :samples:cross-plugin-jpa:plugin-user-book-service:pf4boot `
  :samples:cross-plugin-jpa:plugin-workflow:pf4boot
```

运行时打包：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:sampleDistZip
```

第二阶段补运行时 smoke：

```text
GET /api/sample/workflow/place
GET /api/sample/workflow/place?failAfterAudit=true
GET /api/sample/workflow/summary
GET /api/sample/workflow/audit
```

## 9. 状态追踪

- 计划开始日期：2026-06-11
- 当前状态：M5 验收阶段，编译/打包/包边界/HTTP smoke 已通过；provider 失败隔离需补无关插件或生命周期测试
- 负责人：Codex
- 阻塞项：无；剩余项为后续隔离验收增强
