# 插件应用快速搭建模板

## 背景

3.3 已经补齐插件开发指南、复杂 sample、管理接口、离线仓库、兼容预检和控制台 sample。新的问题是：首次接入者仍需要从多个 sample 中拼装宿主依赖、插件包目录、管理接口配置和最小插件结构。快速搭建模板用于提供一个可复制、可运行、可删减的起点。

## 目标

1. 提供一个最小宿主应用模板，复制后能快速启用 pf4boot、Web 插件、管理接口和 Actuator。
2. 提供一个最小插件模板，展示插件 descriptor、`Pf4bootPlugin`、`@SpringBootPlugin` 和 Web Controller。
3. 给 JPA 场景提供明确升级路径，复用 `samples/cross-plugin-jpa`，不复制第二套复杂 JPA 示例。
4. 给后续脚手架或外部模板仓库沉淀稳定模块边界。

## 非目标

- 不做命令行生成器。
- 不引入新的发布模块。
- 不把 sample UI 内置到 starter。
- 不复制完整跨插件 JPA 示例。

## 模块设计

| 模块 | 职责 | 可复制性 |
| --- | --- | --- |
| `samples:app-template-basic:host` | 最小 Spring Boot 宿主，启用 pf4boot/web/management/actuator | 可直接复制成业务宿主 |
| `samples:app-template-basic:plugin-hello` | 最小 Web 插件，提供 `/api/template/hello` | 可复制成业务插件 |
| `samples:cross-plugin-jpa` | JPA 升级模板来源 | 按 README 选择性复制 |

## 宿主默认能力

- `pf4boot-starter`
- `pf4boot-web-starter`
- `pf4boot-management-starter`
- `pf4boot-actuator`
- `spring-boot-starter-web`
- `plugins-root` 指向模板构建出的插件目录
- 管理接口默认使用本地 token，token 来自 `PF4BOOT_ADMIN_TOKEN`，未设置时为 `sample-token`

## 快速路径

1. 构建插件包：

```powershell
.\gradlew.bat :samples:app-template-basic:host:assembleTemplatePlugins
```

2. 运行宿主：

```powershell
.\gradlew.bat :samples:app-template-basic:host:runTemplateHost
```

3. 验证插件接口：

```text
GET http://127.0.0.1:7788/api/template/hello
GET http://127.0.0.1:7788/actuator/pf4bootplugins
```

## JPA 升级路径

需要 JPA 时，不从 basic 模板直接堆功能，而是以 `samples/cross-plugin-jpa` 作为模板来源：

1. 复制 `demo-host` 中 JPA starter 和管理 starter 配置。
2. 复制一个 model module，用于放实体和值对象。
3. 复制一个 domain provider 插件，用于提供数据源、EMF、事务管理器和 JPA domain descriptor。
4. 复制一个 consumer 插件，用于放 Repository 和业务服务。
5. 保持实体、Repository 按包分组，避免多个数据源实体混放。

## 兼容影响

新增模块全部位于 `samples/*`，不会改变发布模块 API。`settings.gradle` 增加 sample 模块仅影响本仓库构建。

## 验证计划

```powershell
.\gradlew.bat :samples:app-template-basic:host:compileJava
.\gradlew.bat :samples:app-template-basic:plugin-hello:pf4boot
.\gradlew.bat :samples:app-template-basic:host:assembleTemplatePlugins
```

可选运行验证：

```powershell
.\gradlew.bat :samples:app-template-basic:host:runTemplateHost
```

