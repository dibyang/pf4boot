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
| P10-A-AC1：配置可区分 repository dry-run 和 real replace | Planned | 待实现 |
| P10-A-AC2：release 包进入受控 staging cache，并重新校验 sha256 | Planned | 待实现 |
| P10-A-AC3：校验失败不会进入 replace | Planned | 待实现 |
| P10-A-AC4：真实 replace 复用现有 rollback 编排 | Planned | 待实现 |
| P10-A-AC5：幂等 replay 不重复执行 replace | Planned | 待实现 |
| P10-A-AC6：部署记录和 Actuator 输出 repository 执行摘要且不泄露绝对路径 | Planned | 待实现 |

## P10-B 跨平台 runtime smoke

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P10-B-AC1：`runtimeSmoke` task 保持可发现且命令不变 | Planned | 待实现 |
| P10-B-AC2：Java 或跨平台 runner 可执行完整 smoke | Planned | 待实现 |
| P10-B-AC3：成功和失败都生成 `result.json` | Planned | 待实现 |
| P10-B-AC4：生成 JUnit XML 并可被 CI 收集 | Planned | 待实现 |
| P10-B-AC5：PowerShell 脚本仍可作为 Windows 入口 | Planned | 待实现 |
| P10-B-AC6：报告不包含 token、私钥、完整堆栈或敏感绝对路径 | Planned | 待实现 |

## P10-C no-jpa/unrelated 隔离示例

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P10-C-AC1：新增 unrelated 插件不依赖 JPA starter 或 datasource provider | Planned | 待实现 |
| P10-C-AC2：正常场景 unrelated 插件能启动并响应检查 | Planned | 待实现 |
| P10-C-AC3：JPA provider 缺失时 JPA consumer 失败，无关插件仍工作 | Planned | 待实现 |
| P10-C-AC4：JPA provider 启动失败时无关插件仍工作 | Planned | 待实现 |
| P10-C-AC5：runtime smoke 报告包含 `unrelatedPluginAlive` 检查 | Planned | 待实现 |
| P10-C-AC6：README 和开发指南说明隔离语义 | Planned | 待实现 |

## 当前建议

P10 建议先做 P10-C，再做 P10-B，最后做 P10-A。这样先建立运行态隔离基线，再把验收跨平台化，最后再打开真实仓库部署链。
