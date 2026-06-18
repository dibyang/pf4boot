# 插件开发指南

## 目标

本文面向插件开发者，提供从创建插件、声明依赖、打包验证、接入 JPA/Web/管理能力到排查故障的官方路径。3.3 起，本文同时作为官方模板索引和 `pf4boot-plugin 1.7.0` 能力基线说明。

## 快速开始

最小插件开发流程：

1. 创建插件模块并应用 `net.xdob.pf4boot-plugin`。
2. 为宿主提供的 API 使用 `compileOnlyApi`。
3. 为插件必须自带的运行时依赖使用 `bundle`。
4. 如依赖其它插件，使用 `plugin project(":...")` 声明插件依赖。
5. 定义插件描述符，确保 plugin id、version、provider、dependencies 稳定。
6. 执行插件包任务或 sample 组装任务。
7. 使用 sample host 或目标宿主加载插件并运行 smoke。

最小验证命令按插件类型选择，复杂 JPA sample 推荐：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

## `pf4boot-plugin 1.7.0` 能力基线

当前根构建使用：

```groovy
classpath "net.xdob.pf4boot:pf4boot-plugin:1.7.0"
```

本仓库只消费该辅助 Gradle 插件，不在当前项目中修改 `pf4boot-plugin` 外部仓库。3.3 文档和 sample 对齐以下基线：

| 能力 | 当前状态 | 本仓库使用位置 | 验证方式 |
| --- | --- | --- | --- |
| 插件工程应用 | 已确认 | `samples/cross-plugin-jpa:plugin-*` | `rg "apply plugin: 'net.xdob.pf4boot-plugin'" samples` |
| 依赖作用域 | 已使用 | sample 插件 `build.gradle` | 编译和插件包任务 |
| 插件间依赖 | 已使用 | workflow 依赖 user-book service/domain | runtime smoke |
| 插件描述符 | 当前 sample 使用 `plugin.properties` | `samples/cross-plugin-jpa:plugin-*` | 打包后读取 descriptor |
| 模板生成 | 待核对 1.7.0 公开任务 | 后续 E4/E5 再决定是否引用 | 不把未核对能力写成必选路径 |
| package manifest/check | 框架侧已有 checksum/trust 设计 | `plugin-loading-and-packaging.md`、repository 示例 | 后续兼容矩阵和打包校验补强 |

规则：

- 如果 1.7.0 同时支持 Gradle DSL 和 `plugin.properties`，官方新模板应优先使用更易校验和生成的方式；旧 sample 可保留 `plugin.properties`，但文档必须说明兼容关系。
- 未在当前仓库验证过的 1.7.0 能力只能标记为“待核对”，不得作为开发者必选步骤。
- 发布、打 tag、修改 `pf4boot-plugin` 本身必须在该外部仓库中明确执行，不能混入当前 `pf4boot` 任务。

## 依赖作用域决策表

| 场景 | 使用 | 不要使用 |
| --- | --- | --- |
| 宿主提供的框架 API | `compileOnlyApi` | `bundle` |
| 插件运行时必须自带的第三方库 | `bundle` | 依赖宿主偶然存在 |
| 插件 A 依赖插件 B 的生命周期 | `plugin project(":...")` | 只用普通 project 依赖 |
| 插件 A 编译期需要插件 B 的 API | `compileOnlyApi project(":plugin-b")` + `plugin project(":plugin-b")` | 把插件 B 打进插件 A |
| 宿主平台统一提供的库 | `platformApi` 或宿主依赖 | 插件重复打包不同版本 |

反例：

- 不要把 `pf4boot-api`、`pf4boot-jpa`、`pf4boot-web-support` 这类宿主共享 API 打进插件包。
- 不要把另一个插件的实现类作为普通 jar 打进当前插件，应该通过插件依赖和导出的 service API 协作。
- 不要依赖宿主 classpath 上偶然存在的业务 jar；业务 API 应有明确的 host-provided 或 plugin-provided 边界。

## 官方模板矩阵

| 模板 | 来源 sample | 必选依赖 | 禁止事项 | 验证命令 |
| --- | --- | --- | --- | --- |
| service plugin | `plugin-unrelated-service` | `pf4boot-api`、导出 API | 不引入 JPA starter | `.\gradlew.bat :samples:cross-plugin-jpa:plugin-unrelated-service:compileJava` |
| web plugin | 后续 Web sample 或现有 Web 支持扩展 | `pf4boot-web-support` | 不直接操作 host MVC 内部集合 | `.\gradlew.bat :pf4boot-web-starter:test` |
| JPA domain plugin | `plugin-demo-jpa-domain` | `pf4boot-jpa-domain-starter`、model 模块 | 不定义 entity、Repository、Controller、业务 service | `.\gradlew.bat :samples:cross-plugin-jpa:plugin-demo-jpa-domain:compileJava` |
| JPA consumer plugin | `plugin-user-book-service` | `pf4boot-jpa-starter`、model 模块、domain 插件依赖 | 不创建本地 EMF/TM | `.\gradlew.bat :samples:cross-plugin-jpa:plugin-user-book-service:compileJava` |
| workflow plugin | `plugin-workflow` | 服务 API、domain/consumer 插件依赖 | 不跨插件注入内部 Repository | `.\gradlew.bat :samples:cross-plugin-jpa:plugin-workflow:compileJava` |
| management client | `samples/plugin-management-console` | HTTP 管理 API | 不依赖 core 内部类 | `.\gradlew.bat :samples:plugin-management-console:test` |

