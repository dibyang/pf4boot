# 插件框架下一阶段生产化增强实施规划

## 范围

本规划对应 [plugin-framework-next-stage-hardening.md](plugin-framework-next-stage-hardening.md)，覆盖三项下一阶段任务：

- P7 离线插件仓库治理。
- P8 版本范围严格预检。
- P9 runtime smoke Gradle/CI 化。

P0-P6 生产级完善主线保持完成态；本规划是新的独立追踪文档。

## 实施原则

- 一个任务卡只改变一个清晰边界，不跨 P7/P8/P9 混合提交。
- 默认关闭或 WARN，不破坏历史插件。
- 先写模型和单元测试，再接入部署、管理和 sample smoke。
- 涉及公共 API、配置、HTTP 响应或 sample 命令时，同步更新中文文档和英文翻译。
- 只有实际运行过命令的验收项才能标 `Done`。

## 阶段总览

| 阶段 | 主题 | 状态 | 主要产物 |
| --- | --- | --- | --- |
| P7 | 离线插件仓库治理 | Done | repository API、offline-index resolver、dry-run、sample index |
| P8 | 版本范围严格预检 | Done | version range matcher、pf4boot/spring/capability 预检、错误码 |
| P9 | runtime smoke Gradle/CI 化 | Done | Gradle `runtimeSmoke` task、`result.json`、CI 文档 |

## 通用任务卡模板

| 字段 | 要求 |
| --- | --- |
| 任务 ID | 使用 `P7-1a` 这类编号 |
| 输入文件 | 列出要先读取的设计、代码和测试 |
| 允许修改 | 明确模块和包路径 |
| 禁止修改 | 明确不能改变的默认行为或安全边界 |
| 完成证据 | 测试命令、文档检查或 smoke 输出 |
| 回滚口径 | 配置默认关闭，单任务可独立回退 |

## 阶段依赖

| 当前任务 | 依赖 | 说明 |
| --- | --- | --- |
| P7 | P1、P2、P5 | 复用 trust manifest、部署记录和管理 smoke |
| P8 | P4 | 复用 capability manifest 和部署预检 |
| P9 | P5 | 复用已稳定的 runtime smoke 脚本和 sample runtime |

## P7 离线插件仓库治理

### 目标

支持本地或内网挂载的 offline-index 仓库，让插件 release 可以被索引、校验、解析、dry-run 和审计。默认关闭，不影响现有本地目录加载。

### 实施步骤

1. 在 `pf4boot-api/src/main/java/net/xdob/pf4boot/repository/` 新增公共模型：
   - `PluginRepositoryResolver`
   - `PluginRepositoryIndex`
   - `PluginReleaseRecord`
   - `PluginReleaseRequest`
   - `PluginRepositoryResolution`
   - `PluginRepositoryStatus`
2. 在 `Pf4bootProperties` 增加配置：
   - `pluginRepositoryEnabled`
   - `pluginRepositoryType`
   - `pluginRepositoryLocation`
   - `pluginRepositoryTrustMode`
   - `pluginRepositoryCacheDirectory`
3. 在 `pf4boot-core` 新增 `OfflineIndexPluginRepositoryResolver`：
   - 读取 `repository-index.json`。
   - 校验 schema version、repositoryId、相对路径、package sha256。
   - 路径 resolve 后必须仍在 repository root 内。
   - 第一阶段 signature 字段可进入 WARN 诊断，不引入签名算法强依赖。
4. 扩展 `DefaultPluginDeploymentService` 或新增 adapter，让 repository release 可转换为 staged package 后复用现有 plan/replace。
5. 在 `pf4boot-management-starter` 增加仓库 dry-run 入口或扩展现有 plan request：
   - 请求包含 pluginId、version 或 versionRange。
   - 响应包含 repositoryId、release version、package 摘要和 deployment checks。
6. 在 `pf4boot-actuator` 的 governance snapshot 增加 repository 摘要。
7. 在 `samples/cross-plugin-jpa` 增加 sample offline repository index 生成说明和最小 sample index。

### 必测用例

