# 跨插件 JPA 事务改进实施规划

## 1. 目标与范围

本规划用于跟踪跨插件 JPA 事务能力从“最小可用”走向“可长期维护”的增强过程。

范围包括：

- 插件级 JPA 绑定配置。
- shared JPA 的本地 BeanDefinition 桥接。
- domain descriptor 与 ready 状态。
- entity/model 可见性验证。
- 多 domain Repository 绑定规范。
- 事务代理边界示例和测试。
- 运行时 smoke 与失败注入验收。

不包括：

- JTA/XA。
- 跨数据源原子事务。
- 插件热替换部署设计和实现。
- 复杂 sample 的完整实现；复杂 sample 有独立规划文档。

## 2. 里程碑

| 里程碑 | 目标 | 交付物 | 通过条件 |
| --- | --- | --- | --- |
| M1 方案冻结 | 锁定改进方向和兼容策略 | 改进方案文档、本规划 | 评审通过，无阻塞未决问题 |
| M2 Shared JPA bridge | consumer 插件本地 BeanFactory 可识别 platform 导出的 EMF/TX | registrar 修复、单元测试、复杂 sample 复验 | Spring Data Repository registrar 可启动 |
| M3 插件级绑定 | 支持按 plugin-id 解析 JPA 绑定 | 配置属性、解析器、单元测试 | 旧配置兼容，新配置生效 |
| M4 Domain Descriptor | provider 导出 domain 元信息和 ready 状态 | descriptor 类型、导出/回滚逻辑、测试 | consumer 可基于 descriptor 诊断 |
| M5 Entity 可见性验证 | provider 对 entity 包做启动期校验 | 验证逻辑、错误码、测试 | 配置错误能被定位 |
| M6 事务边界与多 domain 验收 | 补齐事务代理、多 domain、失败隔离测试 | 测试用例、示例说明 | 关键失败路径可复查 |
| M7 文档与收口 | 更新开发指南和验收记录 | 中文/英文文档、验收文档 | 文档、实现、验收一致 |

## 3. 任务拆解

### M1 方案冻结

- [x] 梳理当前跨插件事务能力现状。
- [x] 明确保留 provider + `SHARED` consumer 主架构。
- [x] 明确不支持跨 domain 原子事务。
- [x] 明确热替换部署不纳入本规划。
- [x] 评审是否接受插件级 JPA 绑定配置结构。
- [x] 评审 descriptor 是否作为内部类型还是公共 API 类型。

评审结论：

- 接受 `pf4boot.plugin.jpa.plugins.<plugin-id>` 作为插件级绑定配置，优先级高于旧的全局 `mode/domain-id`。
- `JpaDomainDescriptor` 第一版作为 JPA 集成内部能力处理，先不提升到 `pf4boot-api` 公共 API；如果第三方插件需要编译期依赖，再单独评估 API 稳定性。

### M2 Shared JPA bridge

- [x] 修复 shared JPA BeanDefinition 可见性，确保 Spring Data JPA repository registrar 不再因 platform 导出 Bean 缺少 BeanDefinition 失败。
- [x] 在 consumer 插件本地 BeanFactory 注册 `domain.{id}.entityManagerFactory` 和 `domain.{id}.transactionManager` 的 BeanDefinition。
- [x] BeanDefinition 实例解析仍委托 parent/platform BeanFactory，避免复制真实 EMF/TX 实例。
- [x] shared bridge 读取当前插件上下文中的 `mode/domain-id`，并保留旧配置 fallback。
- [x] 增加测试：parent/platform 已导出 EMF/TX 时，插件本地 BeanFactory 可通过 `containsBeanDefinition` 和 `getBean` 访问。
- [x] 增加测试：动态共享 Bean 在平台 BeanFactory 中同时具备 singleton 与 BeanDefinition，供 Spring Data JPA 反查。
- [x] 复验复杂 sample，service/workflow Repository 能启动。

实现说明：

- `Pf4bootPluginManagerImpl.registerBeanToContext()` 在动态共享 Bean 注册时同步注册 `RootBeanDefinition`，解决 Spring Data JPA 递归扫描 parent BeanFactory 后调用 `getBeanDefinition()` 失败的问题。
- `SharedJpaAutoConfiguration` 只保留 `@Import(SharedJpaBeanDefinitionRegistrar.class)`，避免同一 registrar 同时通过 import 和静态 `@Bean` 双路径注册。

### M3 插件级绑定

- [x] 扩展 `Pf4bootJpaProperties`，增加 `plugins.<plugin-id>` 绑定配置。
- [x] 新增绑定解析器，按当前 plugin-id 优先解析。
- [x] 保持现有 `mode/domain-id` 作为 fallback。
- [x] `LOCAL` 默认行为不变。
- [x] 缺失或冲突配置输出 `PJF-006`。
- [x] 增加单元测试：旧配置、插件级配置、fallback、错误配置。

实现说明：

