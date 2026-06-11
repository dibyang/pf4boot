# 插件开发指南

## 目标

本文给插件开发者提供一条最小闭环路径：声明依赖、打包插件、启用包校验、接入只读观测、使用 JPA，并在失败时能定位问题。

## 依赖作用域

- 宿主已经提供的 API 使用 `compileOnlyApi`，例如 `pf4boot-api`、Spring Boot API、公共业务接口。
- 插件运行时必须自带的依赖使用 `bundle`，避免宿主缺依赖时启动失败。
- 插件依赖另一个插件时使用 `plugin project(":samples:cross-plugin-jpa:plugin-user-book-service")` 这类插件依赖声明，由 PF4J 依赖解析保证启动顺序。
- 不要把宿主框架 API 打进插件包，否则容易造成类加载冲突。

## 插件包校验

宿主可启用加载前插件包治理：

```yaml
spring:
  pf4boot:
    plugin-package-verification-mode: WARN
    plugin-compatibility-verification-mode: WARN
    plugin-package-checksum-extension: .sha256
    system-version: 1.0.0
```

- `DISABLED`：默认兼容模式，不阻断历史插件。
- `WARN`：记录缺失 checksum、checksum 不匹配或系统版本不兼容，不阻断加载。
- `ENFORCE`：校验失败时阻断插件加载。

默认 checksum 文件位于插件包同级目录，例如 `sample-workflow.zip.sha256`。签名校验作为后续专题，不阻塞 checksum/verifier 使用。

## 只读观测

宿主显式引入 `pf4boot-actuator` 后，会注册只读 actuator endpoint：`pf4bootplugins`。

该 endpoint 只返回插件快照，不提供 start、stop、reload 或 delete。快照包含插件 ID、版本、状态、路径、最近错误、依赖、启动耗时和资源计数占位。

引入 `pf4boot-actuator` 后还会条件注册 Micrometer 指标：

- `pf4boot.plugins`
- `pf4boot.plugins.started`
- `pf4boot.plugins.failed`

## JPA 插件

插件 JPA 必须显式启用：

```yaml
pf4boot:
  plugin:
    jpa:
      enabled: true
```

插件侧默认 `ddl-auto=none`。schema 迁移由宿主或插件显式接入迁移工具，框架不强制绑定 Flyway 或 Liquibase。

实体扫描顺序为：

1. `@EntityScan` / `EntityScanPackages`
2. Spring Boot auto-configuration packages
3. 插件主类所在包

运行时新增 entity 不会自动刷新已有 `EntityManagerFactory`，`DynamicMetadata.sync()` 会明确失败，避免误以为 Hibernate metamodel 已热刷新。

共享 JPA 事务域推荐拆成三类模块：

- model 模块只放 entity 和值对象，可由领域能力插件打包或由宿主提供。
- 领域能力插件只创建并导出 DataSource、EntityManagerFactory、TransactionManager 和 descriptor，不定义业务 Repository、Controller 或 service。
- consumer 插件定义自己的 Repository 和业务 service，Repository 的 entity 来自 model 模块，并通过 `@EnableJpaRepositories` 显式绑定共享 EMF/TM。

领域能力插件的 `pf4boot.plugin.jpa.domain.entity-packages` 必须指向 provider 可见的 model 包；配置为空按 `PJF-005` 失败，包不可见或扫描失败按 `PJF-008` 失败，包可见但暂未发现 `@Entity` 时第一阶段输出 `PJF-008` warning。

如果一个 consumer 插件需要访问多个共享 domain，主 domain 使用 `domain-id`，其它 domain 放入 `additional-domains`，并按 Repository 包分别声明 `@EnableJpaRepositories`：

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

## 升级回滚

宿主可通过 `upgradePlugin(pluginId, newPluginPath, rollbackPluginPath)` 执行带回滚点的升级。升级失败时，框架会尝试从 `rollbackPluginPath` 重新加载上一版本；如果升级前插件处于启动态，回滚后会尝试恢复启动态。

## 最小验证命令

```powershell
.\gradlew.bat :pf4boot-core:test
.\gradlew.bat :pf4boot-actuator:test
.\gradlew.bat :pf4boot-jpa-starter:test
```
