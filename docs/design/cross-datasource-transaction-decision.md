# 跨数据源事务决策

## 背景

当前跨插件 JPA 事务能力只支持同一个共享 `domain-id` 内的事务。一个 consumer 插件可以依赖多个 JPA domain，但 Repository 必须按包分组并显式绑定各自的 `EntityManagerFactory` 和 `TransactionManager`。已有设计多次声明：跨 domain、跨数据源原子事务不在当前阶段支持。

缺口是：当业务插件同时写入多个数据源时，框架应继续禁止本地跨数据源事务，还是提供 Saga、Outbox 或 XA/JTA 可选模块。本决策用于锁定边界，防止后续实现误把多个本地事务包装成“原子事务”。

## 目标

- 明确框架默认是否支持跨数据源原子事务。
- 给出 Saga/Outbox/XA 的边界和进入条件。
- 定义能力声明、预检和错误提示方向。
- 保持现有同 domain 事务能力不变。

## 非目标

- P6 不实现跨数据源事务。
- 不把 XA/JTA 作为核心依赖。
- 不为业务补偿流程提供通用业务语义。
- 不承诺跨数据库强一致提交。

## 现状/已有流程

| 能力 | 当前状态 |
| --- | --- |
| 同 domain 事务 | 支持，共享同一个 `JpaTransactionManager` |
| 多 domain Repository | 支持按包绑定多个 EMF/TM |
| 跨 domain 原子事务 | 明确不支持 |
| capability 预检 | 可声明 `jpa.datasource`、`jpa.consumer` 和包扫描路径 |
| 热替换/JPA 刷新 | provider 替换需停止相关 consumer，事务排空另行治理 |

## 核心约束

- 本地 `JpaTransactionManager` 只能管理一个确定 EMF。
- 多个数据源之间没有共同事务日志或两阶段提交协调器。
- 业务一致性语义无法由框架自动推断。
- Java 8、Spring Boot 2.7.x 不应被 XA 实现强绑定。
- 默认行为必须兼容历史同 domain 事务。

## 备选方案

| 方案 | 描述 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| A. 继续禁止本地跨数据源事务 | 预检或文档明确多个 TM 不提供原子提交 | 清晰、安全、无新依赖 | 业务需要自行设计最终一致性 | 推荐默认 |
| B. Saga/TCC/补偿 | 框架只提供模式指导或轻量扩展点，业务实现状态和补偿 | 适合长事务和跨系统 | 业务复杂度高，无法框架自动完成 | 推荐作为业务层模式 |
| C. Outbox/Inbox | 每个本地事务写业务数据和 outbox，通过异步投递实现最终一致 | 实现简单、可审计、适合事件驱动 | 非同步强一致，需要消息投递治理 | 推荐作为业务层模式 |
| D. XA/JTA 可选模块 | 引入事务协调器，多个 XA datasource 二阶段提交 | 可提供强一致语义 | 依赖重、性能和恢复复杂、资源必须 XA 化 | 仅未来可选模块评估 |

## 推荐结论

推荐：框架核心继续禁止本地跨数据源原子事务。Saga/Outbox 作为业务层一致性模式提供文档和 sample 方向，但不由 core 自动保证。XA/JTA 仅作为未来独立可选模块评估，不能进入 `pf4boot-core`、`pf4boot-jpa-starter` 默认依赖。

## 接口与配置草案

未来可先做策略预检，而不是事务实现：

```java
public enum CrossDatasourceTransactionPolicy {
  FORBID,
  WARN,
  ALLOW_APPLICATION_MANAGED
}
```

```java
public class TransactionCapabilityDescriptor {
  private String pluginId;
  private List<String> datasourceIds;
  private CrossDatasourceTransactionPolicy policy;
  private String consistencyPattern; // LOCAL, SAGA, OUTBOX, XA_OPTIONAL
}
```

```yaml
spring:
  pf4boot:
    transaction:
      cross-datasource-policy: FORBID # FORBID, WARN, ALLOW_APPLICATION_MANAGED
```