- `JpaPluginBindingResolver` 统一解析当前插件最终 JPA 绑定：插件级配置优先，其次回退旧配置，最后默认 `LOCAL`。
- `SharedJpaBeanDefinitionRegistrar` 使用同一解析结果注册 shared EMF/TM 本地占位 BeanDefinition，避免插件级 `SHARED` 被旧的全局条件误判。
- 本地 `EntityManagerFactory`/`TransactionManager` Bean 改为通过 `LocalJpaModeCondition` 判断，仅在最终绑定不是 `SHARED` 时创建。
- `PluginJPAStarter.afterPropertiesSet()` 使用最终绑定校验 shared domain，并在插件级 shared 缺少 `domain-id` 时输出 `PJF-006`。
- 单元测试覆盖旧配置、插件级配置覆盖全局 LOCAL、fallback、缺失 domain-id。

建议配置：

```yaml
pf4boot:
  plugin:
    jpa:
      plugins:
        plugin-user-book-service:
          mode: SHARED
          domain-id: demo
        plugin-workflow:
          mode: SHARED
          domain-id: demo
```

### M4 Domain Descriptor

- [x] 定义 `JpaDomainDescriptor`。
- [x] provider 在全部 `domain.{id}.*` Bean 导出成功后再导出 descriptor。
- [x] provider 导出失败时回滚已导出的 Bean 和 descriptor。
- [x] consumer 在 `SHARED` 模式优先读取 descriptor。
- [x] descriptor 缺失或 `ready=false` 输出 `PJF-007`。
- [x] 增加测试：正常导出、部分失败回滚、consumer 诊断。

建议字段：

```text
domainId
providerPluginId
entityPackages
dataSourceBeanName
entityManagerFactoryBeanName
transactionManagerBeanName
ready
createdAt
```

实现说明：
- `JpaDomainDescriptor` 位于 `pf4boot-jpa` 的 `net.xdob.pf4boot.jpa.domain` 包，作为 JPA 集成内部元信息类型，暂不提升到 `pf4boot-api`。
- `pf4boot-jpa-domain-starter` 在 DataSource、EntityManagerFactory、TransactionManager 全部导出成功后，再导出 `domain.{id}.descriptor`；如果 descriptor 或前置 Bean 导出失败，按反向顺序注销已经导出的 Bean。
- `pf4boot-jpa-starter` 的 `SHARED` consumer 启动时先读取 descriptor，并校验 `ready=true` 以及 descriptor 中的 EMF/TM Bean 名称与当前绑定一致；descriptor 缺失、未 ready 或绑定不一致时输出 `PJF-007`。
- 默认 descriptor 名称为 `domain.{id}.descriptor`；provider 可通过 `pf4boot.plugin.jpa.domain.descriptor-name` 覆盖，consumer 可通过全局或插件级 `descriptor-ref` 指向同一 descriptor。
- 已覆盖 provider 正常导出、descriptor 导出失败回滚、consumer descriptor 缺失和未 ready 诊断测试。

### M5 Entity 可见性验证

- [x] provider 校验 `entity-packages` 非空。
- [x] provider 尝试解析包内至少一个 `@Entity`。
- [x] 第一阶段可使用 warning，确认稳定后切为 fail-fast。
- [x] 无 managed entity 时输出 `PJF-008`。
- [x] 将“entity 来自 model 模块”写入开发指南。
- [x] 增加测试：空包、不可见类、正常 model jar。

实现说明：
- `pf4boot-jpa-domain-starter` 在创建 `LocalContainerEntityManagerFactoryBean` 前执行 entity 包可见性校验。
- `entity-packages` 为空继续按 `PJF-005` fail-fast；包在 provider classpath 不可见或扫描异常时按 `PJF-008` fail-fast。
- 包可见但未扫描到 `@Entity` 时按第一阶段策略输出 `PJF-008` warning 并继续启动，后续可基于 sample 和生产反馈评估是否切为 fail-fast。
- 开发指南和 JPA 集成文档已明确 entity 来自 model 模块，provider 保持职责单一，consumer Repository 显式绑定共享 EMF/TM。

### M6 事务边界与多 domain 验收

- [x] 增加事务代理边界测试：同类自调用反例、独立 bean 正例。
- [x] 增加多 domain Repository 绑定测试。
- [x] 增加 provider 缺失/失败隔离测试。
- [x] 增加强制异常回滚测试。
- [x] 增加插件包内容检查：provider 不包含 consumer Repository。
- [x] 增加运行时 smoke 流程说明。

实现说明：
- `TransactionProxyBoundaryTest` 覆盖同类自调用反例和独立 bean `REQUIRES_NEW` 正例。
- `PluginJPAStarterTest.pluginLevelBindingRegistersAdditionalSharedDomains` 覆盖一个 consumer 绑定主 domain 与 `additional-domains` 的 EMF/TM BeanDefinition 注册和 descriptor 校验。
- `PluginJPAStarterTest.providerMissingFailureDoesNotAffectUnrelatedNonJpaPluginContext` 覆盖 provider 缺失时 shared consumer 失败、无关非 JPA 插件上下文仍可启动。
- 强制异常回滚、provider 包边界和运行时 HTTP smoke 由复杂 sample 验收记录覆盖；跨 domain 原子事务继续明确不支持。

