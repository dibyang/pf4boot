# 插件框架生产级完善验收追踪

## 使用说明

本文件用于追踪 [plugin-framework-production-hardening-plan.md](plugin-framework-production-hardening-plan.md) 中各阶段的验收状态。状态值建议使用：

- `Planned`：已规划，尚未实现。
- `In Progress`：正在实现。
- `Done`：已完成并有验证证据。
- `Blocked`：受外部条件阻塞，需要说明原因。

每次完成任务后，应补充证据，包含提交号、测试命令、关键日志或文档链接。

## P0 设计与追踪基线

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P0-AC1：中文生产级完善设计文档已新增 | Done | `docs/design/plugin-framework-production-hardening.md` |
| P0-AC2：英文翻译已新增并与中文主旨一致 | Done | `docs/design/en/plugin-framework-production-hardening.md` |
| P0-AC3：实施规划和验收追踪文档已独立维护 | Done | `docs/design/plugin-framework-production-hardening-plan.md`、`docs/design/plugin-framework-production-hardening-acceptance.md` |
| P0-AC4：中英文设计索引已更新 | Done | `docs/design/README.md`、`docs/design/en/README.md` |
| P0-AC5：文档 diff 无明显乱码和链接遗漏 | Done | 已执行 `git diff --check`、`rg -n "plugin-framework-production-hardening" docs\design\README.md docs\design\en\README.md`、U+FFFD 检查 |

## P1 插件包签名与信任链

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P1-AC1：`PluginPackageTrustVerifier` 及 request/result/trust root SPI 编译通过 | Planned | 待补充 |
| P1-AC2：默认配置不阻断历史无签名插件 | Planned | 待补充 |
| P1-AC3：WARN 模式能记录缺签名、签名无效、信任根缺失 | Planned | 待补充 |
| P1-AC4：ENFORCE 模式能阻断不可信插件包 | Planned | 待补充 |
| P1-AC5：管理接口错误响应不泄露 token、私钥路径、完整堆栈或敏感路径 | Planned | 待补充 |
| P1-AC6：开发指南包含插件包 manifest 示例和 WARN 到 ENFORCE 的迁移步骤 | Planned | 待补充 |

## P2 操作/部署/审计持久化

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P2-AC1：`PluginOperationRecorder` 与 `PluginIdempotencyStore` 公共 SPI 编译通过 | Planned | 待补充 |
| P2-AC2：默认内存实现保持现有管理接口行为 | Planned | 待补充 |
| P2-AC3：文件 recorder 支持原子写入，半写记录不会被识别为成功 | Planned | 待补充 |
| P2-AC4：相同幂等 key 在宿主重启后仍能返回已有结果或冲突 | Planned | 待补充 |
| P2-AC5：`EXECUTING` 部署记录在重启后可被恢复扫描识别 | Planned | 待补充 |
| P2-AC6：审计记录不包含 token、完整敏感路径和完整异常堆栈 | Planned | 待补充 |

## P3 生命周期并发与资源泄漏验证

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P3-AC1：同一插件并发 start/stop/reload 被串行化或明确拒绝 | Planned | 待补充 |
| P3-AC2：重复 start 不会重复注册 share bean、MVC mapping、interceptor 或 scheduler | Planned | 待补充 |
| P3-AC3：stop 后动态资源计数归零，或输出明确残留报告 | Planned | 待补充 |
| P3-AC4：加载失败、启动失败、health check 失败均能进入可诊断状态 | Planned | 待补充 |
| P3-AC5：热替换失败时能回滚旧包；回滚失败时进入人工介入状态并保留现场 | Planned | 待补充 |
| P3-AC6：复杂样例包含可触发失败路径的演示插件或配置 | Planned | 待补充 |

## P4 能力声明与兼容矩阵

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P4-AC1：capability manifest 模型和解析规则编译通过 | Planned | 待补充 |
| P4-AC2：缺失 manifest 的历史插件默认兼容或 WARN，不被默认阻断 | Planned | 待补充 |
| P4-AC3：管理部署预检能识别缺失能力并返回可读错误 | Planned | 待补充 |
| P4-AC4：JPA 数据源插件能声明 `jpa.datasource` | Planned | 待补充 |
| P4-AC5：JPA 消费插件能声明 `jpa.consumer`，并按实体/Repository 包路径分组 | Planned | 待补充 |
| P4-AC6：插件依赖多个数据源时，预检能指出每个包扫描路径所属数据源 | Planned | 待补充 |
| P4-AC7：兼容矩阵覆盖框架版本、Java 版本、能力版本和插件依赖范围 | Planned | 待补充 |

## P5 管理 smoke 与观测闭环

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P5-AC1：复杂样例插件可通过单一 Gradle 命令完成打包 | Planned | 待补充 |
| P5-AC2：sample host smoke 能启动宿主、调用管理接口、验证 JPA 示例并关闭进程 | Planned | 待补充 |
| P5-AC3：本地管理接口 smoke 使用 token，不绕过安全保护 | Planned | 待补充 |
| P5-AC4：Actuator 暴露信任校验摘要、部署摘要和清理报告摘要 | Planned | 待补充 |
| P5-AC5：metrics 覆盖管理请求、拒绝、幂等命中、部署耗时和回滚次数 | Planned | 待补充 |
| P5-AC6：失败 smoke 能输出 HTTP 响应、部署记录和可定位日志 | Planned | 待补充 |

## P6 后续决策专题

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| P6-AC1：JPA 运行时刷新/EntityManagerFactory 重建已有独立设计结论 | Planned | 待补充 |
| P6-AC2：跨数据源事务已有独立设计结论，明确禁止、Saga、Outbox 或 XA 可选模块路径 | Planned | 待补充 |
| P6-AC3：插件市场/仓库治理已有独立设计结论 | Planned | 待补充 |
| P6-AC4：控制台 UI 是否进入项目范围已有独立设计结论 | Planned | 待补充 |

## 当前建议

优先推进 P1 和 P2。原因是签名信任链与持久化记录是后续严格治理、审计恢复、管理 smoke 和生产环境 ENFORCE 的基础；没有这两项，继续扩展热替换和管理接口会缺少可信边界与恢复证据。
