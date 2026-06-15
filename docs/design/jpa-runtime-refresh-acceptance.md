# JPA 运行时刷新验收清单

## 1. 使用说明

本文跟踪 [jpa-runtime-refresh-plan.md](jpa-runtime-refresh-plan.md) 中 R0-R7 的验收状态。

状态值：

- `Planned`：已规划，尚未实现。
- `In Progress`：正在实现。
- `Done`：已完成并有验证证据。
- `Blocked`：受外部条件阻塞。

只有代码、文档和验证都完成后，才能标记 `Done`。

## 2. R0 文档和现状对齐

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| R0-AC1：新增中文设计、规划、验收文档 | Done | `jpa-runtime-refresh.md`、`jpa-runtime-refresh-plan.md`、`jpa-runtime-refresh-acceptance.md` |
| R0-AC2：新增英文翻译文档 | Done | `en/jpa-runtime-refresh.md`、`en/jpa-runtime-refresh-plan.md`、`en/jpa-runtime-refresh-acceptance.md` |
| R0-AC3：README 索引包含新文档 | Done | `docs/design/README.md` 和 `docs/design/en/README.md` 已更新 |
| R0-AC4：复杂 JPA 示例 provider 隔离验收状态与 P10 同步 | Done | `cross-plugin-jpa-transaction-complex-sample-acceptance.md` 已同步为通过 |
| R0-AC5：文档 diff 无空白错误和编码替换字符 | Done | `git diff --check`；U+FFFD 检查 |

## 3. R1 公共模型和配置

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| R1-AC1：`pf4boot-jpa` 提供 reload 公共模型和 SPI | Done | `pf4boot-jpa/src/main/java/net/xdob/pf4boot/jpa/reload/*` |
| R1-AC2：配置前缀 `pf4boot.plugin.jpa.domain-reload.*` 可绑定 | Done | `Pf4bootJpaProperties.DomainReload` |
| R1-AC3：默认 `DISABLED` 无行为变化 | Done | `DefaultJpaDomainReloadServiceTest.reloadDoesNotExecuteWhenConfiguredDisabledEvenIfRequestAsksExecuteMode`；runtime smoke `jpaReloadDisabledNoMutation` |
| R1-AC4：Java 8 编译通过 | Done | `.\gradlew.bat :pf4boot-jpa:compileJava :pf4boot-jpa-starter:compileJava` |
| R1-AC5：request 校验覆盖空 domain、缺幂等键、超长 reason、V1 不支持 providerReplacementPath | Done | `DefaultJpaDomainReloadServiceTest.reloadRejectsInvalidRequestsBeforeExecution`、`reloadRejectsProviderReplacementPathWithoutExecuting` |
| R1-AC6：failure code/blocker code 为稳定枚举，不直接暴露异常类名 | Done | `JpaDomainReloadFailureCode`、`JpaDomainReloadBlocker`；管理接口返回 code/message |

## 4. R2 PLAN_ONLY 影响范围识别

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| R2-AC1：能读取 `domain.{domainId}.descriptor` | Done | `DefaultJpaDomainReloadPlanService.findDescriptor` |
| R2-AC2：能识别 provider 插件和 descriptor 快照 | Done | `DefaultJpaDomainReloadPlanServiceTest` |
| R2-AC3：能输出 consumer 候选、unrelated 插件、停止顺序和启动顺序 | Done | `DefaultJpaDomainReloadPlanServiceTest`；runtime smoke `jpaReloadPlanOnly` |
| R2-AC4：plan 阶段不做任何 stop/start/reload 变更 | Done | plan service 只读实现；runtime smoke 先 plan 后业务仍可用 |
| R2-AC5：缺失 descriptor、provider 未运行、已有 reload 等阻断项可见 | Done | `JpaDomainReloadFailureCode` blocker；plan service 单测覆盖主要 blocker |
| R2-AC6：plan 输出排序稳定 | Done | plan service 对插件和 consumer 排序；单测固定顺序 |
| R2-AC7：存在 inferred consumer 时 execute 不可执行 | Done | `INFERRED_CONSUMER_PRESENT` blocker；plan service 单测覆盖 |

