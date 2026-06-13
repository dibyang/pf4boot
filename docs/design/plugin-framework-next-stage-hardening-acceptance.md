# 插件框架下一阶段生产化增强验收追踪

## 使用说明

本文件用于追踪 [plugin-framework-next-stage-hardening-plan.md](plugin-framework-next-stage-hardening-plan.md) 中 P7-P9 的验收状态。状态值建议使用：

- `Planned`：已规划，尚未实现。
- `In Progress`：正在实现。
- `Done`：已完成并有验证证据。
- `Blocked`：受外部条件阻塞，需要说明原因。

只有实际完成代码、文档和验证后，才能把条目标为 `Done`。

## P7 离线插件仓库治理

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P7-AC0：设计和规划已明确 offline-index 边界、API、索引格式、默认关闭和禁止事项 | Done | `docs/design/plugin-framework-next-stage-hardening.md`、`docs/design/plugin-framework-next-stage-hardening-plan.md` |
| P7-AC1：repository 公共 API 和配置字段编译通过 | Done | 新增 `net.xdob.pf4boot.repository.*` 和 `Pf4bootProperties` repository 配置；已执行 `.\gradlew.bat :pf4boot-api:compileJava` |
| P7-AC2：offline-index resolver 能加载有效索引并拒绝路径越界 | Done | `OfflineIndexPluginRepositoryResolverTest.loadsValidIndex`、`rejectsPathTraversal`；已执行 `.\gradlew.bat :pf4boot-core:test` |
| P7-AC3：包 sha256 不匹配时阻断 staging | Done | `OfflineIndexPluginRepositoryResolverTest.rejectsPackageChecksumMismatch` |
| P7-AC4：repository release 能转换为 deployment plan，dry-run 不改变运行态 | Done | `DefaultPluginDeploymentServiceTest.planReplacementFromRepositoryRelease`、`repositoryReleaseRecordsSafeSummary` |
| P7-AC5：管理 dry-run 通过鉴权、幂等和脱敏测试 | Done | `PluginManagementControllerTest.repositoryPlanEndpointUsesReleaseRequestAndReplaysIdempotency`；已执行 `.\gradlew.bat :pf4boot-management-starter:test --tests "*PluginManagementControllerTest"` |
| P7-AC6：Actuator 只读暴露 repository 摘要 | Done | `Pf4bootGovernanceEndpointTest.summaryIncludesGovernanceConfigurationAndDeploymentMetrics` 覆盖 repository 摘要；已执行 `.\gradlew.bat :pf4boot-actuator:test` |
| P7-AC7：sample 提供 repository-index 示例和使用说明 | Done | `samples/cross-plugin-jpa/repository/repository-index.example.json`、`samples/cross-plugin-jpa/README.md`、`docs/design/plugin-developer-guide.md` |

## P8 版本范围严格预检

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P8-AC0：设计和规划已明确版本范围语法、WARN/ENFORCE 行为和错误码 | Done | `docs/design/plugin-framework-next-stage-hardening.md`、`docs/design/plugin-framework-next-stage-hardening-plan.md` |
| P8-AC1：version range 公共 API 编译通过 | Done | 新增 `net.xdob.pf4boot.version.*`；已执行 `.\gradlew.bat :pf4boot-api:compileJava` |
| P8-AC2：默认 matcher 覆盖精确版本、闭开区间、开区间和非法表达式 | Done | `DefaultVersionRangeMatcherTest`；已执行 `.\gradlew.bat :pf4boot-core:test` |
| P8-AC3：capability `versionRange` 不满足时能生成 deployment check | Done | `PluginCapabilityPrecheckTest.rejectsCapabilityVersionOutsideRange`、`warnsInvalidCapabilityVersionRange` |
| P8-AC4：`pf4bootVersionRange` 不满足时能按模式 warning 或 reject | Done | `DefaultPluginDeploymentServiceTest.planWarnsPf4bootVersionMismatch` |
| P8-AC5：`springBootVersionRange` 不满足时能按模式 warning 或 reject | Done | `DefaultPluginDeploymentServiceTest.planRejectsSpringBootVersionMismatchInEnforceMode` |
| P8-AC6：sample manifest 和开发指南包含范围语法与迁移说明 | Done | `docs/design/plugin-developer-guide.md` 和英文版包含范围语法、配置和 WARN 到 ENFORCE 建议 |

## P9 runtime smoke Gradle/CI 化

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P9-AC0：设计和规划已明确 Gradle task、报告路径、CI 退出码和安全约束 | Done | `docs/design/plugin-framework-next-stage-hardening.md`、`docs/design/plugin-framework-next-stage-hardening-plan.md` |
| P9-AC1：`:samples:cross-plugin-jpa:app-run:runtimeSmoke` task 可被 Gradle 发现 | Done | 已执行 `.\gradlew.bat :samples:cross-plugin-jpa:app-run:tasks --all`，输出包含 `runtimeSmoke` |
| P9-AC2：runtime smoke 成功时 task 退出 0 并生成 `result.json` | Done | 已执行 `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke`，输出全部 `SMOKE_*` marker；`result.json` status 为 `PASSED` |
| P9-AC3：runtime smoke 失败时 task 退出非 0，报告失败 check 并打印日志尾部 | Done | 已执行 `runtime-smoke.ps1 -SkipAssemble -TimeoutSeconds 0 -ResultPath ...failure-result.json`，报告 status 为 `FAILED` 且包含 `runtimeSmoke` failed check |
| P9-AC4：报告不包含 token、私钥、完整堆栈或敏感绝对路径 | Done | 成功报告只包含 check 名称、状态、HTTP 摘要和错误码；失败报告只包含 `PFS-001 host not ready within 0 seconds` |
| P9-AC5：README 和开发指南提供本地/CI 推荐命令与排障步骤 | Done | `samples/cross-plugin-jpa/README.md`、`docs/design/plugin-developer-guide.md` 和英文版 |

## 当前建议

P7-P9 已完成。后续若继续扩展远程仓库、跨平台 Java smoke runner、JUnit XML 或更完整语义版本比较，应新增独立规划和验收条目。