| 测试类 | 用例 |
| --- | --- |
| `OfflineIndexPluginRepositoryResolverTest` | `disabledRepositoryDoesNotLoadIndex`、`loadsValidIndex`、`rejectsPathTraversal`、`rejectsPackageChecksumMismatch`、`selectsExactVersion`、`selectsRollbackCandidate` |
| `DefaultPluginDeploymentServiceTest` | `planReplacementFromRepositoryRelease`、`repositoryReleaseRecordsSafeSummary` |
| `PluginManagementControllerTest` | `repositoryDryRunRequiresWriteAuthorization`、`repositoryDryRunReplaysIdempotencyKey` |
| `Pf4bootGovernanceEndpointTest` | `exposesRepositorySummaryWhenResolverAvailable` |

### 禁止事项

- 不要在 P7 引入远程 HTTP 下载、对象存储 SDK 或中心服务。
- 不要让仓库索引替代 PF4J descriptor。
- 不要在响应、日志或 index 中保存 token、私钥、完整绝对敏感路径。
- 不要默认开启仓库治理。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P7-1 | 定义 repository 公共 API 和配置字段 | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P7-2 | 实现 offline-index resolver 和索引校验 | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*OfflineIndexPluginRepositoryResolverTest*"` |
| P7-3 | 接入 deployment plan/staging，不直接改变运行态 | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginDeploymentServiceTest*"` |
| P7-4 | 管理 API 增加 repository dry-run/plan | `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P7-5 | Actuator 暴露 repository 只读摘要 | `pf4boot-actuator` | `.\gradlew.bat :pf4boot-actuator:test` |
| P7-6 | sample index 和开发指南 | `samples/cross-plugin-jpa`、`docs/design` | sample 打包检查、文档检查 |

### 小任务卡

| ID | 输入文件 | 允许修改 | 关键步骤 | 完成证据 |
| --- | --- | --- | --- | --- |
| P7-1a | `PluginDeploymentService`、`DeploymentPlan`、`Pf4bootProperties` | `pf4boot-api` | 新增 repository POJO/SPI；public 类型补 JavaDoc；配置 setter null 回退默认 | `.\gradlew.bat :pf4boot-api:compileJava` |
| P7-2a | `DefaultPluginTrustManifestLoader`、core 测试风格 | `pf4boot-core` | JSON 解析、schema 校验、相对路径校验、摘要校验 | targeted test |
| P7-2b | `PluginCapabilityPrecheck`、版本 matcher 若已存在 | `pf4boot-core` | 支持按 exact version 选择 release；versionRange 选择可先依赖 P8 后补 | targeted test |
| P7-3a | `DefaultPluginDeploymentService` | `pf4boot-core` | release -> staged path -> plan；dry-run 不启动/替换插件 | deployment service test |
| P7-4a | `PluginManagementController`、request factory、安全测试 | `pf4boot-management-starter` | 写鉴权、幂等、脱敏、错误码 | management starter test |
| P7-5a | `Pf4bootGovernanceEndpoint` | `pf4boot-actuator` | 只读 repository summary；resolver 异常转 warning | actuator test |
| P7-6a | sample README、开发指南中英文版 | `samples/cross-plugin-jpa`、`docs/design` | 增加 repository-index 示例、生成/校验/部署 dry-run 步骤 | 文档 diff 和 U+FFFD 检查 |

### 退出条件

- 默认关闭时历史流程无行为变化。
- offline-index 能解析有效 release，并拒绝路径越界和摘要不匹配。
- 管理 dry-run 可展示 repository release 到 deployment plan 的检查结果。
- Actuator 只读摘要不会触发仓库写入或部署。

## P8 版本范围严格预检

### 目标

实现统一版本范围解析和匹配，把 `pf4bootVersionRange`、`springBootVersionRange`、`capability.versionRange` 接入部署预检。默认 WARN 或 DISABLED，允许后续 ENFORCE 阻断不兼容插件。

### 实施步骤

1. 在 `pf4boot-api/src/main/java/net/xdob/pf4boot/version/` 新增：
   - `VersionRangeMatcher`
   - `VersionRange`
   - `VersionBoundary`
   - `VersionMatchResult`
2. 在 `pf4boot-core` 新增 `DefaultVersionRangeMatcher`：
   - 支持精确版本和 `[1,2)`、`(1,2]`、`[1,)`、`(,2]`。
   - 对无法解析表达式返回结构化失败，不抛出裸异常到 Controller。
3. 扩展 `PluginCapabilityPrecheck`：
   - provider capability version 必须匹配 requirement versionRange。
   - 匹配失败返回 `PFC-003`，范围非法返回 `PFC-004`。
4. 在部署预检中检查 trust manifest 的：
   - `pf4bootVersionRange`
   - `springBootVersionRange`
   - `requiredCapabilities[].versionRange`
5. 增加配置：
   - `pluginCompatibilityPrecheckMode`
   - `pluginCompatibilityPf4bootVersion`
   - `pluginCompatibilitySpringBootVersion`
6. 更新 sample manifest，覆盖成功和失败范围。
7. 更新开发指南，说明支持的范围语法和 ENFORCE 迁移策略。

### 必测用例

| 测试类 | 用例 |
| --- | --- |
| `DefaultVersionRangeMatcherTest` | `matchesExactVersion`、`matchesInclusiveExclusiveRange`、`matchesOpenEndedRange`、`rejectsInvalidRange`、`comparesQualifiedVersionsDeterministically` |
| `PluginCapabilityPrecheckTest` | `rejectsCapabilityVersionOutsideRange`、`warnsInvalidCapabilityVersionRange` |
| `DefaultPluginDeploymentServiceTest` | `planWarnsPf4bootVersionMismatch`、`planRejectsSpringBootVersionMismatchInEnforceMode` |
| `DefaultPluginTrustManifestLoaderTest` | `loadsFrameworkCompatibilityRanges` |

### 禁止事项

- 不要引入 Maven Artifact Resolver 或 OSGi 版本库作为强依赖。
- 不要默认阻断历史插件。
- 不要把复杂 semver 预发布规则伪装成完整支持；文档必须写清第一阶段支持范围。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P8-1 | 定义 version range 公共 API | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P8-2 | 实现默认 parser/matcher | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultVersionRangeMatcherTest*"` |
| P8-3 | capability versionRange 接入预检 | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*PluginCapabilityPrecheckTest*"` |
| P8-4 | pf4boot/spring 版本范围接入 deployment plan | `pf4boot-core`、`pf4boot-starter` | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginDeploymentServiceTest*"` |
| P8-5 | sample manifest 和开发指南 | `samples/cross-plugin-jpa`、`docs/design` | sample 打包检查、文档检查 |