默认必须是 `FORBID`。`ALLOW_APPLICATION_MANAGED` 只表示框架不阻断，不表示框架提供原子性。

## capability 声明建议

```json
{
  "capabilities": {
    "requires": [
      {
        "name": "jpa.datasource",
        "attributes": {
          "datasource": "orderDs",
          "transactionRole": "LOCAL"
        }
      },
      {
        "name": "jpa.datasource",
        "attributes": {
          "datasource": "billingDs",
          "transactionRole": "LOCAL"
        }
      }
    ]
  }
}
```

当同一个事务入口声明或诊断出多个 `transactionRole=LOCAL` 的 datasource 时，`FORBID` 模式必须输出预检失败或运行时 warning。

## 状态机

Saga/Outbox 若未来提供 sample，应使用业务状态机：

```text
RECEIVED -> LOCAL_COMMITTED -> EVENT_PUBLISHED -> CONSUMED -> COMPLETED
LOCAL_COMMITTED -> PUBLISH_FAILED -> RETRYING / MANUAL_INTERVENTION
CONSUMED -> COMPENSATING -> COMPENSATED / MANUAL_INTERVENTION
```

该状态机属于业务 sample，不属于核心事务管理器。

## 时序流程

### 默认禁止路径

1. 部署预检读取 capability manifest。
2. 发现插件需要多个 datasource。
3. 若配置为 `FORBID`，输出明确错误或 warning：跨数据源原子事务不支持。
4. 插件仍可按多个独立事务方法启动，但不能声明单方法原子提交。

### Outbox 模式路径

1. 在 `orderDs` 本地事务中写订单和 outbox。
2. outbox dispatcher 发送事件。
3. `billingDs` consumer 幂等消费事件并更新账务。
4. 失败通过重试、死信或人工介入处理。

## 异常处理

| 异常 | 行为 |
| --- | --- |
| 同一方法误用多个本地 TM | 文档和预检提示，不承诺回滚一致 |
| Outbox 投递失败 | 业务重试或人工介入 |
| Saga 补偿失败 | 业务状态进入 `MANUAL_INTERVENTION` |
| XA 资源不支持 | 可选模块预检失败，不影响核心 |

## 兼容性

- 同 domain 事务保持不变。
- 多 domain Repository 绑定保持可用。
- 新策略默认 `FORBID`，只影响未来显式开启的预检/诊断。
- 不新增核心依赖。

## 回滚策略

若未来添加策略预检，可通过配置切回 `WARN` 或关闭对应 capability 检查。业务 Saga/Outbox sample 不得成为框架启动必要条件。

## 测试方案

- 同 domain 跨插件事务成功和回滚测试继续通过。
- 多 datasource 单方法原子事务在 `FORBID` 下预检失败。
- `WARN` 模式只输出诊断，不改变历史启动。
- Outbox sample 验证幂等消费、重复投递和失败重试。
- 可选 XA 模块若未来存在，必须独立测试，不纳入核心 build 必需路径。

## 风险点

| 风险 | 缓解 |
| --- | --- |
| 用户误以为多 TM 等于原子事务 | 文档、错误码、capability 诊断反复声明不支持 |
| Saga/Outbox 被误认为框架通用实现 | sample 标注业务模式，核心只提供边界说明 |
| XA 依赖污染核心 | XA 只能作为独立可选模块 |

## 进入实施规划条件

- 需要至少一个真实业务 sample 演示 Saga 或 Outbox。
- capability 预检能识别 datasource 分组。
- 管理 smoke 能展示多 datasource 预检失败或 warning。
- 若评估 XA，必须先定义独立模块、依赖和恢复策略。

## 最终决策

本专题暂不进入核心事务代码实现。框架核心继续禁止本地跨数据源原子事务；Saga/Outbox 作为业务层推荐模式；XA/JTA 仅保留为未来独立可选模块评估。