## 5. R3 Binding registry 和精确 consumer 识别

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| R3-AC1：shared consumer 启动成功后登记 `JpaPluginBinding` | Done | `PluginJPAStarter.registerBinding` |
| R3-AC2：consumer 停止后移除 binding | Done | `PluginJPAStarter.destroy` |
| R3-AC3：`mode=LOCAL` 插件不进入 shared domain reload 计划 | Done | registry 只登记 shared binding；plan service 只使用匹配 domain 的 binding |
| R3-AC4：绑定其它 domain 的插件不进入当前 domain reload 计划 | Done | `DefaultJpaPluginBindingRegistry.findByDomainId` |
| R3-AC5：依赖图 fallback 标记为 `INFERRED` | Done | `DefaultJpaDomainReloadPlanService.inferredConsumers` |
| R3-AC6：registry 线程安全，支持 snapshot 且不暴露内部可变集合 | Done | `DefaultJpaPluginBindingRegistryTest` |

## 6. R4 管理接口和 Actuator 只读观测

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| R4-AC1：plan HTTP 接口在服务存在时可用 | Done | `JpaDomainReloadManagementController.plan`；runtime smoke `jpaReloadPlanOnly` |
| R4-AC2：reload record 查询接口可用 | Done | `JpaDomainReloadManagementController.getRecord`；runtime smoke `jpaReloadRecord` |
| R4-AC3：默认禁用态不会执行刷新 | Done | runtime smoke `jpaReloadDisabledNoMutation` |
| R4-AC4：Actuator 输出只读摘要 | Done | `Pf4bootJpaReloadEndpoint`；runtime smoke `actuatorJpaReload` |
| R4-AC5：接口复用管理面鉴权、审计和幂等策略 | Done | controller 复用 `PluginManagementAuthorizer`、`PluginManagementRequestFactory`、`PluginManagementAuditRecorder` 和 `X-Idempotency-Key` |
| R4-AC6：管理接口输出不包含敏感绝对路径、token、完整堆栈 | Done | DTO 只返回 plan/record/code/message；runtime smoke 报告不写 token |
| R4-AC7：`current` 和 `record` 查询接口能区分不存在和进行中 | Done | `JpaDomainReloadService.getCurrent/getRecord`；controller 对 null 返回 `notFound` |

## 7. R5 执行模式

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| R5-AC1：同一 domain reload 串行执行 | Done | `DefaultJpaDomainReloadService` global lock + per-domain lock |
| R5-AC2：幂等键重复请求不会重复执行 | Done | `InMemoryJpaDomainReloadRecordRepository`；单测 `reloadReplaysSameIdempotencyKey` |
| R5-AC3：drain 失败时不停止插件 | Done | `DefaultJpaDomainReloadServiceTest.reloadDoesNotStopPluginsWhenDrainTimesOut`；runtime smoke 覆盖 `jpaReloadDrainTimeoutNoMutation` |
| R5-AC4：consumer 按依赖下游优先停止 | Done | 单测 `reloadStopsConsumersRestartsProviderAndStartsConsumers` |
| R5-AC5：provider 重启后旧 JPA 导出 Bean 已注销且新 descriptor ready | Done | `verifyProviderExportsRemoved` 检查 DataSource/EMF/TM/descriptor；health check 重新 plan |
| R5-AC6：consumer 按依赖上游优先启动 | Done | 单测 `reloadStopsConsumersRestartsProviderAndStartsConsumers` |
| R5-AC7：失败 record 包含 state transitions 和 failure code | Done | `DefaultJpaDomainReloadService` 失败 record；单测 `reloadFailsWhenProviderExportsRemainAfterStop` |
| R5-AC8：provider 或 consumer 启动失败时 unrelated 插件仍可工作 | Done | runtime smoke `jpaProviderIsolation`、`unrelatedPluginAlive` |
| R5-AC9：同一幂等键重复 execute 返回同一 reloadId | Done | runtime smoke `jpaReloadIdempotency` |
| R5-AC10：providerReplacementPath 非空时返回 `UNSUPPORTED_REPLACEMENT_PATH` 且不执行 | Done | 单测 `reloadRejectsProviderReplacementPathWithoutExecuting` |
| R5-AC11：provider 停止后旧 descriptor、EMF、TM、DataSource 导出 Bean 不存在 | Done | `verifyProviderExportsRemoved`；单测 `reloadFailsWhenProviderExportsRemainAfterStop` |
| R5-AC12：provider 启动失败时至少重试恢复一次，恢复失败进入人工介入状态 | Done | 单测 `reloadRetriesProviderStartOnce`；失败路径返回 `MANUAL_INTERVENTION_REQUIRED` |

