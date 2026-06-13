# 插件框架后续增强实施规划

## 范围

本规划对应 [plugin-framework-follow-up-hardening.md](plugin-framework-follow-up-hardening.md)，覆盖 P10 三个增强方向：

- P10-A 仓库发布物真实 replace 执行链。
- P10-B 跨平台 runtime smoke runner 与 JUnit XML。
- P10-C no-jpa/unrelated 隔离示例。

P7-P9 已完成，本规划不回改 P7-P9 状态。

## 实施原则

- 每个子阶段单独提交，避免仓库部署、smoke runner 和 sample 隔离互相混杂。
- 默认行为保持兼容；真实 replace 和新 smoke 行为必须显式启用或保留旧入口。
- sample 代码只能放在 `samples/*`，不把演示模块加回根级旧示例。
- 验收文档只在实际运行命令后标记 `Done`。
- 中英文设计、规划和验收文档保持同步。

## 阶段总览

| 阶段 | 主题 | 状态 | 主要产物 |
| --- | --- | --- | --- |
| P10-A | 仓库发布物真实 replace | Planned | staging cache、repository replace、审计摘要、rollback 验证 |
| P10-B | 跨平台 runtime smoke | Planned | Java runner、JUnit XML、统一报告 schema |
| P10-C | no-jpa/unrelated 隔离示例 | Planned | 无关插件、故障场景、runtime smoke 检查 |

## 阶段依赖

| 当前任务 | 依赖 | 说明 |
| --- | --- | --- |
| P10-A | P7、P8 | 仓库 release 和版本/能力预检已可用于 plan |
| P10-B | P9 | 复用 `runtimeSmoke` task 名称和 `result.json` schema |
| P10-C | 复杂 JPA sample、P10-B 可选 | 可先用现有 PowerShell smoke 验证，再接入 Java runner |

## P10-A 仓库发布物真实 replace

### 目标

把仓库 release 从 dry-run 推进到真实 replace，但继续保持默认安全：未开启真实 replace 时只允许 plan/dry-run。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P10-A1 | 定义 repository replace 执行配置和 options | `pf4boot-api` | `.\gradlew.bat :pf4boot-api:compileJava` |
| P10-A2 | 实现 staging cache 复制、摘要复核和清理 | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*Repository*"` |
| P10-A3 | 接入真实 replace 和 rollback | `pf4boot-core` | `.\gradlew.bat :pf4boot-core:test --tests "*DefaultPluginDeploymentServiceTest*"` |
| P10-A4 | 管理 API 支持 repository real replace | `pf4boot-management-starter` | `.\gradlew.bat :pf4boot-management-starter:test` |
| P10-A5 | 部署记录和 Actuator 增加 repository 执行摘要 | `pf4boot-core`、`pf4boot-actuator` | `.\gradlew.bat :pf4boot-actuator:test` |
| P10-A6 | sample README 和开发指南补真实 replace 示例 | `samples/cross-plugin-jpa`、`docs/design` | 文档检查 |

### 禁止事项

- 不引入远程下载。
- 不在 HTTP 响应暴露 staging cache 绝对路径。
- 不绕过现有管理鉴权、幂等和审计。
- 不让 dry-run 请求执行真实 replace。

### 退出条件

- dry-run 和 real replace 行为可由配置明确区分。
- release 校验失败不会进入 staging。
- staging 成功后 replace 失败会进入现有 rollback。
- 幂等 replay 不重复执行 replace。

## P10-B 跨平台 runtime smoke

### 目标

保留 `runtimeSmoke` Gradle 入口，内部优先使用跨平台 runner，输出 `result.json` 和 JUnit XML，适配 Windows 和 Linux CI。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P10-B1 | 抽象 smoke check 和报告 schema | `samples/cross-plugin-jpa` | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:tasks --all` |
| P10-B2 | 实现 Java runtime smoke runner | `samples/cross-plugin-jpa/app-run` | `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke` |
| P10-B3 | 生成 JUnit XML | `samples/cross-plugin-jpa/app-run` | 检查 `build/test-results/runtimeSmoke/*.xml` |
| P10-B4 | PowerShell 脚本与 Java runner 报告 schema 对齐 | `samples/cross-plugin-jpa/scripts` | Windows smoke |
| P10-B5 | 文档补 CI 产物收集和失败排查 | `samples/cross-plugin-jpa`、`docs/design` | 文档检查 |

### 禁止事项

- 不删除 PowerShell 脚本。
- 不依赖浏览器、外部网络或私有用户目录。
- 不把 smoke runner 做成框架公共 API。

### 退出条件

- `runtimeSmoke` 在 Windows 可运行。
- 非 Windows 环境有明确 runner 路径，缺失依赖时错误清晰。
- 成功和失败都生成 `result.json`。
- JUnit XML 可被 CI 识别。

## P10-C no-jpa/unrelated 隔离示例

### 目标

增加一个不依赖 JPA 数据源的无关插件，验证 JPA provider 或 consumer 故障不会影响无关插件启动和服务。

### 任务

| ID | 任务 | 影响模块 | 验证 |
| --- | --- | --- | --- |
| P10-C1 | 设计 no-jpa/unrelated sample 模块边界 | `samples/cross-plugin-jpa` | 文档检查 |
| P10-C2 | 新增 unrelated API/service 插件 | `samples/cross-plugin-jpa` | `.\gradlew.bat :samples:cross-plugin-jpa:assembleSampleRuntime` |
| P10-C3 | 增加 unrelated HTTP endpoint 或 exported service | `samples/cross-plugin-jpa` | runtime smoke |
| P10-C4 | 增加 provider 缺失/失败运行态对照 | `samples/cross-plugin-jpa` | runtime smoke failure scenario |
| P10-C5 | README 和开发指南补隔离语义 | `samples/cross-plugin-jpa`、`docs/design` | 文档检查 |

### 禁止事项

- 不在 datasource provider 插件中放实体或 unrelated 业务。
- 不让 unrelated 插件依赖 `pf4boot-jpa-starter`。
- 不为了演示修改生产默认启动语义。

### 退出条件

- 正常场景下 unrelated 插件启动并响应检查。
- JPA provider 缺失或失败时，JPA consumer 失败被记录，无关插件仍工作。
- smoke 报告包含 `unrelatedPluginAlive=PASSED`。

## 推荐执行顺序

1. P10-C：先补隔离样例，给后续 smoke 和 repository replace 提供更强的验证对象。
2. P10-B：再做跨平台 smoke 和 JUnit XML，把 P10-C 纳入自动验收。
3. P10-A：最后开放 repository real replace，用前两项验证真实部署链。

## 每阶段完成定义

- 代码保持 Java 8 兼容。
- 中英文设计、规划或指南同步更新。
- 运行对应 Gradle 验证命令。
- 验收文档填写真实命令和证据。
- 本地提交信息能清楚说明阶段范围。