## 插件类型指南

### 普通服务插件

适用于导出业务服务、扩展点或内部任务，不需要 Web/JPA。

- 使用 `compileOnlyApi` 依赖宿主 API。
- 只把插件私有运行时库放入 `bundle`。
- 如果服务要被其它插件调用，导出稳定 service API，不暴露内部实现类。
- 可参考 `samples/cross-plugin-jpa/plugin-unrelated-service`。

### Web 插件

适用于提供 Controller、拦截器或静态资源。

- 依赖 `pf4boot-web-support` 作为编译 API。
- Web starter 由宿主提供，不建议插件私自携带一套 Spring MVC。
- 插件停止后动态 mapping、interceptor 和静态资源必须可清理。
- 验证至少覆盖 endpoint 启动可访问、停止不可访问、重启恢复。

### JPA domain 插件

适用于提供共享数据源和事务域。

- 使用 `pf4boot-jpa-domain-starter`。
- 实现 `JpaDomainDefinitionProvider` 声明 domain id、entity packages、DataSource 和 DDL 策略。
- 只导出 `domain.{domain-id}.dataSource`、`entityManagerFactory`、`transactionManager` 和 descriptor。
- 不定义业务 Repository、Controller 或 service。

### JPA consumer 插件

适用于消费共享 domain 并定义 Repository/service。

- 使用 `pf4boot-jpa-starter`。
- 插件主类实现 `JpaConsumerBindingProvider`，例如 `JpaConsumerBinding.shared("demo")`。
- Repository 按包分组，并通过 `@EnableJpaRepositories` 显式绑定共享 EMF/TM。
- 业务事务推荐显式指定 `transactionManager = "domain.demo.transactionManager"`。

### Workflow 插件

适用于组合多个插件导出的服务。

- 通过导出的 service API 调用其它插件能力。
- 不直接注入其它插件内部 Repository。
- 需要跨插件事务时，所有参与方必须在同一 JPA domain 内。
- 跨数据源原子事务不支持，使用 Saga/Outbox 等业务补偿模式。

### 管理客户端

适用于 UI、CLI 或脚本接入管理能力。

- 只调用 `/pf4boot/admin/**` 和只读 Actuator。
- 写接口携带 token 和 `X-Idempotency-Key`。
- 不依赖 `pf4boot-core` 内部类或内存对象。
- 可参考 `samples/plugin-management-console`。

## 插件描述符

当前官方 sample 仍保留 `plugin.properties`，后续可在确认 `pf4boot-plugin 1.7.0` DSL 语义后逐步迁移。

基本规则：

- plugin id 必须稳定，不能随版本变化。
- version 必须与插件包和依赖声明一致。
- dependencies 表达插件运行时依赖关系，而不是普通 Java 编译依赖。
- 描述符变更属于插件兼容性变更，必须能被 package verification 或部署预检发现。

## 插件包校验

宿主可启用加载前插件包治理。checksum 用于包完整性，trust manifest 用于包摘要、签名元数据和能力声明：

```yaml
spring:
  pf4boot:
    plugin-package-verification-mode: WARN
    plugin-package-trust-mode: WARN
    plugin-compatibility-verification-mode: WARN
    plugin-package-checksum-extension: .sha256
    plugin-package-trust-manifest-extension: .pf4boot-trust.json
    plugin-capability-precheck-mode: WARN
    plugin-compatibility-precheck-mode: WARN
    plugin-compatibility-pf4boot-version: 3.3.0
    plugin-compatibility-pf4boot-plugin-version: 1.7.0
    plugin-compatibility-spring-boot-version: 2.7.22
    plugin-compatibility-pf4j-version: 3.15.0
    plugin-compatibility-jdk-version: 1.8
    plugin-compatibility-package-format-version: 1
    system-version: 1.0.0
```

- `DISABLED`：默认兼容模式，不阻断历史插件。
- `WARN`：记录缺失 checksum、checksum 不匹配或系统版本不兼容，不阻断加载。
- `ENFORCE`：校验失败时阻断插件加载。

默认 checksum 文件位于插件包同级目录，例如 `sample-workflow.zip.sha256`。trust manifest 第一阶段也使用同级旁路文件，例如 `sample-workflow.zip.pf4boot-trust.json`。

部署预检会读取 trust manifest 中的 `pf4bootVersionRange`、`pf4bootPluginVersionRange`、`springBootVersionRange`、`pf4jVersionRange`、`jdkVersionRange` 和 `packageFormatVersionRange`。`plugin-compatibility-precheck-mode=WARN` 时只进入部署计划告警；切换为 `ENFORCE` 后不兼容插件包会被 replace 阶段阻断。

## 离线插件仓库