## 8. R6 Runtime smoke 和样例扩展

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| R6-AC1：runtime smoke 包含 `jpaReloadPlanOnly` | Done | `RuntimeSmokeRunner` |
| R6-AC2：runtime smoke 包含 `jpaReloadSuccess` | Done | `RuntimeSmokeRunner` |
| R6-AC3：runtime smoke 包含 reload 失败隔离检查 | Done | `unrelatedPluginAlive`、`jpaProviderIsolation` |
| R6-AC4：`result.json` 输出 reload 检查项 | Done | `samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json` |
| R6-AC5：JUnit XML 输出 reload 检查项 | Done | `samples/cross-plugin-jpa/app-run/build/test-results/runtimeSmoke/TEST-runtime-smoke.xml` |
| R6-AC6：报告不泄露敏感路径、token 或完整堆栈 | Done | smoke runner 只记录检查名、状态、摘要 |
| R6-AC7：runtime smoke 包含 `jpaReloadDisabledNoMutation` | Done | `RuntimeSmokeRunner` |
| R6-AC8：runtime smoke 包含 `jpaReloadIdempotency` | Done | `RuntimeSmokeRunner` |

## 9. R7 文档、迁移指南和验收收口

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| R7-AC1：开发指南补充 JPA 运行时刷新说明 | Done | `plugin-developer-guide.md` |
| R7-AC2：JPA 集成文档补充刷新边界 | Done | `jpa-integration.md` |
| R7-AC3：HTTP 管理接口文档补充 JPA reload 接口 | Done | `plugin-http-management-api.md` |
| R7-AC4：英文翻译同步 | Done | `docs/design/en/*` 对应文档 |
| R7-AC5：验收清单按实际结果更新 | Done | 本文 |
| R7-AC6：明确 V1 是重启式刷新，不承诺生产无停顿 | Done | `jpa-runtime-refresh.md`、`jpa-integration.md`、开发指南 |

## 10. V1 完成门槛

| 门槛 | 状态 | 证据 |
| --- | --- | --- |
| V1-GATE-1：R1-R4 完成后，`PLAN_ONLY` 可独立发布 | Done | plan service、管理 plan 接口、Actuator 摘要 |
| V1-GATE-2：R5 执行模式默认仍关闭，只有显式配置才可运行 | Done | 默认 `DISABLED`；sample 显式配置 `STOP_CONSUMERS_AND_REBUILD` |
| V1-GATE-3：R5 执行模式通过单元、集成和 runtime smoke 验证 | Done | `:pf4boot-jpa-starter:test`；`:samples:cross-plugin-jpa:app-run:runtimeSmoke` |
| V1-GATE-4：失败场景不会停止 unrelated 插件 | Done | runtime smoke `unrelatedPluginAlive`、`jpaProviderIsolation` |
| V1-GATE-5：所有新增/变更文档有英文同步 | Done | `docs/design/en` 对应文档 |
| V1-GATE-6：目标模块测试通过 | Done | `:pf4boot-jpa-starter:test`、`:pf4boot-management-starter:test`、`:pf4boot-actuator:test` |
| V1-GATE-7：`.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke` 通过 | Done | runtime smoke |

## 11. 当前结论

JPA 运行时刷新 V1 已完成：默认禁用、支持 plan-only、支持显式重启式 execute、接入通用 `PluginTrafficDrainer`、管理接口和 Actuator 可观测、runtime smoke 覆盖禁用态、计划、执行、幂等、drain 成功、drain timeout 不变更和隔离。后续工作进入持久化记录、provider 包替换和更高级刷新策略。
