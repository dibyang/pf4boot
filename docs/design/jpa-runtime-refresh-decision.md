# JPA 运行时刷新决策

## 背景

`pf4boot-jpa-starter` 已支持插件本地 `LOCAL` JPA 和共享 `SHARED` JPA domain；`pf4boot-jpa-domain-starter` 可以由领域能力插件创建并导出 `DataSource`、`EntityManagerFactory`、`TransactionManager` 和 domain descriptor。当前 `DynamicMetadata.sync()` 明确不支持把运行时新增 entity 同步到已创建的 Hibernate metamodel。

缺口是：当领域能力插件的 entity 包、model jar、Repository 绑定或数据源配置变化时，框架是否应支持“运行时刷新 JPA metamodel/EMF”。该问题影响类加载、事务排空、Repository 代理、连接池释放和热替换部署边界，不能在实现阶段临时决定。

## 目标

- 明确是否支持在线刷新 Hibernate metamodel。
- 明确未来若要重建共享 JPA domain，应以什么粒度停启插件。
- 给出未来可能的接口、配置、状态机和验证要求。
- 保持历史插件默认行为不变。

## 非目标

- 本阶段不实现 JPA 运行时刷新。
- 不支持在已启动 EMF 中动态追加或删除 entity。
- 不承诺不中断的 JPA provider 在线替换。
- 不引入 Hibernate 私有 SPI 作为稳定公共契约。
- 不处理跨数据源事务；该问题由 `cross-datasource-transaction-decision.md` 决策。

## 现状/已有流程

| 领域 | 当前行为 |
| --- | --- |
| entity 扫描 | `PluginJPAStarter` 创建 EMF 时扫描 `@EntityScan`、auto-configuration packages 和插件主类包 |
| 共享 domain | provider 插件通过 `Pf4bootJpaDomainStarter` 创建并导出 `domain.{id}.*` Bean |
| consumer 绑定 | consumer 通过 `mode=SHARED` 和显式 `@EnableJpaRepositories` 绑定共享 EMF/TM |
| 动态元数据 | `DynamicMetadata.sync()` 明确失败，不更新 Hibernate metamodel |
| 热替换 | provider 替换前应停止依赖它的 JPA consumer，并确认没有在途事务 |

## 核心约束

- Java 8、Spring Boot 2.7.x、Hibernate 5.6.x 兼容。
- `EntityManagerFactory` 创建后，Repository 代理、metamodel、事务管理器和连接池已经形成一组一致运行时资源。
- 共享 domain provider 的类加载器必须能看到全部 entity model。
- consumer Repository 不能在 provider EMF 创建后要求追加新 entity。
- 任何重建 EMF/TM 的流程必须先排空事务并停止相关 consumer。

## 备选方案

| 方案 | 描述 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| A. 禁止刷新，只允许重启 domain plugin | 继续保持当前边界，新增 entity/model 后重启 provider 及依赖 consumer | 最安全、最少运行时复杂度、兼容当前实现 | 操作上需要停启依赖链，自动化程度低 | 保留为默认行为 |
| B. 停止相关 consumer 后重建 domain EMF/TM | 由框架编排停止 consumer，排空事务，关闭旧 EMF/TM，重建 provider domain，再启动 consumer | 可作为未来托管能力，边界清晰，能复用热替换状态机 | 需要生命周期锁、事务排空、失败回滚和资源泄漏验证 | 推荐作为未来候选 |
| C. 在线刷新 Hibernate metamodel | 不停止 consumer，尝试通过 Hibernate 内部结构更新 managed entity | 理论中断最小 | Hibernate 私有实现风险高，Repository 代理和缓存一致性难保证，难以验证泄漏 | 拒绝 |

## 推荐结论

推荐：第一阶段继续禁止在线刷新；未来若需要框架托管刷新，仅评估方案 B“停止相关 consumer 后重建 domain EMF/TM”。方案 C 在线刷新 Hibernate metamodel 被拒绝，除非未来有稳定公开 SPI、完整泄漏验证和明确版本绑定策略。

## 接口与配置草案

仅作为未来实现草案，不在 P6 直接编码：

```java
public interface JpaDomainReloadService {
  JpaDomainReloadPlan planReload(String domainId);

  JpaDomainReloadRecord reload(JpaDomainReloadPlan plan);

  JpaDomainReloadRecord rollback(String reloadId);
}
```

```java
public class JpaDomainReloadPlan {
  private String domainId;
  private String providerPluginId;
  private List<String> consumerPluginIds;
  private boolean transactionsDrained;
  private List<String> warnings;
}
```

