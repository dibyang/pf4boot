# JPA 插件自治配置整改计划

## 问题

当前框架已经把 `samples/cross-plugin-jpa` 中的宿主 JPA 结构配置清理掉，但框架能力本身仍暴露了可由宿主配置的插件 JPA 维护项：

- provider 侧：`pf4boot.plugin.jpa.domain.*`，包括 `id`、`entity-packages`、`datasource`、`ddl-auto`、导出 Bean 名称等。
- consumer 侧：`pf4boot.plugin.jpa.enabled`、`pf4boot.plugin.jpa.mode/domain-id`、`pf4boot.plugin.jpa.plugins.{pluginId}.*`、`additional-domains` 等绑定项。

这与插件自治边界不一致。JPA domain 的实体包、数据源、DDL 策略、共享 domain 绑定关系都属于插件自身的运行契约，不应成为宿主应用的通用配置项。宿主如果能通过配置替插件维护这些字段，就会让插件升级、依赖边界、实体可见性和事务绑定都变成宿主侧隐性知识。

宿主仍可以配置平台治理能力，例如管理接口、JPA reload 策略、drain 超时、记录仓库和观测暴露；但不能替插件描述“有哪些实体、连哪个数据源、绑定哪个 JPA domain”。

## 影响模块

- `pf4boot-jpa-domain-starter`：移除 provider domain 的环境配置绑定，改为读取插件提供的 domain 定义。
- `pf4boot-jpa-starter`：移除 consumer JPA 绑定的环境配置入口，改为读取插件提供的 consumer binding 定义。
- `pf4boot-jpa`：承载可复用的 domain/binding 描述模型时，需要保持 Java 8 兼容和最小依赖。
- `samples/cross-plugin-jpa`：把当前 `setProperty(...)` 示例改成新的插件自治 API。
- `docs/design`：同步更新 JPA 集成、开发指南和迁移文档，避免继续把环境配置写成推荐方式。

## 目标边界

### 不再作为框架公开配置项的插件维护项

以下 key 不应再作为框架公开配置项出现，也不应在宿主 `application.yml` 中被推荐：

```text
pf4boot.plugin.jpa.domain.id
pf4boot.plugin.jpa.domain.entity-packages
pf4boot.plugin.jpa.domain.datasource.*
pf4boot.plugin.jpa.domain.ddl-auto
pf4boot.plugin.jpa.domain.data-source-name
pf4boot.plugin.jpa.domain.entity-manager-factory-name
pf4boot.plugin.jpa.domain.transaction-manager-name
pf4boot.plugin.jpa.domain.descriptor-name
pf4boot.plugin.jpa.enabled
pf4boot.plugin.jpa.mode
pf4boot.plugin.jpa.domain-id
pf4boot.plugin.jpa.entity-manager-factory-ref
pf4boot.plugin.jpa.transaction-manager-ref
pf4boot.plugin.jpa.descriptor-ref
pf4boot.plugin.jpa.additional-domains
pf4boot.plugin.jpa.plugins.*
```

这些项对应的是插件自有 contract，应由插件代码、插件内元数据或插件内 Bean 明确提供。

### 可以保留的宿主治理项

以下属于平台治理能力，可以继续由宿主配置：

```text
pf4boot.plugin.jpa.domain-reload.*
management.endpoints.web.exposure.include
spring.pf4boot.management.*
```

这些配置只决定平台如何治理插件生命周期和记录运维操作，不决定插件自身 JPA 结构。

## 设计方案

### Provider 插件定义 domain

新增插件侧 domain 定义模型，例如：

```java
public interface JpaDomainDefinitionProvider {
  JpaDomainDefinition jpaDomainDefinition();
}
```

或通过插件上下文中的 Bean 暴露：

```java
@Bean
public JpaDomainDefinition demoDomainDefinition() {
  return JpaDomainDefinition.builder("demo")
      .entityPackage("net.xdob.sample.model.userbook")
      .entityPackage("net.xdob.sample.model.audit")
      .dataSource(dataSourceDefinition())
      .ddlAuto("update")
      .build();
}
```

`Pf4bootJpaDomainStarter` 不再绑定 `Pf4bootJpaDomainProperties`。启动时只从当前插件上下文读取一个明确的 `JpaDomainDefinition`，并基于该定义创建 DataSource、EMF、TM 和 descriptor。若缺少定义，按新的错误码 fail-fast，例如：

```text
PJF-009 Domain provider plugin did not provide JpaDomainDefinition
```

### Consumer 插件定义 shared binding

新增插件侧 consumer 绑定定义，例如：

```java
public interface JpaConsumerBindingProvider {
  JpaConsumerBinding jpaConsumerBinding();
}
```

或通过 Bean 暴露：

```java
@Bean
public JpaConsumerBinding userBookJpaBinding() {
  return JpaConsumerBinding.shared("demo").build();
}
```

`PluginJPAStarter` 是否启用不再依赖 `pf4boot.plugin.jpa.enabled=true`。插件只要在 `@PluginStarter` 中显式包含 `PluginJPAStarter`，就表示它需要 JPA starter。starter 启动时从插件上下文读取 `JpaConsumerBinding`：

- 缺省或缺少 binding：按 `LOCAL` 处理，保持简单插件本地 JPA 能力。
- `SHARED` binding：按 domain id 和额外 domain 定义注册本地 EMF/TM BeanDefinition，并校验 descriptor ready。

### 兼容期处理

当前配置已经存在，不能一次性无提示删除。建议分两阶段：

