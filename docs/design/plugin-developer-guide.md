# 插件开发指南

## 目标

本文给插件开发者提供一条最小闭环路径：声明依赖、打包插件、启用包校验、接入只读观测、使用 JPA，并在失败时能定位问题。

## 依赖作用域

- 宿主已经提供的 API 使用 `compileOnlyApi`，例如 `pf4boot-api`、Spring Boot API、公共业务接口。
- 插件运行时必须自带的依赖使用 `bundle`，避免宿主缺依赖时启动失败。
- 插件依赖另一个插件时使用 `plugin project(":samples:cross-plugin-jpa:plugin-user-book-service")` 这类插件依赖声明，由 PF4J 依赖解析保证启动顺序。
- 不要把宿主框架 API 打进插件包，否则容易造成类加载冲突。

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
    system-version: 1.0.0
```

- `DISABLED`：默认兼容模式，不阻断历史插件。
- `WARN`：记录缺失 checksum、checksum 不匹配或系统版本不兼容，不阻断加载。
- `ENFORCE`：校验失败时阻断插件加载。

默认 checksum 文件位于插件包同级目录，例如 `sample-workflow.zip.sha256`。trust manifest 第一阶段也使用同级旁路文件，例如 `sample-workflow.zip.pf4boot-trust.json`：

```json
{
  "formatVersion": 1,
  "pluginId": "sample-workflow",
  "pluginVersion": "3.0.0-SNAPSHOT",
  "packageSha256": "lowercase-hex-sha256",
  "pf4bootVersionRange": "[3.0.0,4.0.0)",
  "springBootVersionRange": "[2.7.0,2.8.0)",
  "signature": {
    "algorithm": "SHA256withRSA",
    "keyId": "local-dev-key",
    "value": "base64-signature"
  },
  "capabilities": {
    "provides": [
      {
        "name": "jpa.consumer",
        "version": "1",
        "scope": "PLUGIN",
        "attributes": {
          "datasource": "orderDs"
        }
      }
    ],
    "requires": [
      {
        "name": "jpa.datasource",
        "versionRange": "[1,2)",
        "required": true,
        "attributes": {
          "datasource": "orderDs",
          "entityPackages": "net.xdob.sample.model.userbook",
          "repositoryPackages": "net.xdob.sample.userbook.repository"
        }
      }
    ]
  }
}
```

版本范围第一阶段支持精确版本和常见 Maven 风格范围：

| 表达式 | 含义 |
| --- | --- |
| `1.2.3` | 精确匹配 |
| `[1.0,2.0)` | 大于等于 1.0 且小于 2.0 |
| `(1.0,2.0]` | 大于 1.0 且小于等于 2.0 |
| `[1.0,)` | 大于等于 1.0 |
| `(,2.0]` | 小于等于 2.0 |

`plugin-compatibility-precheck-mode` 控制 `pf4bootVersionRange` 和 `springBootVersionRange`。`plugin-capability-precheck-mode` 控制 `requiredCapabilities[].versionRange`。建议先用 `WARN` 观察，再切换到 `ENFORCE`。

## 离线插件仓库

宿主可以启用本地或内网挂载的 offline-index 仓库。仓库目录包含 `repository-index.json` 和插件包，框架不会从远程中心服务下载插件：

```yaml
spring:
  pf4boot:
    plugin-repository-enabled: true
    plugin-repository-type: offline-index
    plugin-repository-location: /opt/pf4boot/repository
    plugin-repository-trust-mode: WARN
```

`repository-index.json` 使用相对路径，包路径解析后必须仍在仓库根目录内：

```json
{
  "schemaVersion": 1,
  "repositoryId": "local-prod",
  "generatedAt": 1781280000000,
  "releases": [
    {
      "pluginId": "sample-workflow",
      "version": "3.0.0-SNAPSHOT",
      "packagePath": "plugins/plugin-workflow-3.0.0-SNAPSHOT.zip",
      "packageSha256": "lowercase-sha256",
      "trustManifestPath": "plugins/plugin-workflow-3.0.0-SNAPSHOT.zip.pf4boot-trust.json",
      "rolloutPolicy": "manual",
      "rollbackCandidate": true
    }
  ]
}
```

管理接口可用 release 字段做 dry-run plan：

```json
{
  "pluginId": "sample-workflow",
  "repositoryVersion": "3.0.0-SNAPSHOT",
  "dryRun": true
}
```

第一阶段 repository release 只保证解析、校验和 plan；真实 replace 仍建议使用已经 staging 的包路径。

生产迁移建议：

1. `DISABLED`：先部署不带 manifest 的历史插件，确认业务行为不变。
2. `WARN`：为新包生成 `.sha256` 和 `.pf4boot-trust.json`，观察缺失、摘要不一致、签名元数据和能力缺失告警。
3. `ENFORCE`：只在所有上线插件都具备 manifest、checksum 和必要能力声明后启用；启用前先在预发环境跑部署预检。

注意：`signature.value`、token、私钥路径和完整堆栈不得写入 HTTP 响应、审计记录或 operation store。框架会做基础脱敏，但插件发布流程也应避免把私钥或原始凭证放进 manifest。

## 只读观测

宿主显式引入 `pf4boot-actuator` 后，会注册只读 actuator endpoint：`pf4bootplugins` 和 `pf4bootgovernance`。

这些 endpoint 只返回插件快照和治理摘要，不提供 start、stop、reload 或 delete。`pf4bootplugins` 快照包含插件 ID、版本、状态、路径、最近错误、依赖、启动耗时和资源计数占位；`pf4bootgovernance` 汇总 trust/capability 配置、部署摘要、清理诊断和 warning。

宿主需要按 Spring Boot Actuator 规则暴露 endpoint，例如：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,pf4bootplugins,pf4bootgovernance
```

引入 `pf4boot-actuator` 后还会条件注册 Micrometer 指标：

- `pf4boot.plugins`
- `pf4boot.plugins.started`
- `pf4boot.plugins.failed`
- `pf4boot.management.request.total`
- `pf4boot.management.rejected.total`
- `pf4boot.management.idempotency.hit.total`

如果 `/actuator/pf4bootgovernance` 返回 404，优先确认宿主是否引入了 `pf4boot-actuator`、是否暴露了 endpoint、运行时 classpath 是否包含 `spring-boot-actuator-autoconfigure`，以及 `Pf4bootActuatorAutoConfiguration` 是否在 `Pf4bootAutoConfiguration` 后装配。

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
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

`runtimeSmoke` 会生成机器可读报告：

```text
samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json
```
