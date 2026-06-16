# 跨插件 JPA 事务迁移指南

本文说明如何把已有插件从本地 JPA 模式迁移到共享领域事务模式。设计背景见
[cross-plugin-jpa-transaction-capability.md](cross-plugin-jpa-transaction-capability.md)，实施追踪见
[archive/cross-plugin-jpa-transaction-capability-plan.md](archive/cross-plugin-jpa-transaction-capability-plan.md)。

## 1. 适用范围

- 适用于多个插件需要访问同一数据源、并希望共享同一个 `JpaTransactionManager` 的场景。
- 适用于单域事务；跨数据源、跨域原子事务不在当前阶段支持。
- 不适用于业务插件在运行时动态追加 entity 到已启动 EMF 的场景。

## 2. 迁移原则

- 一个数据源对应一个领域能力插件。
- entity 归属领域能力插件或领域共享库，Repository 归属业务插件。
- 业务插件一个域一个 `@EnableJpaRepositories`，显式设置 `entityManagerFactoryRef` 和 `transactionManagerRef`。
- 业务服务上的 `@Transactional` 推荐显式指定领域事务管理器。
- provider 故障只应影响依赖它的插件链，未依赖该 provider 的插件继续工作。

## 3. 新建领域能力插件

领域能力插件依赖 `pf4boot-jpa-domain-starter`，并声明 starter：

```java
@PluginStarter(Pf4bootJpaDomainStarter.class)
public class DemoJpaDomainPlugin extends Pf4bootPlugin {
  public DemoJpaDomainPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }
}
```

Gradle 依赖示例：

```groovy
dependencies {
  compileOnlyApi project(":pf4boot-api")
  compileOnlyApi project(":pf4boot-jpa")
  compileOnlyApi project(":demo-lib")
  bundle project(":pf4boot-jpa-domain-starter")
}
```

## 4. 配置共享事务域

在宿主配置或插件可见配置中声明 provider 和 consumer 共用的域：

```yaml
pf4boot:
  plugin:
    jpa:
      enabled: true
      mode: SHARED
      domain-id: demo
      domain:
        id: demo
        entity-packages:
          - net.xdob.demo.plugin1.dao.entity
          - net.xdob.demo.dao
        datasource:
          url: jdbc:h2:file:~/h2/pf4boot_demo;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=user
          username: sa
          password: ysyhljt2020*
          driver-class-name: org.h2.Driver
        ddl-auto: update
```

默认导出名称：

- `domain.demo.dataSource`
- `domain.demo.entityManagerFactory`
- `domain.demo.transactionManager`

## 5. 改造业务插件

业务插件依赖领域能力插件：

```groovy
pf4bootPlugin {
  dependencies = "demo-jpa-domain"
}

dependencies {
  compileOnlyApi project(":plugin-jpa-domain")
  plugin project(":plugin-jpa-domain")
  bundle project(":pf4boot-jpa-starter")
}
```

业务插件只扫描自己的 Repository 包，并绑定共享 EMF/TM：

```java
@SpringBootPlugin
@EnableJpaRepositories(
    basePackages = {
        "net.xdob.demo.plugin1.dao.repository",
        "net.xdob.demo.dao"
    },
    entityManagerFactoryRef = "domain.demo.entityManagerFactory",
    transactionManagerRef = "domain.demo.transactionManager"
)
public class Plugin1Starter {
}
```

业务事务显式指定共享事务管理器：

```java
@Transactional(transactionManager = "domain.demo.transactionManager")
public void removeUserAndBooks(String username) {
  userRepository.deleteById(username);
  bookRepository.deleteByAuthor(username);
}
```

## 6. 插件内多域示例

当一个插件依赖多个数据源时，按域拆分 Repository 包：

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.demo.order.repository",
    entityManagerFactoryRef = "domain.order.entityManagerFactory",
    transactionManagerRef = "domain.order.transactionManager"
)
public class OrderJpaConfig {
}
```

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.demo.report.repository",
    entityManagerFactoryRef = "domain.report.entityManagerFactory",
    transactionManagerRef = "domain.report.transactionManager"
)
public class ReportJpaConfig {
}
```

不同域之间不自动组成一个原子事务。需要同时写两个域时，应在业务层明确接受部分成功风险，或等待后续跨域事务方案。

## 7. 回滚方式

- 快速回滚：业务插件改回 `pf4boot.plugin.jpa.mode=LOCAL`，并恢复本地 datasource/EMF 配置。
- 依赖回滚：移除业务插件的 `plugin.dependencies` 中的领域能力插件。
- 代码回滚：将 entity 从领域能力插件迁回业务插件，并恢复原 Repository 扫描范围。

## 8. 验证命令

```powershell
.\gradlew.bat :pf4boot-jpa-starter:compileJava :pf4boot-jpa-starter:test
.\gradlew.bat :pf4boot-jpa-domain-starter:compileJava :pf4boot-jpa-domain-starter:test
.\gradlew.bat :plugin-jpa-domain:build :plugin1:build :plugin2:build
```

手工验证：

- provider 启动后平台上下文存在 `domain.demo.*` Bean；
- `plugin1` 未创建本地 EMF/TM，Repository 绑定 `domain.demo.*`；
- `/api/user/remove` 可在同一个事务中删除用户与图书；
- 停用或破坏 `demo-jpa-domain` 时，依赖链失败，未依赖该链的插件不受影响。
