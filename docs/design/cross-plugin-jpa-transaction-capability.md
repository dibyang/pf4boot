# 跨插件 JPA 事务能力设计（可落地执行）

## 1. 目标与非目标

### 1.1 目标

- 在不改动现有宿主核心行为的前提下，提供**可选**的跨插件事务能力。
- 没有 JPA 需求的插件不加载该能力，不受影响。
- 有 JPA 共享需求的插件通过依赖“领域能力插件”进入同一事务域。
- 插件内可按包按域管理 `Repository`，领域能力插件按包管理 `Entity`，避免混放。
- 多个数据源可作为多个域独立提供，当前阶段不支持跨域事务（跨数据库）。

### 1.2 非目标（本阶段不做）

- 跨域（XA/JTA）统一事务。
- 运行时自动刷新已启动 EMF 的 metamodel。
- 业务插件运行时向已启动的共享 EMF 动态追加 entity。
- 为现有 `pf4boot-jpa-starter` 重构为新的根架构；仅做增量兼容扩展。

## 2. 现状问题

当前每个插件有独立 Spring 上下文，JPA 默认在插件内独立创建 `EntityManagerFactory` 和 `TransactionManager`，导致：

- 共享事务边界难定义；
- 多插件共同操作同一数据源时容易出现“各管各线”的事务边界；
- 为了统一事务不得不在配置层强绑定大量上下文实现细节。

同时，`pf4boot-jpa` 与 `pf4boot-jpa-starter` 已具备稳定的 JPA 基础能力，适合复用，不应重复造轮子。

## 3. 方案总览

把“共享事务能力”拆成**域能力插件**，不是框架硬依赖：

1. 平台仍保留插件独立上下文模型。
2. `pf4boot-jpa-starter` 新增 `mode`，支持：
   - `LOCAL`：现有行为，插件独立创建 JPA 上下文。
   - `SHARED`：只使用指定领域的共享 EMF/TM，不创建本地 EMF/TM。
3. 领域能力插件（建议名：`pf4boot-jpa-domain-starter`）独立声明 `DataSource/EntityManagerFactory/TransactionManager`，并以 `PLATFORM` 级别暴露给依赖方。

## 4. 模块边界与职责

- `pf4boot-jpa`
  - 继续提供 JPA 抽象、持久化基础能力。
  - 不改动其公共行为边界。
- `pf4boot-jpa-starter`
  - 新增 `mode` 配置分支；
  - `SHARED` 下不再创建本地 EMF/TM；
  - `SHARED` 下在插件本地上下文注册指向父上下文共享 EMF/TM 的占位 BeanDefinition，保证 Spring Data Repository 能按名称解析；
  - 支持 `additional-domains`，一个 consumer 插件可为多个共享 domain 注册本地 EMF/TM BeanDefinition，并由不同 Repository 包显式绑定；
  - 启动时读取 `domain.{id}.descriptor`，确认 domain ready 且 EMF/TM 名称与绑定一致；
  - 排除 `DataSourceAutoConfiguration` 和 `JpaRepositoriesAutoConfiguration`，数据源由本地已有 Bean 或领域能力插件提供，Repository 由业务插件显式配置；
  - 补充对共享域参数校验和错误码。
- `pf4boot-jpa-domain-starter`（新增）
  - 提供可选的域级能力实现。
  - 插件本地创建：
    - `DataSource`
    - `EntityManagerFactory`
    - `PlatformTransactionManager` / `JpaTransactionManager`
  - 通过 `DomainJpaPlatformExporter` 导出到当前插件 `group` 对应的 `PLATFORM` 上下文：
    - `domain.{domain-id}.dataSource`
    - `domain.{domain-id}.entityManagerFactory`
    - `domain.{domain-id}.transactionManager`
    - `domain.{domain-id}.descriptor`
  - 导出发生在领域插件 Spring 上下文完成单例初始化后；插件上下文关闭时反向注销。
  - 若导出过程中出现冲突或异常，已导出的 Bean 必须回滚注销，避免平台上下文残留半初始化域。
  - 排除 Spring Boot 的 DataSource/JPA Repository 自动配置，域 starter 自己显式创建域级 DataSource/EMF/TM。
  - 默认 `ddl-auto=none`，`entity-packages` 为空时按 `PJF-005` fail-fast；包不可见或扫描失败时按 `PJF-008` fail-fast。
- `pf4boot-core`
  - 不新增 JPA 领域运行时逻辑，仅按现有依赖加载机制生效。
- `pf4boot-api`
  - 优先不新增新公共 API；如需则仅补充配置常量与文档说明。
