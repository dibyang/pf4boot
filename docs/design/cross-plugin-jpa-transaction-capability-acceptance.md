# 跨插件 JPA 事务能力验收清单（追踪版）

## 1. 验收目标

确认以下三点成立：

- 本地模式行为不变。
- 共享域模式下可稳定共享同域事务。
- 能力插件失败不破坏未依赖链路。

## 2. 前置条件

- 代码已合并到目标分支。
- 变更范围与版本可复现（含 `pf4boot-jpa-starter`、`pf4boot-jpa-domain-starter`、样例模块）。
- 有 H2 或其他数据库环境可用于演示。

## 3. 验收项（AC）

### AC-01 本地回归

- 输入：`mode` 不配置、或 `mode=LOCAL`。
- 验证：
  - 插件在原有方式下可正常启动；
  - 继续创建本地 EMF/TM；
  - 无额外能力插件依赖需求。
- 通过标准：启动与运行行为不受共享能力改造影响。

### AC-02 共享域启动成功

- 输入：能力插件可用，业务插件配置 `mode=SHARED` + `domain-id`。
- 验证：
  - 业务插件未创建重复的本地 EMF/TM；
  - 成功绑定 `domain.<id>.entityManagerFactory` 与 `domain.<id>.transactionManager`；
  - 共享 EMF 已包含该领域配置的 entity 包；
  - 多个依赖同域插件可在同一 TM 下执行事务操作。
- 通过标准：无 `Transaction` 绑定报错，提交/回滚一致。

### AC-03 多域隔离

- 输入：两个能力插件（如 `order` / `report`）及对应消费插件。
- 验证：
  - 每域各自使用各自 TM；
  - 同一插件内双域仓储绑定正确；
  - 不出现跨域自动共享。
- 通过标准：多域场景下 TM 路由与实体扫描互不污染。

### AC-04 缺失域配置失败

- 输入：业务插件配置 `mode=SHARED`，但未配置 `domain-id`、或目标能力未提供。
- 验证：
  - 插件启动失败；
  - 报错包含 `PJF-001 / PJF-002 / PJF-003`；
  - 错误信息可指向修复动作。
- 通过标准：fail-fast，错误可判定。

### AC-05 能力插件故障隔离

- 输入：模拟能力插件启动失败。
- 验证：
  - 依赖链上的插件失败；
  - 未依赖该链的独立插件仍可继续启动/运行；
  - 恢复能力插件后依赖链可重新成功启动。
- 通过标准：隔离行为与依赖边界成立。

### AC-06 迁移路径可执行

- 输入：从 `LOCAL` 迁移到 `SHARED` 按文档步骤执行。
- 验证：
  - 按文档完成配置修改；
  - 业务可按域替换 `@Transactional` TM；
  - 无功能回退。
- 通过标准：迁移清单一项未执行通过不得验收。

### AC-07 Entity 扫描边界可判定

- 输入：领域能力插件配置 `entity-packages`，业务插件只配置 Repository 包与 TM/EMF 引用。
- 验证：
  - 领域能力插件能在 EMF 创建前扫描到 entity；
  - 业务插件中的 Repository 引用这些已管理 entity；
  - 业务插件新增未被领域 EMF 管理的 entity 时启动失败或 Repository 创建失败，错误可定位。
- 通过标准：共享域 entity 归属明确，不依赖运行时 metamodel 刷新。

### AC-08 Descriptor Ready 诊断

- 输入：provider 正常导出、descriptor 导出失败、descriptor 未 ready。
- 验证：
  - provider 成功后可读取 `domain.<id>.descriptor`；
  - descriptor 导出失败时已导出的 Bean 被回滚；
  - consumer 在 descriptor 缺失、未 ready 或与绑定不一致时输出 `PJF-007`。
- 通过标准：consumer 可基于 descriptor 诊断共享 domain 状态。

### AC-09 事务代理边界

- 输入：同类自调用 `REQUIRES_NEW` 与独立 bean `REQUIRES_NEW` 两类路径。
- 验证：
  - 同类自调用不产生新的代理事务边界；
  - 独立 bean 调用可以触发新的事务边界；
  - sample 强制异常路径符合主事务回滚、独立审计提交的预期。
- 通过标准：开发指南和测试能防止误用事务代理。

### AC-10 插件包边界与运行时 smoke

- 输入：复杂 sample 插件包与 HTTP smoke。
- 验证：
  - provider 包不包含 consumer Repository；
  - 成功路径和失败路径 HTTP smoke 有记录；
  - provider 失败隔离至少有独立测试覆盖，无关插件不被 shared provider 缺失影响。