### 小任务卡

| ID | 输入文件 | 允许修改 | 关键步骤 | 完成证据 |
| --- | --- | --- | --- | --- |
| P8-1a | `PluginCapabilityRequirement`、`PluginCapabilityPrecheckResult` | `pf4boot-api` | 新增 version 包；结果包含 code/message，不改已有构造语义 | api compile |
| P8-2a | core 单测目录 | `pf4boot-core` | 解析范围、比较版本、非法表达式返回 result | matcher test |
| P8-3a | `PluginCapabilityPrecheck` | `pf4boot-core` | name/attribute 匹配后再检查 versionRange；consumer-owned 属性仍忽略 | capability precheck test |
| P8-4a | `DefaultPluginDeploymentService`、`Pf4bootProperties` | `pf4boot-api`、`pf4boot-core`、`pf4boot-starter` | 框架/Spring 版本 mismatch 转 deployment check | deployment service test |
| P8-5a | trust manifest 示例、开发指南中英文版 | `samples/cross-plugin-jpa`、`docs/design` | 增加范围语法、WARN 到 ENFORCE 迁移、排错表 | 文档检查 |

### 退出条件

- 版本范围解析器覆盖常用 Maven 风格范围。
- capability provider 版本不满足 requirement 时能被预检发现。
- 框架/Spring Boot 版本不满足 manifest 时能按模式 warning 或 reject。
- 默认配置不阻断历史插件。

## P9 runtime smoke Gradle/CI 化

### 目标

