# JPA 运行时刷新 Drain SPI 验收清单

## 1. 使用说明

本文跟踪 [jpa-runtime-refresh-drain-spi-plan.md](jpa-runtime-refresh-drain-spi-plan.md) 中 D0-D7 的验收状态。

状态值：

- `Planned`：已规划，尚未实现。
- `In Progress`：正在实现。
- `Done`：已完成并有验证证据。
- `Blocked`：受外部条件阻塞。

## 2. D0 设计补齐

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| D0-AC1：设计说明复用 `PluginTrafficDrainer` 而不是新增平行 SPI | Done | `jpa-runtime-refresh-drain-spi.md` |
| D0-AC2：设计包含字段、构造器和 JSON 示例 | Done | `jpa-runtime-refresh-drain-spi.md` 5.6 |
| D0-AC3：设计包含 coordinator 伪代码和自动配置落点 | Done | `jpa-runtime-refresh-drain-spi.md` 5.6.3、5.6.4 |
| D0-AC4：设计包含测试和验收要求 | Done | `jpa-runtime-refresh-drain-spi.md` 9 |
| D0-AC5：英文翻译同步 | Done | `en/jpa-runtime-refresh-drain-spi.md` |

## 3. D1 公共模型扩展

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| D1-AC1：新增 `JpaDomainDrainerPhase` | Done | `pf4boot-jpa/src/main/java/net/xdob/pf4boot/jpa/reload/JpaDomainDrainerPhase.java` |
| D1-AC2：新增 `JpaDomainDrainerResult` | Done | `JpaDomainDrainerResult.java` |
| D1-AC3：`JpaDomainDrainReport` 保留旧构造器并新增完整构造器 | Done | `JpaDomainDrainReport` 两个构造器 |
| D1-AC4：list 字段不可变且 null 转空集合 | Done | `JpaDomainDrainReport.copy/copyStrings` |
| D1-AC5：`JpaDomainReloadRecord` 新增 `drainReport` 且保留旧构造器 | Done | `JpaDomainReloadRecord` 重载构造器和 `getDrainReport()` |
| D1-AC6：`:pf4boot-jpa:compileJava` 通过 | Done | `.\gradlew.bat :pf4boot-jpa:compileJava` |

## 4. D2 Drain coordinator

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| D2-AC1：coordinator 能注入所有 `PluginTrafficDrainer` | Done | `JpaDomainReloadDrainCoordinator`；`JpaDomainReloadAutoConfiguration` |
| D2-AC2：impact plugin ids 为 stopOrder + provider，去重且顺序稳定 | Done | `JpaDomainReloadDrainCoordinator.impactPluginIds`；`JpaDomainReloadDrainCoordinatorTest.noDrainerContinuesForCompatibility` |
| D2-AC3：无 drainer 且 `require-drainer=false` 返回 accepted + warning | Done | `noDrainerContinuesForCompatibility` |
| D2-AC4：无 drainer 且 `require-drainer=true` 返回 `DRAIN_REJECTED` | Done | `noDrainerRejectsWhenStrictModeEnabled` |
| D2-AC5：begin 异常映射 `DRAIN_REJECTED` 并反向 end 已 begin drainer | Done | `beginFailureEndsAlreadyBegunDrainers` |
| D2-AC6：await false 映射 `DRAIN_TIMEOUT` | Done | `awaitFalseReturnsTimeoutAndEndsDrainers` |
| D2-AC7：await 异常/中断映射 `DRAIN_REJECTED`，中断位被恢复 | Done | `awaitExceptionReturnsRejectedAndEndsDrainers`；中断恢复逻辑在 coordinator 中实现 |
| D2-AC8：多 drainer 共享总 timeout | Done | `JpaDomainReloadDrainCoordinator.drain` 使用 deadline/remaining；`successfulDrainEndsLaterByPlanId` 覆盖多 drainer |