1. `3.1.x` 兼容期：
   - 新增插件自治 API。
   - 旧配置仍可绑定，但启动时输出 deprecation warning。
   - 当旧配置来自宿主环境时，warning 明确提示“插件 JPA 结构不应由宿主维护”。
   - sample 全部迁移到新 API。

2. `4.0` 移除期：
   - 删除 `Pf4bootJpaDomainProperties` 中 provider 结构配置绑定。
   - 删除 `Pf4bootJpaProperties` 中 consumer 结构绑定字段，只保留 `DomainReload` 或将 reload 配置拆到独立 properties。
   - 删除旧配置相关测试和文档。

### 配置命名调整

为避免误解，建议把 reload 治理配置从 `pf4boot.plugin.jpa.domain-reload.*` 迁移到更明确的宿主治理前缀，例如：

```text
spring.pf4boot.jpa.reload.*
```

兼容期内旧前缀继续读取并输出迁移提示。这样可以从命名上区分：

- `spring.pf4boot.*`：宿主/平台治理配置。
- 插件 JPA 结构：插件代码或插件内元数据声明，不走宿主配置。

## 修改计划

1. 设计 API：增加 `JpaDomainDefinition`、`JpaDataSourceDefinition`、`JpaConsumerBinding` 等不可变模型，优先放在 `pf4boot-jpa`。
2. Provider starter 改造：`Pf4bootJpaDomainStarter` 改为读取 `JpaDomainDefinition` Bean 或 provider SPI；旧配置作为兼容 fallback 并输出 warning。
3. Consumer starter 改造：`PluginJPAStarter` 改为基于显式 `@PluginStarter` 引入和 `JpaConsumerBinding` 定义启动；旧 `pf4boot.plugin.jpa.*` 仅作为兼容 fallback。
4. Reload 配置拆分：新增 `spring.pf4boot.jpa.reload.*`，旧 `pf4boot.plugin.jpa.domain-reload.*` 作为兼容 fallback。
5. Sample 迁移：`samples/cross-plugin-jpa` provider 暴露 `JpaDomainDefinition`，consumer 暴露 `JpaConsumerBinding`，删除结构性 `setProperty("pf4boot.plugin.jpa...")`。
6. 文档迁移：更新 `jpa-integration.md`、`plugin-developer-guide.md`、`cross-plugin-jpa-transaction-*.md` 和英文翻译。

## 落地状态

- 已新增 `JpaDomainDefinition`、`JpaDataSourceDefinition`、`JpaDomainDefinitionProvider`、
  `JpaConsumerBinding`、`JpaConsumerDomainBinding`、`JpaBindingMode`、`JpaConsumerBindingProvider`。
- `Pf4bootJpaDomainStarter` 已改为优先读取插件提供的 `JpaDomainDefinition`；旧
  `pf4boot.plugin.jpa.domain.*` 保留兼容 fallback 并输出 deprecation warning。
- `PluginJPAStarter` 已移除 `pf4boot.plugin.jpa.enabled=true` 条件，显式加入 `@PluginStarter` 即 opt-in；
  shared binding 优先读取 `JpaConsumerBinding`，旧 `mode/domain-id` 和 `plugins.*` 保留兼容 fallback。
- `spring.pf4boot.jpa.reload.*` 已作为 JPA reload 宿主治理前缀接入，旧
  `pf4boot.plugin.jpa.domain-reload.*` 保留兼容 fallback。
- `samples/cross-plugin-jpa` 已迁移到插件主类 SPI，不再通过结构性 `setProperty("pf4boot.plugin.jpa...")`
  描述 domain 或 consumer binding。
- 当前文档入口和中英文迁移说明已同步；archive 下的历史验收/计划文档保持原始记录语义。

## 兼容性

- 源码兼容：新增 API 不破坏现有插件源码；旧配置路径在兼容期仍可用。
- 运行时兼容：旧插件继续启动，但日志提示迁移。
- 行为兼容：`LOCAL/SHARED`、domain descriptor、reload plan 和 drain 行为保持不变。
- 配置兼容：旧配置进入 deprecation；`4.0` 才移除。
- 插件打包兼容：插件需要随自身代码或插件内资源携带 JPA 定义，宿主不再持有实体包清单。

## 验证

最小验证：

```powershell
.\gradlew.bat :pf4boot-jpa:compileJava
.\gradlew.bat :pf4boot-jpa-starter:test
.\gradlew.bat :pf4boot-jpa-domain-starter:test
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

新增测试：

- provider 缺少 `JpaDomainDefinition` 时 fail-fast。
- provider 使用新定义创建并导出 domain。
- 旧 `pf4boot.plugin.jpa.domain.*` 配置仍可兼容并输出 warning。
- consumer 使用新 binding 绑定 shared domain。
- 旧 `pf4boot.plugin.jpa.mode/domain-id` 和 `plugins.*` 配置仍可兼容并输出 warning。
- sample 不依赖宿主结构性 JPA 配置即可启动。

## 未决问题

- 插件自治定义优先使用 SPI 方法还是 Bean 模型。Bean 模型更贴近 Spring starter；SPI 更容易在非 Spring 插件中复用。
- 数据源定义是否允许直接提供 `DataSource` Bean，还是只提供构建参数。
- `spring.pf4boot.jpa.reload.*` 是否在 3.x 同步支持，还是先只做 deprecation warning。
- 是否需要在管理接口暴露“插件使用旧 JPA 配置”的治理 warning。