- `samples/cross-plugin-jpa`
  - 提供可直接落地的复杂演示示例、运行时打包项目和迁移指南。

### 4.1 核心约束：实体归属与扫描边界

同域共享事务依赖同一个 `JpaTransactionManager`，而 `JpaTransactionManager` 绑定一个确定的 `EntityManagerFactory`。因此第一阶段必须锁定以下边界：

- 领域能力插件负责创建共享 EMF/TM，并在 EMF 创建前完成该域 entity 扫描。
- 业务插件可以定义自己的 Repository 扫描路径，但 Repository 使用的 entity 必须已经被领域 EMF 管理。
- 业务插件应显式使用 `@EnableJpaRepositories` 声明 Repository 包和 EMF/TM 引用，不依赖 Spring Boot Repository 自动扫描。
- 共享域 entity 类必须放在领域能力插件、领域共享库，或其它对领域能力插件类加载器可见的位置。
- 业务插件不能在启动后把新 entity 动态加入已启动的共享 EMF；如需新增 entity，必须更新领域能力插件或领域共享库并重启该领域。
- 插件内多域访问时，按域拆分 Repository 包和事务管理器引用；跨域原子事务仍不支持。

## 5. 配置契约

### 5.1 领域能力插件侧（Provider）

`plugin.properties`：

```properties
plugin.id=order-jpa-domain
plugin.class=net.xdob.demo.domain.order.OrderJpaDomainPlugin
plugin.dependencies=
```

`application.yml`：

```yaml
pf4boot:
  plugin:
    jpa:
      domain:
        id: order
        entity-packages:
          - net.xdob.demo.domain.order.entity
        datasource:
          url: jdbc:h2:mem:orderdb
          username: sa
          password:
        ddl-auto: none
```

### 5.2 业务插件侧（Consumer）

`plugin.properties`：

```properties
plugin.id=order-service
plugin.class=net.xdob.demo.order.OrderServicePlugin
plugin.dependencies=order-jpa-domain
```

`application.yml`：

```yaml
pf4boot:
  plugin:
    jpa:
      enabled: true
      mode: SHARED
      domain-id: order
      # 不配置也可使用默认命名
      entity-manager-factory-ref: domain.order.entityManagerFactory
      transaction-manager-ref: domain.order.transactionManager
```

推荐默认命名（支持显式覆盖）：

- `domain.{domain-id}.dataSource`
- `domain.{domain-id}.entityManagerFactory`
- `domain.{domain-id}.transactionManager`

## 6. 插件内多域实体与仓储扫描

为保证扫描边界清晰，领域能力插件负责 entity 扫描；业务插件负责 Repository 扫描并显式绑定到对应领域的 EMF/TM。

领域能力插件侧 entity 包示例：

```yaml
pf4boot:
  plugin:
    jpa:
      domain:
        id: order
        entity-packages:
          - net.xdob.demo.domain.order.entity
```

业务插件侧 Repository 包示例：

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.demo.order.domain.order.repository",
    entityManagerFactoryRef = "domain.order.entityManagerFactory",
    transactionManagerRef = "domain.order.transactionManager"
)
public class OrderDomainJpaConfig {}
```

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.demo.order.domain.report.repository",
    entityManagerFactoryRef = "domain.report.entityManagerFactory",
    transactionManagerRef = "domain.report.transactionManager"
)
public class ReportDomainJpaConfig {}
```

规则：

- 一个领域能力插件负责一个 `domain-id` 的 entity 扫描；
- 业务插件一个域一个 `@EnableJpaRepositories`；
- `basePackages` 只应覆盖单一业务域的 Repository；
- Repository 引用的 entity 必须已被领域能力插件的共享 EMF 管理；
- 业务代码在 `@Transactional` 上显式指定对应 TM（生产环境推荐显式配置）。

## 7. 运行时行为

### 7.1 启动链路

1. 宿主启动并解析插件依赖图。
2. 领域能力插件先于其依赖插件启动；
   - 校验 `pf4boot.plugin.jpa.domain.id`；
   - 校验并解析 `entity-packages`；
   - 创建本地域级 `DataSource/EMF/TM`；
   - 向当前插件 `group` 的平台上下文导出 `domain.{id}.*` Bean 和 descriptor；
   - 若创建或导出失败，按 `PJF-004/PJF-005/PJF-008` fail-fast，并回滚已导出的 Bean。
