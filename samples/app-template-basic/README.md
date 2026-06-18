# PF4Boot Basic App Template

这是一个最小可复制应用模板，包含一个宿主应用和一个 hello 插件。

## 模块

| 模块 | 用途 |
| --- | --- |
| `host` | Spring Boot 宿主，启用 pf4boot、Web 插件、管理接口和 Actuator |
| `plugin-hello` | 最小 Web 插件，暴露 `/api/template/hello` |

## 快速运行

```powershell
.\gradlew.bat :samples:app-template-basic:host:assembleTemplatePlugins
.\gradlew.bat :samples:app-template-basic:host:runTemplateHost
```

运行后访问：

```text
http://127.0.0.1:7788/api/template/hello
http://127.0.0.1:7788/actuator/pf4bootplugins
```

管理接口默认 token：

```text
sample-token
```

生产复制时请改为环境变量：

```powershell
$env:PF4BOOT_ADMIN_TOKEN="your-token"
```

## 复制到业务项目

1. 复制 `host` 作为业务宿主。
2. 复制 `plugin-hello` 作为第一个业务插件。
3. 修改包名、插件 id、插件 class 和接口路径。
4. 保留宿主中的 `plugins-root`、管理接口 token 和 Actuator 配置。
5. 有 JPA 需求时，以 `samples/cross-plugin-jpa` 为模板来源，不要把实体放进数据源 provider 插件。