```yaml
spring:
  pf4boot:
    plugin:
      jpa:
        domain-reload-mode: DISABLED # DISABLED, PLAN_ONLY, STOP_CONSUMERS_AND_REBUILD
        domain-reload-timeout: 60s
```

默认必须是 `DISABLED`。`PLAN_ONLY` 只输出影响范围，不改变运行时。

## 数据结构

| 类型 | 字段 | 说明 |
| --- | --- | --- |
| `JpaDomainReloadPlan` | `domainId`、`providerPluginId`、`consumerPluginIds`、`warnings` | 重建计划 |
| `JpaDomainReloadRecord` | `reloadId`、`state`、`startedAt`、`updatedAt`、`errorCode` | 可审计记录 |
| `JpaDomainDrainReport` | `activeTransactions`、`activeConnections`、`timeout` | 事务/连接排空摘要 |

## 状态机

```text
PLANNED -> DRAINING -> STOPPING_CONSUMERS -> CLOSING_DOMAIN
  -> REBUILDING_DOMAIN -> STARTING_CONSUMERS -> HEALTH_CHECKING -> SUCCEEDED
任一执行态失败 -> ROLLING_BACK -> ROLLED_BACK / MANUAL_INTERVENTION
```

非法转换必须拒绝并记录。

## 时序流程

1. 解析 `domainId`，读取 domain descriptor。
2. 计算依赖该 domain 的 consumer 插件。
3. 进入 `DRAINING`，拒绝新事务刷新请求，等待已有事务结束。
4. 按依赖顺序停止 consumer。
5. 关闭旧 EMF/TM/DataSource 并做资源诊断。
6. 重建 provider domain。
7. 启动 consumer，并执行 Repository/事务/HTTP smoke。
8. 失败时恢复旧 domain 和旧 consumer 启动状态。

## 异常处理

| 异常 | 行为 |
| --- | --- |
| 有在途事务 | `PLAN_ONLY` 输出 warning；执行模式等待，超时进入失败或回滚 |
| consumer 停止失败 | 停止流程中断，保持旧 domain，不重建 |
| EMF 重建失败 | 回滚旧 EMF/TM/DataSource，consumer 不启动或恢复旧状态 |
| 资源泄漏 | 输出诊断，执行模式可按配置进入 `MANUAL_INTERVENTION` |

## 兼容性

- 默认 `DISABLED`，历史插件无行为变化。
- `mode=LOCAL` 插件不受共享 domain reload 影响。
- 未来实现只作为高层编排能力，不改变 `DynamicMetadata.sync()` 当前失败语义。

## 回滚策略

未来实现必须在关闭旧 domain 前保存 provider 插件版本、旧 descriptor、旧 bean 名称、consumer 启动状态和健康检查结果。回滚失败必须进入人工介入，保留旧/新 domain 现场摘要。

## 灰度/迁移

1. 先实现 `PLAN_ONLY`，只输出影响范围。
2. 在 sample 中验证停止 consumer 后重建 domain。
3. 只对显式配置的 domain 开启执行模式。
4. 最后评估是否纳入热替换部署编排。

## 测试方案

- `.\gradlew.bat :pf4boot-jpa-domain-starter:test`
- `.\gradlew.bat :pf4boot-jpa-starter:test`
- provider 重建失败回滚测试。
- consumer 停启顺序测试。
- 事务中刷新拒绝或等待超时测试。
- `samples/cross-plugin-jpa` runtime smoke。

## 风险点

| 风险 | 缓解 |
| --- | --- |
| Hibernate metamodel 不一致 | 拒绝在线刷新，只重建 EMF |
| 事务未排空 | 增加 drain 状态、超时和诊断 |
| classloader 泄漏 | 复用生命周期清理报告和弱引用辅助检查 |
| provider/consumer 依赖顺序错误 | 复用 PF4J 依赖图和热替换影响范围计算 |

## 进入实施规划条件

- P5 runtime smoke 已稳定通过。
- 生命周期清理诊断能覆盖共享 bean、Web mapping、JPA domain descriptor。
- 可以识别依赖某个 `domainId` 的 consumer 列表。
- 有可重复的 provider 重建失败注入测试。

## 最终决策

本专题暂不进入代码实现。框架继续禁止在线刷新 Hibernate metamodel；未来如进入实现，仅按“停止相关 consumer 后重建 domain EMF/TM”的托管编排路径推进。