3. 业务插件进入 `STARTING`：
   - `mode=LOCAL`：保持现有本地 JPA 初始化流程；
    - `mode=SHARED`：查找 `domain-id` 和 `additional-domains` 对应能力域；
      - descriptor 缺失或未 ready 则记录 `PJF-007` 并阻断该插件启动。
      - 找到则注入共享 EMF/TM，不再创建本地 EMF/TM。
4. 同域插件可在同一个事务管理器上协作。

### 7.2 失败隔离与回退

- 能力插件启动失败时，依赖它的插件应连同依赖链失败，宿主其它独立插件继续启动。
- 插件可通过切回 `mode=LOCAL` 快速回退；或移除 `plugin.dependencies`。
- 已失败插件可在修复能力插件后重启依赖链。

## 8. 示例

### 8.1 用例 A：单域共享（推荐路径）

```java
@Transactional(transactionManager = "domain.order.transactionManager")
public void createOrder(...) {
  // 与依赖 order-domain 的插件协作时可共享同一事务
}
```

### 8.2 用例 B：同一插件内双域（暂不支持跨域事务）

```yaml
pf4boot:
  plugin:
    jpa:
      plugins:
        report-workflow:
          mode: SHARED
          domain-id: order
          additional-domains:
            - domain-id: report
```

```java
@Transactional(transactionManager = "domain.order.transactionManager")
public void updateOrder() {}

@Transactional(transactionManager = "domain.report.transactionManager")
public void updateReport() {}
```

### 8.3 迁移路径（本地 -> 共享）

1. 保持 `mode=LOCAL` 与原有 `EntityManagerFactory`，确认功能正常；
2. 新建领域能力插件并配置好 `domain-id`；
3. 业务插件加 `plugin.dependencies` 与 `mode=SHARED`；
4. 按域逐步替换 `@Transactional` 的 TM 引用；
5. 删除旧本地重复 datasource/EMF 配置。

完整迁移步骤见 [cross-plugin-jpa-transaction-migration.md](cross-plugin-jpa-transaction-migration.md)。

## 9. 错误码与可观测性

| 错误码 | 场景 | 建议处理 |
| --- | --- | --- |
| `PJF-001` | SHARED 模式未配置 `domain-id` | 快速失败，提示补齐 `domain-id` |
| `PJF-002` | 共享域 EMF 缺失 | 快速失败，提示依赖插件 ID 与默认命名 |
| `PJF-003` | 共享域 TransactionManager 缺失 | 快速失败，提示修正 `transaction-manager-ref` |
| `PJF-004` | 共享域启动失败（如 provider 缺少 `domain.id`、数据源配置无效、平台导出冲突） | 标记插件链故障，修正能力插件配置后重启依赖链 |
| `PJF-005` | 领域 entity 包配置为空 | 能力插件启动失败，提示补齐 `entity-packages` |
| `PJF-007` | domain descriptor 缺失、未 ready 或与绑定不一致 | 阻断 consumer 启动，提示 provider、domain-id、descriptor 和 EMF/TM 名称 |
| `PJF-008` | 领域 entity 包不可见、扫描失败或未扫描到 managed entity | 能力插件启动失败或第一阶段 warning，提示修正 model 模块依赖、`entity-packages` 或类加载依赖 |

日志要求：

- 启动日志记录 `domain-id`、能力插件 ID、TM/EMF 名称；
- 失败日志明确写出“缺失原因 + 下一步动作”。

## 10. 验收标准

见验收文档：

- [archive/cross-plugin-jpa-transaction-capability-acceptance.md](archive/cross-plugin-jpa-transaction-capability-acceptance.md)

## 11. 规划（单独文档）

- [archive/cross-plugin-jpa-transaction-capability-plan.md](archive/cross-plugin-jpa-transaction-capability-plan.md)

## 12. 风险与对策

- `EntityManagerFactory` 名称/域名配置错误 -> 提供标准模板 + 校验异常码 + 启动提示；
- 多域时扫描配置遗漏 -> 规则化 `@EntityScan` 与 `@EnableJpaRepositories` 模板；
- 团队理解成本上升 -> 提供“本地->共享”迁移清单。

## 13. 决策结论（锁定）

- 领域能力插件负责创建并导出 `DataSource/EMF/TM`（当前建议）；
- 不引入统一 `@EnablePf4bootJpaSharedDomain` 注解，配置驱动为主；
- 首轮不做跨域原子事务，只做同域共享事务；
- 共享域 entity 由领域能力插件或领域共享库提供，业务插件只在本阶段绑定 Repository；
- 与现有 `DynamicMetadata.sync()` 行为保持一致，不支持运行时扩展同步。