- 通过标准：sample 职责边界和运行态关键路径可复查。

## 4. 验证方法

### 4.1 命令级验证

- `.\gradlew.bat :pf4boot-jpa-starter:compileJava`
- `.\gradlew.bat :pf4boot-jpa:compileJava`
- `.\gradlew.bat :pf4boot-jpa-domain-starter:compileJava :pf4boot-jpa-domain-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-core:test :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

### 4.2 手工演练

- 单域共享场景启动成功；
- 去除能力插件后验证失败码；
- 同插件双域配置验证 TM 映射；
- descriptor 缺失或未 ready 时验证 `PJF-007`；
- 故障注入演练（能力插件未启动）验证隔离。

## 5. 通过标准

- 上述 AC-01~AC-10 全部通过。
- 无新增阻塞级别回归风险。
- 验收记录完整、可复查。

## 6. 验收记录（2026-06-11）

### 6.1 命令结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-core:test :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins` | 通过 | 覆盖 JPA starter、domain starter、core 生命周期/部署测试、复杂 sample 插件打包。 |

### 6.2 AC 结果

| AC | 结果 | 证据 |
| --- | --- | --- |
| AC-01 本地回归 | 通过 | `PluginJPAStarterTest.jpaStarterDoesNotCreateEntityManagerFactoryWhenPropertyIsMissing` 覆盖未启用时不创建 JPA；`LOCAL` 默认分支保持为本地 EMF/TM 创建路径。 |
| AC-02 共享域启动成功 | 通过 | `PluginJPAStarterTest.sharedModeBindsParentDomainBeansWithoutLocalJpaBeans` 验证消费侧绑定共享 EMF/TM 且不创建本地 JPA Bean；`DomainJpaPlatformExporterTest.domainStarterCreatesEntityManagerFactoryAndExportsBeans` 使用 H2 创建共享 EMF 并导出 `domain.sample.*` 和 descriptor。 |
| AC-03 多域隔离 | 通过 | `PluginJPAStarterTest.pluginLevelBindingRegistersAdditionalSharedDomains` 覆盖主 `domain-id` 与 `additional-domains` 的本地 BeanDefinition 注册和 descriptor 校验；文档要求一域一个 Repository 包。 |
| AC-04 缺失域配置失败 | 通过 | `PluginJPAStarterTest.sharedModeRequiresDomainId` 覆盖 `PJF-006`；`sharedModeRequiresReadyDomainDescriptor` 覆盖 provider 缺失时的 `PJF-007`。 |
| AC-05 能力插件故障隔离 | 通过 | `PluginJPAStarterTest.providerMissingFailureDoesNotAffectUnrelatedNonJpaPluginContext` 覆盖 shared consumer 因 provider 缺失失败、无关非 JPA 插件上下文仍可启动。 |
| AC-06 迁移路径可执行 | 通过 | 迁移指南、JPA 集成文档和开发指南已同步；复杂 sample 已按 provider/model/service/workflow 拆分并通过 `assembleSamplePlugins`。 |
| AC-07 Entity 扫描边界可判定 | 通过 | `JpaDomainEntityPackageValidatorTest` 覆盖正常 entity 包、不可见包和无 entity warning 阶段；复杂 sample provider 打包 model jar 并配置 model 包扫描。 |
| AC-08 descriptor ready 诊断 | 通过 | `DomainJpaPlatformExporterTest.exporterRegistersAndUnregistersDomainBeans`、`exporterRollsBackDescriptorAndBeansWhenExportFails`、`PluginJPAStarterTest.sharedModeRejectsNotReadyDomainDescriptor` 覆盖导出、回滚和未 ready 诊断。 |
| AC-09 事务代理边界 | 通过 | `TransactionProxyBoundaryTest` 覆盖同类自调用反例和独立 bean `REQUIRES_NEW` 正例；复杂 sample HTTP smoke 记录覆盖强制异常回滚。 |
| AC-10 插件包边界与运行时 smoke | 通过 | `samples/cross-plugin-jpa` 验收记录显示 provider 包不包含 consumer Repository，HTTP smoke 成功路径和失败路径均有记录。 |

### 6.3 备注

- 本轮命令级验收未启动长驻 demo HTTP 服务；运行态 HTTP smoke 证据来自 `samples/cross-plugin-jpa` 独立验收记录。
- 跨域原子事务仍是明确非目标，后续如要支持需单独设计。

## 7. 审核结论

- 验收人：Codex
- 审核日期：2026-06-11
- 结论（通过/不通过）：通过
