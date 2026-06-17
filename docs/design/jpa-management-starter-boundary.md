# JPA 管理 starter 边界

## 问题

基础插件管理只需要插件查询、生命周期操作和部署编排。把 JPA reload HTTP/Actuator 能力放在 `pf4boot-management-starter` 或 `pf4boot-actuator` 中，会让不使用 JPA 的应用被动打包 `pf4boot-jpa`，也会让 CLI/控制台误以为 `plugin-jpa-reload` 是基础能力。

## 影响模块

- `pf4boot-management-starter`：只保留基础插件管理和 `plugin-deploy` 接口，不依赖 `pf4boot-jpa`。
- `pf4boot-actuator`：只保留插件快照、治理和基础 metrics，不依赖 `pf4boot-jpa`。
- `pf4boot-jpa-management-starter`：可选模块，承载 JPA reload HTTP 接口和 `pf4bootjpareload` Actuator 端点。
- `samples/cross-plugin-jpa`：显式依赖 `pf4boot-jpa-management-starter`，继续覆盖 JPA reload smoke。

## 设计方案

`pf4boot-management-starter` 的默认接口面只包含：

- `GET /pf4boot/admin/plugins`
- `GET /pf4boot/admin/plugins/{pluginId}`
- `POST /pf4boot/admin/plugins/{pluginId}/start`
- `POST /pf4boot/admin/plugins/{pluginId}/stop`
- `POST /pf4boot/admin/plugins/{pluginId}/restart`
- `POST /pf4boot/admin/plugins/{pluginId}/reload`
- `POST /pf4boot/admin/plugins/{pluginId}/enable`
- `DELETE /pf4boot/admin/plugins/{pluginId}/enable`
- `GET /pf4boot/admin/deployments`
- `GET /pf4boot/admin/deployments/{deploymentId}`
- `POST /pf4boot/admin/deployments/plan`
- `POST /pf4boot/admin/deployments/replace`
- `POST /pf4boot/admin/deployments/{deploymentId}/confirm`
- `POST /pf4boot/admin/deployments/{deploymentId}/rollback`

JPA reload 能力仅在应用显式引入 `pf4boot-jpa-management-starter` 时注册：

- `POST /pf4boot/admin/jpa/domains/{domainId}/reload/plan`
- `POST /pf4boot/admin/jpa/domains/{domainId}/reload`
- `GET /pf4boot/admin/jpa/reloads/{reloadId}`
- `GET /pf4boot/admin/jpa/domains/{domainId}/reload/current`
- `/actuator/pf4bootjpareload`

`pf4boot-jpa-management-starter` 复用基础管理模块的鉴权、请求工厂、审计和路径校验 Bean，但自身依赖 `pf4boot-jpa`。因此 Polarix 等非 JPA 管理场景只引入 `pf4boot-management-starter` 和按需的 `pf4boot-actuator`，不引入 `pf4boot-jpa-management-starter`，CLI 也不生成 `plugin-jpa-reload` 命令。

## 兼容性

这是依赖边界的破坏性收敛：仅依赖 `pf4boot-management-starter` 的应用不再获得 JPA reload HTTP 接口；仅依赖 `pf4boot-actuator` 的应用不再获得 `pf4bootjpareload` endpoint。需要 JPA reload 的应用必须显式增加 `pf4boot-jpa-management-starter`。

基础插件管理的 plugin list/start/stop/restart/reload/enable/disable 和 plugin-deploy 接口保持不变。

## 验证

- `.\gradlew.bat :pf4boot-management-starter:compileJava`
- `.\gradlew.bat :pf4boot-actuator:compileJava`
- `.\gradlew.bat :pf4boot-jpa-management-starter:test`
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava`

## 未决问题

- 是否为 JPA reload CLI 单独提供扩展包，而不是放入基础 CLI。
