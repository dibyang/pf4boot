# Cross-Plugin JPA Transaction Migration Guide

This document explains how to migrate an existing plugin from local JPA mode to shared-domain transaction mode.
See [cross-plugin-jpa-transaction-capability.md](cross-plugin-jpa-transaction-capability.md) for the design and
[archive/cross-plugin-jpa-transaction-capability-plan.md](archive/cross-plugin-jpa-transaction-capability-plan.md) for tracking.

## 1. Scope

- Use this when multiple plugins access the same datasource and need the same `JpaTransactionManager`.
- The current phase supports same-domain transactions only.
- Cross-datasource atomic transactions and runtime entity contribution into an already-started EMF are out of scope.

## 2. Migration Principles

- One datasource maps to one domain capability plugin.
- Entities belong to the domain capability plugin or a domain shared library; repositories belong to business plugins.
- Business plugins use one `@EnableJpaRepositories` per domain and set `entityManagerFactoryRef` plus `transactionManagerRef` explicitly.
- Business services should set the transaction manager explicitly on `@Transactional`.
- Provider failure should affect only dependent plugin chains; unrelated plugins should keep working.

## 3. Add A Domain Capability Plugin

The capability plugin depends on `pf4boot-jpa-domain-starter` and declares the starter:

```java
@PluginStarter(Pf4bootJpaDomainStarter.class)
public class DemoJpaDomainPlugin extends Pf4bootPlugin {
  public DemoJpaDomainPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }
}
```

Gradle dependency example:

```groovy
dependencies {
  compileOnlyApi project(":pf4boot-api")
  compileOnlyApi project(":pf4boot-jpa")
  compileOnlyApi project(":demo-lib")
  bundle project(":pf4boot-jpa-domain-starter")
}
```

## 4. Declare The Shared Domain

Provider plugins declare the shared transaction domain through `JpaDomainDefinitionProvider`. Entity packages,
DataSource, and DDL policy are plugin contracts and are no longer maintained in the host `application.yml`:

```java
@PluginStarter(Pf4bootJpaDomainStarter.class)
public class DemoJpaDomainPlugin extends Pf4bootPlugin implements JpaDomainDefinitionProvider {
  @Override
  public JpaDomainDefinition jpaDomainDefinition() {
    return JpaDomainDefinition.builder("demo")
        .entityPackage("net.xdob.demo.plugin1.dao.entity")
        .entityPackage("net.xdob.demo.dao")
        .dataSource(JpaDataSourceDefinition
            .builder("jdbc:h2:file:~/h2/pf4boot_demo;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=user")
            .username("sa")
            .password("******")
            .driverClassName("org.h2.Driver")
            .build())
        .ddlAuto("update")
        .build();
  }
}
```

Consumer plugins bind the shared domain through `JpaConsumerBindingProvider`:

```java
@PluginStarter({Plugin1Starter.class, PluginJPAStarter.class})
public class Plugin1 extends Pf4bootPlugin implements JpaConsumerBindingProvider {
  @Override
  public JpaConsumerBinding jpaConsumerBinding() {
    return JpaConsumerBinding.shared("demo").build();
  }
}
```

Default exported names:

- `domain.demo.dataSource`
- `domain.demo.entityManagerFactory`
- `domain.demo.transactionManager`

## 5. Migrate The Business Plugin

The business plugin depends on the domain capability plugin:

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

The business plugin scans repository packages and binds them to the shared EMF/TM:

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

Business transactions should name the shared transaction manager:

```java
@Transactional(transactionManager = "domain.demo.transactionManager")
public void removeUserAndBooks(String username) {
  userRepository.deleteById(username);
  bookRepository.deleteByAuthor(username);
}
```

## 6. Multiple Domains In One Plugin

When a plugin depends on multiple datasources, split repository packages by domain:

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

Different domains do not automatically form one atomic transaction. If a workflow writes two domains, the business
layer must explicitly accept partial-success risk or wait for a future cross-domain transaction design.

## 7. Rollback

- Fast rollback: remove the business plugin's `SHARED` `JpaConsumerBindingProvider` binding and restore local datasource/EMF config.
- Dependency rollback: remove the domain capability plugin from `plugin.dependencies`.
- Code rollback: move entities back into the business plugin and restore the previous repository scan range.

## 8. Verification Commands

```powershell
.\gradlew.bat :pf4boot-jpa-starter:compileJava :pf4boot-jpa-starter:test
.\gradlew.bat :pf4boot-jpa-domain-starter:compileJava :pf4boot-jpa-domain-starter:test
.\gradlew.bat :plugin-jpa-domain:build :plugin1:build :plugin2:build
```

Manual checks:

- After provider startup, the platform context contains `domain.demo.*` beans.
- `plugin1` does not create a local EMF/TM and repositories bind to `domain.demo.*`.
- `/api/user/remove` deletes the user and books in the same transaction.
- If `demo-jpa-domain` is disabled or broken, its dependent chain fails while unrelated plugins stay unaffected.
