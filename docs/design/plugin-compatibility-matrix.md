# 插件兼容矩阵与打包校验

## 背景

插件框架不仅要能把插件包打出来，还要能判断这个包是否适合当前宿主运行。3.3 将兼容矩阵和打包校验作为插件交付前置检查：开发期用于 sample 验收，生产期可与 trust manifest、离线仓库和部署预检组合使用。

## 兼容矩阵

3.3 当前基线：

| 维度 | 版本/范围 | 说明 |
| --- | --- | --- |
| `pf4boot` | `[3.3.0,3.4.0)` | 3.3 插件生态版本 |
| `pf4boot-plugin` | `[1.7.0,1.8.0)` | 当前辅助 Gradle 插件基线 |
| Spring Boot | `[2.7.0,2.8.0)` | 项目当前使用 2.7.x |
| Spring Framework | `[5.3.0,5.4.0)` | 随 Spring Boot 2.7.x |
| PF4J | `[3.15.0,3.16.0)` | 当前 `pf4j_version=3.15.0` |
| JDK | `[1.8,1.9)` | 源码和插件保持 Java 8 |
| 插件包格式 | `1` | zip 根目录含 `plugin.properties`，依赖位于 `lib/` |
| 描述符来源 | `plugin.properties` | manifest 描述符仍由加载器兼容，但官方 sample 当前使用 properties |

## 包校验规则

| 规则 | 级别 | 当前 sample 行为 |
| --- | --- | --- |
| `DESCRIPTOR_REQUIRED` | ERROR | `plugin.properties` 必须存在，并包含 `plugin.id`、`plugin.version`、`plugin.class` |
| `NO_HOST_API_BUNDLED` | ERROR | `lib/` 中不得包含 `pf4boot-api`、`pf4boot-core`、`pf4boot-jpa`、starter 等 host API |
| `CHECKSUM_PRESENT` | WARN | `.sha256` sidecar 缺失只警告，不阻断 sample |
| `TRUST_MANIFEST_PRESENT` | WARN | `.pf4boot-trust.json` sidecar 缺失只警告，不阻断 sample |

`WARN` 规则用于兼容历史包；生产环境可以在后续版本切换为 ENFORCE。

## E4.1 生产级兼容预检

sample 级 `verifySamplePluginPackages` 只负责构建期发现常见打包错误。生产级兼容判断接入 `PluginDeploymentService.planReplacement(...)`，覆盖 staged path 和 repository release 两条部署路径。

### Trust Manifest 字段

`.pf4boot-trust.json` 可声明以下版本范围字段：

| 字段 | 对比对象 | 默认实际值 |
| --- | --- | --- |
| `pf4bootVersionRange` | `spring.pf4boot.plugin-compatibility-pf4boot-version`，为空时使用 `systemVersion` | `0.0.0` |
| `springBootVersionRange` | `spring.pf4boot.plugin-compatibility-spring-boot-version`，为空时读取 Spring Boot 运行时版本 | 当前 Spring Boot |
| `pf4jVersionRange` | `spring.pf4boot.plugin-compatibility-pf4j-version` | `3.15.0` |
| `pf4bootPluginVersionRange` | `spring.pf4boot.plugin-compatibility-pf4boot-plugin-version` | `1.7.0` |
| `jdkVersionRange` | `spring.pf4boot.plugin-compatibility-jdk-version` | `1.8` |
| `packageFormatVersionRange` | `spring.pf4boot.plugin-compatibility-package-format-version` | `1` |

示例：

```json
{
  "pluginId": "sample-workflow",
  "pluginVersion": "3.3.0",
  "packageSha256": "replace-with-lowercase-sha256",
  "pf4bootVersionRange": "[3.3.0,3.4.0)",
  "pf4bootPluginVersionRange": "[1.7.0,1.8.0)",
  "springBootVersionRange": "[2.7.0,2.8.0)",
  "pf4jVersionRange": "[3.15.0,3.16.0)",
  "jdkVersionRange": "[1.8,1.9)",
  "packageFormatVersionRange": "[1,2)"
}
```

### 预检模式

通过 `spring.pf4boot.plugin-compatibility-precheck-mode` 控制：

| 模式 | 行为 |
| --- | --- |
| `DISABLED` | 不读取 trust manifest 中的兼容范围 |
| `WARN` | 版本范围不匹配时写入 `DeploymentCheckResult(WARN)`，部署计划仍可执行 |
| `ENFORCE` | 版本范围不匹配时写入 `DeploymentCheckResult(ERROR)`，部署计划不可执行 |

规则码采用稳定前缀：

| 规则码 | 含义 |
| --- | --- |
| `PF4BOOT_VERSION_RANGE_MISMATCH` | pf4boot 版本不满足 |
| `SPRING_BOOT_VERSION_RANGE_MISMATCH` | Spring Boot 版本不满足 |
| `PF4J_VERSION_RANGE_MISMATCH` | PF4J 版本不满足 |
| `PF4BOOT_PLUGIN_VERSION_RANGE_MISMATCH` | pf4boot-plugin 版本不满足 |
| `JDK_VERSION_RANGE_MISMATCH` | JDK 版本不满足 |
| `PACKAGE_FORMAT_VERSION_RANGE_MISMATCH` | 插件包格式版本不满足 |
| `PFC-004` | manifest 无法解析或版本范围表达式非法 |

生产环境建议先使用 `WARN` 观察一段时间，再切到 `ENFORCE`：

```yaml
spring:
  pf4boot:
    plugin-compatibility-precheck-mode: WARN
    plugin-compatibility-pf4boot-version: 3.3.0
    plugin-compatibility-pf4boot-plugin-version: 1.7.0
    plugin-compatibility-spring-boot-version: 2.7.22
    plugin-compatibility-pf4j-version: 3.15.0
    plugin-compatibility-jdk-version: 1.8
    plugin-compatibility-package-format-version: 1
```

## 机器可读报告

`samples:cross-plugin-jpa:demo-host:assembleSamplePlugins` 会在插件包组装后自动执行 `verifySamplePluginPackages`，输出：

```text
samples/cross-plugin-jpa/demo-host/build/reports/plugin-package-verification/result.json
```

报告结构：

```json
{
  "schemaVersion": 1,
  "generatedAt": 1781786835637,
  "state": "PASSED",
  "packages": [
    {
      "schemaVersion": 1,
      "pluginId": "sample-workflow",
      "pluginVersion": "3.3.0-SNAPSHOT",
      "packagePath": "samples/cross-plugin-jpa/demo-host/build/sample-plugins/plugin-workflow-3.3.0-SNAPSHOT.zip",
      "state": "PASSED",
      "bundledLibs": ["plugin-workflow-3.3.0-SNAPSHOT.jar"],
      "rules": []
    }
  ]
}
```

只要出现 ERROR 级失败，任务会失败；WARN 会进入报告但不阻断 sample。

## 验证命令

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

该命令同时验证：

- `pf4boot-plugin 1.7.0` 能生成插件 zip。
- sample 插件 descriptor 可读。
- sample 插件未误打包 host API。
- 包校验报告可被 CI 或后续 smoke 读取。

生产级兼容预检验证：

```powershell
.\gradlew.bat :pf4boot-core:test
```

该命令覆盖 trust manifest 中 pf4boot、Spring Boot、PF4J、pf4boot-plugin、JDK 和 package format 范围的 WARN/ENFORCE 行为。
