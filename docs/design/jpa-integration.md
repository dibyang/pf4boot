# JPA 集成

## 问题

部分插件需要 JPA repository 和 entity，但非 JPA 插件不应该承担相关初始化成本，也不应该意外初始化 `EntityManagerFactory`。因此插件 JPA 支持是显式启用且插件本地化的。

## 模块

- `pf4boot-jpa`：提供 `Pf4bootJpaPersistenceProvider`，为 Hibernate 合并 managed class names 和 managed packages。
- `pf4boot-jpa-starter`：提供 `PluginJPAStarter`，作为插件侧 Spring Boot 自动配置类。
- `pf4boot-api`：声明 `DynamicMetadata` 和 `DynamicMetadataSupport`；当前只记录候选 entity class，不支持运行时同步到已创建的 JPA metamodel。

## Plugin Starter 行为

`PluginJPAStarter` 标注了 `@SpringBootPlugin`，并受以下条件保护：

- `LocalContainerEntityManagerFactoryBean`；
- `EntityManager`；
- Hibernate `SessionImplementor`；
- `pf4boot.plugin.jpa.enabled=true`。

这意味着 JPA 默认不会为每个插件启用。插件必须在 `@PluginStarter` 中包含该 starter，并设置插件本地属性来启用 JPA。

`plugin1` 示例：

```java
@PluginStarter({Plugin1Starter.class, PluginJPAStarter.class})
public class Plugin1Plugin extends Pf4bootPlugin {
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
- `.\gradlew.bat :plugin1:build`

手动检查应在 demo H2 数据库下启动 `plugin1`，并确认 repository Bean 在插件上下文中创建成功。