把已有 runtime smoke 封装为稳定 Gradle task，输出机器可读报告和明确退出码，方便本地、CI 和小模型复验。

### 实施步骤

1. 在 `samples/cross-plugin-jpa/app-run/build.gradle` 增加 `runtimeSmoke` task。
2. task 依赖 `assembleSampleRuntime`，默认复用 `samples/cross-plugin-jpa/scripts/runtime-smoke.ps1 -SkipAssemble`。
3. task 支持参数：
   - `-Ppf4bootSmokePort=7791`
   - `-Ppf4bootSmokeKeepWorkDir=true`
   - `-Ppf4bootSmokeSkipAssemble=true`
4. 修改 smoke 脚本以支持输出 `result.json`：
   - 成功和失败都写报告。
   - 失败时报告包含 failed check、HTTP status/error code、日志路径。
   - 不写 token 和完整敏感路径。
5. 可选生成 JUnit XML，供 CI 测试报告展示。
6. 更新 sample README、开发指南和验收文档。

### 必测场景

| 场景 | 验收 |
| --- | --- |
| task 成功 | Gradle task 退出 0，报告 `status=PASSED` |
| host 启动失败 | Gradle task 退出非 0，报告 `hostReady=FAILED`，打印日志尾部 |
| 管理鉴权失败 | 报告包含安全错误码，不泄露 token |
| 重复幂等请求 | 报告 `managementIdempotency=PASSED` |
| 清理 | 正常和失败路径均清理进程；失败时保留日志 |

### 禁止事项

- 不要关闭管理 token 或修改默认安全策略。
- 不要把坏插件包放入启动扫描目录。
- 不要依赖人工浏览器、外部网络或全局用户目录。
- 不要把 runtime smoke 标成普通 unit test；它应是显式 task。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P9-1 | 增加 `runtimeSmoke` Gradle task | `samples/cross-plugin-jpa/app-run` | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:tasks --all` |
| P9-2 | smoke 脚本输出 `result.json` | `samples/cross-plugin-jpa/scripts` | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke` |
| P9-3 | 可选 JUnit XML 或 CI 文档 | `samples/cross-plugin-jpa`、`docs/design` | 报告文件检查 |
| P9-4 | 开发指南和 README 更新 | `docs/design`、sample README | 文档检查 |

### 小任务卡

| ID | 输入文件 | 允许修改 | 关键步骤 | 完成证据 |
| --- | --- | --- | --- | --- |
| P9-1a | `app-run/build.gradle`、现有 runtime smoke 脚本 | `samples/cross-plugin-jpa/app-run` | Exec task 包装 PowerShell；参数透传；输出目录固定 | tasks --all |
| P9-2a | `runtime-smoke.ps1` | `samples/cross-plugin-jpa/scripts` | check 结构化记录；finally 写 result.json；失败退出非 0 | runtimeSmoke |
| P9-2b | smoke 报告样例 | `samples/cross-plugin-jpa` | 确认报告不含 token、绝对敏感路径、堆栈 | 报告检查 |
| P9-3a | CI 文档 | `docs/design`、sample README | 写明推荐 CI 命令、失败排查、产物收集 | 文档检查 |

### 退出条件

- 单条 Gradle 命令能执行 runtime smoke。
- 成功和失败都产生 `result.json`。
- CI 可根据 task exit code 和报告判断结果。
- 现有 PowerShell 脚本仍可直接运行。

## 推荐执行顺序

1. P8 先做：版本范围 parser/matcher 边界最小，能服务 P7 release 选择。
2. P7 第二：仓库治理依赖版本选择和现有部署预检。
3. P9 第三：把 P7/P8 的 sample 验证纳入统一 Gradle smoke。

若只想快速提升 CI 可用性，也可以先做 P9-1/P9-2，但不要把 P7/P8 未实现的检查写成 `PASSED`。

## 每阶段完成定义

- 代码实现保持 Java 8 兼容。
- 中英文设计、规划或指南同步更新。
- 验收文档对应条目填入真实命令和证据。
- 已运行规划中的最小 Gradle 验证；无法运行时记录具体原因。
- 本地提交包含单一阶段的清晰提交信息。
