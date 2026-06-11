# 跨插件 JPA 事务现状改进方案

## 1. 背景

当前跨插件 JPA 事务能力已经完成第一阶段落地：

- `pf4boot-jpa-starter` 支持 `LOCAL / SHARED` 模式。
- `pf4boot-jpa-domain-starter` 可以由领域能力插件创建并导出 `DataSource / EntityManagerFactory / TransactionManager`。
- 依赖同一 `domain-id` 的业务插件可以显式绑定同一个 `EntityManagerFactory` 和 `TransactionManager`。
- 非依赖该领域能力插件的其它插件不应被该领域失败影响。

这套方向是合理的，但当前实现仍偏“最小可用”。如果要进入可长期维护的框架能力，还需要补齐配置粒度、实体可见性、生命周期诊断、事务边界规范和验收闭环。

## 2. 目标

- 保留现有 `LOCAL / SHARED` 兼容行为。
- 让一个应用中多个 JPA 插件可以按插件或按 repository 包明确绑定不同 `domain-id`。
- 让共享 EMF 的 entity 来源和类加载可见性变成可验证约束。
- 提供更清晰的 domain 就绪状态、诊断信息和错误码。
- 明确跨插件事务只支持同 domain 事务，不支持跨 domain 原子事务。
- 补齐运行时 smoke、失败注入和插件依赖隔离验收。

## 3. 非目标

- 不引入 JTA/XA。
- 不支持跨数据源原子事务。
- 不支持 consumer 插件在 provider EMF 启动后动态追加 entity。
- 不改变现有 `mode=LOCAL` 默认兼容行为。
- 不在本方案中处理插件热替换部署；热替换应单独设计。

## 4. 当前能力与不足

| 领域 | 当前状态 | 主要不足 |
| --- | --- | --- |
| 模式选择 | `pf4boot.plugin.jpa.mode=LOCAL/SHARED` | 配置偏全局，多个 JPA 插件同时存在时容易互相影响 |
| domain provider | provider 创建并导出 `domain.{id}.*` | 缺少 domain descriptor/ready 状态，消费者只能靠 Bean 名称判断 |
| entity 扫描 | provider 配置 `entity-packages` | 实体归属和 model jar 可见性没有制度化验证 |
| Repository 绑定 | consumer 显式 `@EnableJpaRepositories` | 多 domain 场景下重复配置多，错误时诊断不足 |
| 事务边界 | 业务代码显式指定 `transactionManager` | `REQUIRES_NEW`、自调用、导出代理对象等约束缺少文档和测试 |
| 故障隔离 | 依赖链失败、无关插件应继续工作 | 缺少真实运行 smoke 和可观测状态 |
| 示例 | 最小 demo 可用 | 复杂示例职责边界仍需迁移到独立 sample |

## 5. 改进方案

### 5.1 插件级 JPA 绑定配置

保留当前配置：

```yaml
pf4boot:
  plugin:
    jpa:
      mode: SHARED
      domain-id: demo
```

新增推荐配置层，允许按插件声明 JPA 绑定：

```yaml
pf4boot:
  plugin:
    jpa:
      plugins:
        plugin-user-book-service:
          mode: SHARED
          domain-id: demo
        plugin-workflow:
          mode: SHARED
          domain-id: demo
```

解析优先级建议：

1. 当前插件 ID 对应的 `pf4boot.plugin.jpa.plugins.<plugin-id>`。
2. 当前已有的 `pf4boot.plugin.jpa.mode/domain-id`。
3. 默认 `LOCAL`。

这样可以保持旧配置可用，同时为多个 JPA 插件共存提供清晰入口。

### 5.2 Domain Descriptor 与就绪状态

provider 除导出以下 Bean：

- `domain.{domain-id}.dataSource`
- `domain.{domain-id}.entityManagerFactory`
- `domain.{domain-id}.transactionManager`

还应导出只读描述对象：

- `domain.{domain-id}.descriptor`

建议字段：

| 字段 | 含义 |
| --- | --- |
| `domainId` | 领域 ID |
| `providerPluginId` | 提供该 domain 的插件 ID |
| `entityPackages` | provider 创建 EMF 时使用的 entity 包 |
| `dataSourceBeanName` | 导出的 DataSource Bean 名称 |
| `entityManagerFactoryBeanName` | 导出的 EMF Bean 名称 |
| `transactionManagerBeanName` | 导出的 TM Bean 名称 |
| `ready` | domain 是否完成初始化并导出 |
| `createdAt` | domain 创建时间 |

consumer 在 `SHARED` 模式启动时优先检查 descriptor，错误信息应包含 `domain-id`、consumer 插件 ID、provider 插件 ID 和缺失 Bean 名称。

### 5.3 Entity 与 model jar 可见性约束

共享 EMF 的 entity 必须由 provider 在创建 EMF 前可见。推荐结构：

```text
model-user-book        -> 只放 entity/value object/enum
model-workflow-audit   -> 只放 entity/value object/enum
plugin-demo-jpa-domain -> 依赖 model 并扫描 model 包
service/workflow       -> 定义 Repository 和 service
```