### M7 文档与收口

- [x] 更新 `cross-plugin-jpa-transaction-capability.md`。
- [x] 更新 `cross-plugin-jpa-transaction-capability-acceptance.md`。
- [x] 更新 `jpa-integration.md`。
- [x] 更新 `plugin-developer-guide.md`。
- [x] 同步英文翻译。
- [x] 补齐最终验收记录。

最终验收记录：
- M2-M6 的代码、测试、复杂 sample 打包验证和文档均已落入对应提交。
- 最终命令级验证：`.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-core:test :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`。
- 验收清单已同步到 `cross-plugin-jpa-transaction-capability-acceptance.md` 及英文翻译。
- 剩余明确非目标：JTA/XA、跨数据源/跨 domain 原子事务、热替换部署规划。

## 4. 验收清单

| 编号 | 验收项 | 通过标准 |
| --- | --- | --- |
| AC-01 | 旧配置兼容 | 仅配置 `pf4boot.plugin.jpa.mode/domain-id` 时行为不变 |
| AC-02 | 插件级绑定生效 | 不同 plugin-id 可解析到不同 domain 配置 |
| AC-03 | descriptor 正常导出 | provider 成功后可读取 `domain.{id}.descriptor` |
| AC-04 | descriptor 失败回滚 | provider 部分导出失败时无残留 descriptor |
| AC-05 | entity 可见性可诊断 | entity 包错误时输出明确错误码和包名 |
| AC-06 | 多 domain Repository 隔离 | 不同 Repository 包绑定不同 EMF/TM 且不互相污染 |
| AC-07 | 事务代理边界有效 | `REQUIRES_NEW` 独立 bean 场景按预期提交/回滚 |
| AC-08 | provider 失败隔离 | 依赖链失败，无关插件仍可启动 |
| AC-09 | 无 JPA 插件零影响 | 未启用 JPA 的插件不需要配置 domain |
| AC-10 | 文档同步 | 中文设计、英文翻译、开发指南和验收记录一致 |
| AC-11 | shared JPA bridge 可被 Spring Data 识别 | consumer 插件本地 BeanFactory 中存在 EMF/TX BeanDefinition，Repository registrar 不再报 `NoSuchBeanDefinitionException` |

## 5. 推荐验证命令

M2/M3/M4 阶段建议：

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test
```

M5 阶段建议：

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test `
  :pf4boot-jpa-domain-starter:test `
  :pf4boot-core:test --tests net.xdob.pf4boot.Pf4bootPluginManagerLifecycleTest
```

涉及 sample 时建议：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

运行态验证追加 sample 专属 HTTP smoke。

## 6. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 插件级配置与旧配置冲突 | consumer 解析结果不符合预期 | 明确解析优先级并输出诊断日志 |
| descriptor 类型放错模块 | 后续 API 兼容压力变大 | 第一版可放 starter 内部包；确认稳定后再考虑公共 API |
| entity 扫描校验不准确 | 误阻断 provider 启动 | 先 warning 后 fail-fast，配测试覆盖常见路径 |
| 多 domain 示例被误读为跨库事务 | 一致性预期错误 | 文档、示例和错误提示都声明不支持跨 domain 原子事务 |
| 测试需要真实插件生命周期 | 单元测试覆盖不足 | 结合 starter 单测、core 生命周期测试、sample smoke |

## 7. 未决问题处理建议

### Q1：`JpaDomainDescriptor` 放在哪个模块

建议：先放在 `pf4boot-jpa-domain-starter` 或 `pf4boot-jpa` 的内部能力包中。如果第三方插件需要编译期引用，再评估是否提升到公共 API。

### Q2：entity package 校验第一版是否 fail-fast

建议：第一版对“包不存在/类不可见” fail-fast；对“扫描不到 entity”先 warning，并在复杂 sample 验证稳定后改为 fail-fast。

### Q3：是否新增简化注解替代 `@EnableJpaRepositories`

建议：暂不新增。先用显式 Spring Data 配置保持透明，等复杂 sample 稳定后再评估是否需要 `@EnablePf4bootJpaRepositories`。

### Q4：插件级配置的 plugin-id 来源

建议：优先使用当前插件的 PF4J `pluginId`。如果 starter 初始化阶段获取不到，则通过已存在的 `pf4j.plugin` Bean 或插件上下文属性注入。

## 8. 状态追踪

- 计划开始日期：2026-06-11
- 当前状态：M7 文档与收口已完成；跨插件 JPA 事务改进方案本轮收口
- 负责人：Codex
- 阻塞项：无；entity package “无 @Entity” 场景第一阶段保持 warning，是否切为 fail-fast 留待后续生产反馈评估