宿主可以启用本地或内网挂载的 offline-index 仓库。仓库目录包含 `repository-index.json` 和插件包，框架不会从远程中心服务下载插件：

```yaml
spring:
  pf4boot:
    plugin-repository-enabled: true
    plugin-repository-type: offline-index
    plugin-repository-location: /opt/pf4boot/repository
    plugin-repository-trust-mode: WARN
    plugin-repository-cache-directory: /opt/pf4boot/repository-cache
    plugin-repository-replace-enabled: false
```

默认情况下 repository release 只执行解析、校验和 plan。若显式设置 `plugin-repository-replace-enabled=true`，管理接口在 `dryRun=false` 时会把 release 包复制到受控 cache 后复用现有 replace/rollback 编排。

## JPA 开发路径

共享 JPA 事务域推荐拆成三类模块：

- model 模块只放 entity 和值对象。
- 领域能力插件只创建并导出 DataSource、EntityManagerFactory、TransactionManager 和 descriptor。
- consumer 插件定义自己的 Repository 和业务 service，Repository 的 entity 来自 model 模块，并通过 `@EnableJpaRepositories` 显式绑定共享 EMF/TM。

provider 示例：

```java
@PluginStarter(Pf4bootJpaDomainStarter.class)
public class DemoJpaDomainPlugin extends Pf4bootPlugin implements JpaDomainDefinitionProvider {
  @Override
  public JpaDomainDefinition jpaDomainDefinition() {
    return JpaDomainDefinition.builder("demo")
        .entityPackage("net.xdob.demo.model")
        .dataSource(JpaDataSourceDefinition.builder("jdbc:h2:file:./work/demo")
            .username("sa")
            .driverClassName("org.h2.Driver")
            .build())
        .ddlAuto("update")
        .build();
  }
}
```

consumer 示例：

```java
@PluginStarter({WorkflowStarter.class, PluginJPAStarter.class})
public class WorkflowPlugin extends Pf4bootPlugin implements JpaConsumerBindingProvider {
  @Override
  public JpaConsumerBinding jpaConsumerBinding() {
    return JpaConsumerBinding.shared("demo")
        .additionalDomain("audit")
        .build();
  }
}
```

Repository 配置：

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "net.xdob.sample.userbook.repository",
    entityManagerFactoryRef = "domain.demo.entityManagerFactory",
    transactionManagerRef = "domain.demo.transactionManager"
)
public class UserBookJpaConfig {
}
```

常见失败：

| 错误码 | 含义 | 处理 |
| --- | --- | --- |
| `PJF-005` | entity package 为空 | provider 补充实体包 |
| `PJF-008` | entity package 不可见或扫描失败 | 检查 model 依赖和类加载边界 |
| `PJF-009` | domain definition 缺失 | provider 实现 `JpaDomainDefinitionProvider` |

JPA runtime refresh 是维护窗口能力，不承诺无停顿。默认 `spring.pf4boot.jpa.reload.mode=DISABLED`，需要 JPA 管理能力时宿主必须显式引入 `pf4boot-jpa-management-starter`。

## 运维接入

### 管理接口

- 基础管理能力由 `pf4boot-management-starter` 提供。
- JPA reload 管理能力由 `pf4boot-jpa-management-starter` 提供。
- 写接口必须鉴权、授权、限流、幂等、审计。
- 管理客户端不得读取内部 Java 对象，只消费 HTTP 响应。

### 只读观测

宿主显式引入 `pf4boot-actuator` 后，会注册只读 Actuator endpoint，例如 `pf4bootplugins`、`pf4bootgovernance` 和按需的 `pf4bootjpareload`。

这些 endpoint 只返回插件快照和治理摘要，不提供 start、stop、reload 或 delete。

### 热替换和回滚

发布级安全替换应使用 `PluginDeploymentService.replace(...)` 或管理接口 deployment replace，不要把底层 `reloadPlugin` 包装成热替换流程。

## 故障排查

| 现象 | 优先检查 |
| --- | --- |
| 插件类找不到 | 是否错误使用 `compileOnlyApi`，应打包的依赖是否放入 `bundle` |
| 类型无法注入 | 宿主 API 是否被重复打入插件包，类加载器是否出现重复类 |
| 插件依赖未启动 | 是否缺少 `plugin project(":...")` 或 descriptor dependencies |
| JPA Repository 找不到 EMF/TM | `@EnableJpaRepositories` 是否显式绑定 `domain.{id}.*` |
| provider 停止后 consumer 失败 | consumer 是否依赖同一 domain，这是预期影响范围 |
| 无关插件被 JPA 故障影响 | 检查是否误依赖 JPA starter 或 provider 插件 |
| 管理写接口 403/401 | token、权限点、loopback 策略和 CSRF/origin 配置 |
| 幂等请求未复用结果 | `X-Idempotency-Key`、operation、target 是否一致 |

## 最小验证命令

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat :samples:plugin-management-console:test
```

`runtimeSmoke` 会生成机器可读报告：

```text
samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json
samples/cross-plugin-jpa/app-run/build/test-results/runtimeSmoke/TEST-runtime-smoke.xml
```
