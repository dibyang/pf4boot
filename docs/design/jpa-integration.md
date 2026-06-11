# JPA 集成

## 问题

部分插件需要 JPA repository 和 entity，但非 JPA 插件不应该承担相关初始化成本，也不应该意外初始化 `EntityManagerFactory`。因此插件 JPA 支持是显式启用且插件本地化的。

## 模块

- `pf4boot-jpa`：提供 `Pf4bootJpaPersistenceProvider`，为 Hibernate 合并 managed class names 和 managed packages。
- `pf4boot-jpa-starter`：提供 `PluginJPAStarter`，作为插件侧 Spring Boot 自动配置类。
- `pf4boot-jpa-domain-starter`：提供 `Pf4bootJpaDomainStarter`，作为领域能力插件侧的共享 DataSource/EMF/TM 创建与平台导出模块。
- `pf4boot-api`：声明 `DynamicMetadata` 和 `DynamicMetadataSupport`；当前只记录候选 entity class，不支持运行时同步到已创建的 JPA metamodel。

## Plugin Starter 行为

`PluginJPAStarter` 标注了 `@SpringBootPlugin`，并受以下条件保护：

- `LocalContainerEntityManagerFactoryBean`；
- `EntityManager`；
- Hibernate `SessionImplementor`；
- `pf4boot.plugin.jpa.enabled=true`。

这意味着 JPA 默认不会为每个插件启用。插件必须在 `@PluginStarter` 中包含该 starter，并设置插件本地属性来启用 JPA。

共享 JPA consumer 示例：

```java
@PluginStarter({UserBookServiceStarter.class, PluginJPAStarter.class})
public class UserBookServicePlugin extends Pf4bootPlugin {
}
```

## 实体扫描规则

`PluginJPAStarter.getPackagesToScan()` 按以下顺序收集包名：

1. Spring Boot `@EntityScan` / `EntityScanPackages` 中的包；
2. Spring Boot auto-configuration packages；
3. 从已注册的 `pf4j.plugin` Bean 获取插件主类所在包。

如果无法找到任何包，启动会失败，并明确提示插件使用 `@EntityScan` 或确保插件 Bean 已注册。

## EntityManager 与事务 Bean

`PluginJPAStarter` 创建：

- 插件上下文中没有 `EntityManagerFactory` 时创建 `LocalContainerEntityManagerFactoryBean`；
- 没有 `TransactionManager` 时创建 `JpaTransactionManager`。

`DataSource`、`JpaProperties`、`HibernateProperties` 和 `HibernatePropertiesCustomizer` 列表从插件上下文及其父 BeanFactory 注入。

## 领域共享事务 starter

`pf4boot-jpa-domain-starter` 面向“数据源能力插件”使用。能力插件在 `@PluginStarter` 中包含 `Pf4bootJpaDomainStarter` 后，会按
`pf4boot.plugin.jpa.domain.*` 配置创建本地域级 Bean：

- `domainDataSource`
- `domainEntityManagerFactory`
- `domainTransactionManager`

随后 `DomainJpaPlatformExporter` 会把它们导出到当前插件 `group` 的平台上下文：

- `domain.{domain-id}.dataSource`
- `domain.{domain-id}.entityManagerFactory`
- `domain.{domain-id}.transactionManager`
- `domain.{domain-id}.descriptor`

导出发生在领域插件 Spring 单例初始化完成后；插件上下文关闭时按相反顺序注销。若平台导出冲突或配置错误，领域插件按
`PJF-004/PJF-005/PJF-008` 失败启动，并回滚已导出的 Bean。

领域插件创建 `EntityManagerFactory` 前会校验 `entity-packages`：

- 配置为空时按 `PJF-005` fail-fast。
- 包在当前插件 classpath 不可见或扫描失败时按 `PJF-008` fail-fast。
- 包可见但暂未扫描到 `@Entity` 时第一阶段输出 `PJF-008` warning，后续稳定后再评估是否切为 fail-fast。

共享 JPA 场景下，数据源能力插件应保持职责单一，不在自身源码中定义业务 entity、Repository、Controller 或业务 service。entity 推荐放在独立 model 模块中，由领域插件打包或由宿主提供到可见 classpath；consumer 插件的 Repository 引用这些 model entity，并显式绑定共享 `EntityManagerFactory` 和 `TransactionManager`。

领域 starter 排除 Spring Boot 的 DataSource/JPA Repository 自动配置，避免自动创建与共享域无关的默认 EMF 或 Repository。
业务插件使用 `PluginJPAStarter` 的 `mode=SHARED` 绑定这些平台 Bean，并通过显式 `@EnableJpaRepositories` 声明 Repository 包、
`entityManagerFactoryRef` 和 `transactionManagerRef`。

一个 consumer 插件需要多个共享 domain 时，主 domain 使用 `domain-id`，额外 domain 使用 `additional-domains`。starter 会为主 domain 和额外 domain 都注册本地 EMF/TM BeanDefinition，并校验对应 descriptor ready；Repository 仍必须按包拆分并显式绑定各自的 EMF/TM。跨 domain 原子事务暂不支持。

注意：Spring Data JPA 会递归扫描 parent BeanFactory 中的 `EntityManagerFactory`，随后反查同名 BeanDefinition。因此 JPA domain 导出到 root/platform/application 等共享上下文时，动态共享 Bean 不能只有 singleton；`Pf4bootPluginManagerImpl` 会同步注册指向同一实例的 BeanDefinition，供 Spring Data JPA 后处理器识别。

## DDL 默认策略

starter 通过 `HibernateSettings.ddlAuto` 将插件默认 `ddl-auto` 设为 `none`。自动 schema 更新在生产环境风险较高，必须由应用或插件显式配置。

## 动态元数据边界

`DynamicMetadata` 只用于记录候选 entity class，不会把新增或移除的 class 同步到已经创建的 `EntityManagerFactory`。`sync()` 必须明确失败，避免调用方误认为 Hibernate metamodel、repository 代理或事务上下文已被运行时刷新。

插件 JPA entity 的有效发现边界是 `PluginJPAStarter` 创建 `EntityManagerFactory` 时的启动时扫描。需要新增 entity 的插件应在插件启动前通过 `@EntityScan`、auto-configuration package 或插件主类包扫描暴露实体。

## 兼容性

JPA 支持依赖 Spring Boot 2.7.x 和 Hibernate 5.6.x 行为。包扫描顺序、默认 DDL 行为、事务 Bean 条件或运行时动态同步边界的变更，都可能影响插件启动和 schema 管理。

## 验证

JPA 变更运行：

- `.\gradlew.bat :pf4boot-jpa:compileJava`
- `.\gradlew.bat :pf4boot-jpa-starter:compileJava`
- `.\gradlew.bat :pf4boot-jpa-domain-starter:compileJava :pf4boot-jpa-domain-starter:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`

手动检查应启动 `samples:cross-plugin-jpa:demo-host`，并确认 service/workflow 插件的 repository Bean 在插件上下文中创建成功。