## 5. D3 Reload service 接入

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| D3-AC1：`DRAINING` 阶段调用 coordinator | Done | `DefaultJpaDomainReloadService` 调用 `drainCoordinator.drain` |
| D3-AC2：drain 失败不调用 `stopPlugin/startPlugin` | Done | `DefaultJpaDomainReloadServiceTest.reloadDoesNotStopPluginsWhenDrainTimesOut` |
| D3-AC3：drain 成功后 stop/start 顺序保持 V1 行为 | Done | `reloadStopsConsumersRestartsProviderAndStartsConsumers` |
| D3-AC4：成功路径调用 `endDrain` | Done | `reloadEndsDrainAfterSuccess` |
| D3-AC5：stop/start/health 失败路径仍调用或记录 `endDrain` | Done | `reloadEndsDrainWhenProviderStartFails` |
| D3-AC6：失败 record 包含 `drainReport`、failure code 和 transitions | Done | `reloadDoesNotStopPluginsWhenDrainTimesOut` |

## 6. D4 管理接口和 Actuator 摘要

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| D4-AC1：reload record 查询能返回 `drainReport` | Done | `JpaDomainReloadRecord.getDrainReport()`；管理接口返回 record DTO |
| D4-AC2：Actuator `pf4bootjpareload` 输出最近 drain 摘要 | Done | `Pf4bootJpaReloadEndpoint.summary`；`Pf4bootJpaReloadEndpointTest.summaryReturnsLatestDrainSummary` |
| D4-AC3：无历史记录时 Actuator 不抛异常 | Done | `summaryReturnsEmptyDrainFieldsWhenNoRecordExists` |
| D4-AC4：输出不包含堆栈、绝对路径、token 或敏感请求明文 | Done | Actuator 只输出 reloadId、drain 布尔/耗时/code/count 摘要 |

## 7. D5 单元和集成测试

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| D5-AC1：no-drainer 兼容和严格模式均有测试 | Planned | 待实现 |
| D5-AC2：begin/await/end 异常路径有测试 | Planned | 待实现 |
| D5-AC3：timeout 和剩余预算有测试 | Planned | 待实现 |
| D5-AC4：drain 失败无 stop/start 有测试 | Planned | 待实现 |
| D5-AC5：stop/start 失败仍 endDrain 有测试 | Planned | 待实现 |
| D5-AC6：`:pf4boot-jpa-starter:test` 通过 | Planned | 待验证 |

## 8. D6 Sample runtime smoke

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| D6-AC1：runtime smoke 包含 `jpaReloadDrainSuccess` | Planned | 待实现 |
| D6-AC2：runtime smoke 包含 `jpaReloadDrainTimeoutNoMutation` | Planned | 待实现 |
| D6-AC3：runtime smoke 包含 `actuatorJpaReloadDrainSummary` | Planned | 待实现 |
| D6-AC4：`result.json` 和 JUnit XML 输出 drain 检查项 | Planned | 待实现 |
| D6-AC5：drain timeout 后 workflow/unrelated 插件仍可访问 | Planned | 待实现 |
| D6-AC6：`:samples:cross-plugin-jpa:app-run:runtimeSmoke` 通过 | Planned | 待验证 |

## 9. D7 文档和验收收口

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| D7-AC1：JPA refresh 主设计更新 drain 语义 | Planned | 待实现 |
| D7-AC2：JPA 集成文档更新 drain 说明 | Planned | 待实现 |
| D7-AC3：开发指南更新 drain 使用和风险说明 | Planned | 待实现 |
| D7-AC4：英文翻译同步 | Planned | 待实现 |
| D7-AC5：验收清单按实际结果更新 | Planned | 待实现 |
| D7-AC6：`git diff --check` 和 U+FFFD 检查通过 | Planned | 待验证 |

## 10. 当前结论

Drain SPI 当前处于设计和规划完成、实现未开始状态。建议按 D1-D7 顺序实施，D3 之后必须运行 `:pf4boot-jpa-starter:test`，D6 之后必须运行 runtime smoke。
