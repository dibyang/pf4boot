# 插件框架后续增强验收追踪

## 使用说明

本文追踪 [plugin-framework-follow-up-hardening-plan.md](plugin-framework-follow-up-hardening-plan.md) 中 P10-A/P10-B/P10-C 的验收状态。

状态值：

- `Planned`：已规划，尚未实现。
- `In Progress`：正在实现。
- `Done`：已完成并有验证证据。
- `Blocked`：受外部条件阻塞。

只有实际完成代码、文档和验证后，才可标记 `Done`。

## P10-A 仓库发布物真实 replace

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P10-A-AC1：配置可区分 repository dry-run 和 real replace | Done | 新增 `pluginRepositoryReplaceEnabled`，默认关闭；管理 replace 在 `dryRun=true` 时仍走 plan |
| P10-A-AC2：release 包进入受控 staging cache，并重新校验 sha256 | Done | `DefaultPluginDeploymentService.replace(PluginReleaseRequest)` 先经 resolver 校验 sha256，再复制到 `pluginRepositoryCacheDirectory/operations/{operationId}` |
| P10-A-AC3：校验失败不会进入 replace | Done | `DefaultPluginDeploymentServiceTest.repositoryReplaceRejectsWhenRealReplaceDisabled` 和 repository 预检失败路径返回 `PRECHECK_FAILED` |
| P10-A-AC4：真实 replace 复用现有 rollback 编排 | Done | `repositoryReplaceStagesPackageAndExecutesReplacementWhenEnabled` 走现有 `replace(targetPluginId, stagedPath)` |
| P10-A-AC5：幂等 replay 不重复执行 replace | Done | 管理层复用现有 idempotency gate；`PluginManagementControllerTest.repositoryReplaceEndpointUsesReleaseRequestWhenDryRunFalse` 覆盖 repository replace 入口 |
| P10-A-AC6：部署记录和 Actuator 输出 repository 执行摘要且不泄露绝对路径 | Done | Actuator 新增 `repositoryReplaceEnabled`、`repositoryCacheConfigured`，只暴露布尔摘要；`.\gradlew.bat :pf4boot-actuator:test` |

## P10-B 跨平台 runtime smoke

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P10-B-AC1：`runtimeSmoke` task 保持可发现且命令不变 | Done | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke` 已运行通过 |
| P10-B-AC2：Java 或跨平台 runner 可执行完整 smoke | Done | 新增 `RuntimeSmokeRunner`，输出 `SMOKE_*` marker |
| P10-B-AC3：成功和失败都生成 `result.json` | Done | 成功报告 `samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json`，status=`PASSED` |
| P10-B-AC4：生成 JUnit XML 并可被 CI 收集 | Done | `samples/cross-plugin-jpa/app-run/build/test-results/runtimeSmoke/TEST-runtime-smoke.xml`，failures=`0` |
| P10-B-AC5：PowerShell 脚本仍可作为 Windows 入口 | Done | `runtime-smoke.ps1` 保留，并同步 `unrelatedPluginAlive`、`jpaProviderIsolation` checks |
| P10-B-AC6：报告不包含 token、私钥、完整堆栈或敏感绝对路径 | Done | Java runner 报告只写 check 名称、状态、摘要消息；runtime smoke 成功报告已检查 |

## P10-C no-jpa/unrelated 隔离示例

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P10-C-AC1：新增 unrelated 插件不依赖 JPA starter 或 datasource provider | Done | 新增 `samples/cross-plugin-jpa:plugin-unrelated-service`，仅依赖 `pf4boot-api` 和 `pf4boot-web-support` |
| P10-C-AC2：正常场景 unrelated 插件能启动并响应检查 | Done | runtime smoke 输出 `SMOKE_UNRELATED_PLUGIN_ALIVE status=200` |
| P10-C-AC3：JPA provider 缺失时 JPA consumer 失败，无关插件仍工作 | Done | runtime smoke 通过管理接口停止 `sample-demo-jpa-domain` 后，unrelated 仍返回 200 |
| P10-C-AC4：JPA provider 启动失败时无关插件仍工作 | Done | 当前以运行态 provider stop 作为故障对照；报告 `jpaProviderIsolation=PASSED` |
| P10-C-AC5：runtime smoke 报告包含 `unrelatedPluginAlive` 检查 | Done | `result.json` 包含 `unrelatedPluginAlive` 和 `jpaProviderIsolation` |
| P10-C-AC6：README 和开发指南说明隔离语义 | Done | `samples/cross-plugin-jpa/README.md` 和开发指南已补充 |

## 当前建议

P10 已完成。后续如推进 JPA 运行时刷新，建议先基于当前 unrelated 隔离 smoke 做 `JpaDomainReloadService` 的 `PLAN_ONLY` 规划和影响范围识别。