硬性规则：

- provider 不定义业务 entity。
- provider 不定义业务 Repository。
- consumer Repository 引用的 entity 必须来自 provider 可见的 model 模块。
- provider 启动时验证 `entity-packages` 可从当前插件 classpath 解析；包不可见或扫描失败时使用明确错误码失败。
- 第一阶段如果包可见但没有扫描到 managed entity，先输出明确错误码 warning，待 sample 和生产反馈稳定后再评估是否切为 fail-fast。

### 5.4 Repository 与多 domain 绑定

一个 consumer 插件可依赖多个 domain，但必须按包拆分 Repository：

```yaml
pf4boot:
  plugin:
    jpa:
      plugins:
        sample-workflow:
          mode: SHARED
          domain-id: order
          additional-domains:
            - domain-id: audit
```

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.sample.order.repository",
    entityManagerFactoryRef = "domain.order.entityManagerFactory",
    transactionManagerRef = "domain.order.transactionManager"
)
public class OrderJpaConfig {
}
```

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.sample.audit.repository",
    entityManagerFactoryRef = "domain.audit.entityManagerFactory",
    transactionManagerRef = "domain.audit.transactionManager"
)
public class AuditJpaConfig {
}
```

约束：

- 不同 domain 的 Repository 不混包。
- 主 `domain-id` 和 `additional-domains` 都会在 consumer 本地注册 EMF/TM BeanDefinition，并在启动时校验 descriptor ready。
- 一个 `@Transactional` 方法默认只绑定一个 TM。
- 跨 domain 编排必须显式走补偿、outbox 或 saga，不承诺原子提交。

### 5.5 事务代理边界规范

必须写入开发规范和示例：

- `@Transactional` 方法必须经 Spring 代理调用才生效。
- `REQUIRES_NEW` 不能依赖同类自调用。
- 跨插件导出的 service 应是 Spring 管理的代理对象。
- Controller/Workflow 插件不要直接注入其它插件内部 Repository。
- 共享 TM 的事务方法应显式声明 `transactionManager`，避免多 TM 时误绑定默认事务管理器。

### 5.6 诊断、错误码与日志

保留已有 `PJF-001` 到 `PJF-005`，并建议新增：

| 错误码 | 场景 | 建议提示 |
| --- | --- | --- |
| `PJF-006` | 指定插件级 JPA 绑定无法解析 | 输出 plugin-id、mode、domain-id |
| `PJF-007` | domain descriptor 缺失或未 ready | 输出 consumer plugin、domain-id、候选 provider |
| `PJF-008` | entity package 不可见、扫描失败或未扫描到 managed entity | 输出 provider plugin、domain-id、entity-packages |
| `PJF-009` | domain Bean 导出冲突 | 输出冲突 Bean 名称、已有 provider、新 provider |
| `PJF-010` | 多 domain Repository 绑定冲突 | 输出 repository package、EMF/TM 引用 |

启动日志至少包含：

- provider plugin ID
- domain ID
- entity packages
- exported bean names
- consumer plugin ID
- resolved EMF/TM

### 5.7 验收闭环

需要补齐以下验收：

- 编译和插件打包。
- provider 包内容检查。
- consumer 不直接携带 provider-only 职责。
- 启动后 HTTP smoke。
- 正常提交、强制异常回滚、`REQUIRES_NEW` 代理边界。
- provider 缺失、provider 启动失败、provider 恢复。
- 无关插件在 provider 失败时仍可启动和服务。

## 6. 兼容性

- 旧的 `pf4boot.plugin.jpa.mode/domain-id` 继续有效。
- 默认 `LOCAL` 行为不变。
- 新增 descriptor 和插件级绑定属于增强能力，不应影响未启用 JPA 的插件。
- 错误码新增只提升诊断，不改变成功路径。
- 若 provider 增加 entity package 验证，可能让历史上“配置错误但未被发现”的场景更早失败，这是预期的 fail-fast 行为。

## 7. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| 插件级配置增加复杂度 | 配置学习成本上升 | 旧配置保留，新配置只在多插件/多 domain 时推荐 |
| entity package 验证误判 | provider 被错误阻断 | 第一阶段只做 warning + 测试验证，第二阶段再 fail-fast |
| descriptor 与 Bean 状态不一致 | consumer 误判 ready | descriptor 只在全部 Bean 导出成功后注册，失败时回滚 |
| 多 domain 被误解为跨库事务 | 业务一致性预期错误 | 文档和示例持续声明不支持跨 domain 原子事务 |
| 事务代理边界被误用 | 回滚行为与预期不符 | 示例和测试覆盖 self-invocation 反例与正确写法 |

## 8. 推荐结论

- 保留当前 provider 插件 + `SHARED` consumer 的主架构。
- 优先补“插件级绑定、domain descriptor、entity 可见性验证、事务代理规范、验收闭环”。
- 暂不引入 JTA/XA。
- 暂不把热替换部署混入跨插件事务方案。
