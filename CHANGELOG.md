# Changelog

本文档记录 `pf4boot` 面向使用者的版本变化。详细发布说明见 `docs/release-notes/`。

## Unreleased

暂无。

## 3.2.0 - 2026-06-18

### Added

- 新增下一版本生产化目标和设计文档，明确 JPA 运行时刷新、热替换部署事务、HTTP 管理治理、资源清理诊断和 runtime smoke 验收的发布范围。
- 新增 `PluginCleanupSummary`，用于在部署记录和 JPA reload 记录中暴露资源清理摘要。
- JPA reload 管理接口新增独立权限点：`JPA_RELOAD_PLAN`、`JPA_RELOAD_EXECUTE`、`JPA_RELOAD_QUERY`。
- `PluginDeploymentService` 新增兼容 default 方法：`getRecord`、`rollback(String)` 和 `rollback(DeploymentRecord)`。

### Changed

- JPA reload 执行接口接入写请求安全校验、幂等、operation record、审计和失败记录。
- JPA provider replacement summary 新增 `errorCode` 字段，并保留旧构造器和旧记录兼容。
- `DefaultPluginDeploymentService` 支持部署记录查询和显式 rollback，并在热替换成功路径记录 cleanup summary。
- `samples/cross-plugin-jpa:app-run:runtimeSmoke` 扩展覆盖部署失败预检、JPA reload、provider replacement、drain timeout、record persistence、Actuator 汇总和无关插件隔离。

### Fixed

- `samples/cross-plugin-jpa:app-run:assembleSampleRuntime` 每次清理完整 runtime 输出目录，避免旧版本 jar 污染 `lib/*` 类路径。
- JPA reload 文件记录仓库补充 provider replacement summary、cleanup summary、latest 和 idempotency 恢复相关测试覆盖。

### Compatibility

- 继续保持 Java 8 兼容。
- 新增 JPA reload 管理权限会影响自定义 `PluginManagementAuthorizer`；默认本地 token authorizer 已放行。
- `PluginDeploymentService` 新增方法均为 default method，不破坏二进制兼容。
- `DeploymentRecord`、`JpaDomainReloadRecord`、`JpaProviderReplacementSummary` 新增字段均保持旧构造器兼容；旧 JSON 记录缺失新增字段时按 `null` 处理。

### Verification

发布前建议执行：

```powershell
.\gradlew.bat build
.\gradlew.bat :pf4boot-core:test :pf4boot-management-starter:test :pf4boot-jpa-starter:test :pf4boot-jpa-management-starter:test --rerun-tasks
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat publishToMavenLocal
```

## 3.1.0 - 2026-06-17

### Added

- 新增 `pf4boot-jpa-management-starter` 可选模块，用于显式启用 JPA reload HTTP 接口和 `pf4bootjpareload` Actuator 端点。

### Changed

- `pf4boot-management-starter` 和 `pf4boot-actuator` 不再强依赖 `pf4boot-jpa`；基础管理仅保留 plugin list/start/stop/restart/reload/enable/disable 与 plugin-deploy。
- 插件管理 HTTP 合同收敛为基础接口，CLI 默认不应生成 `plugin-jpa-reload` 命令。

### Removed

- 移除 `pf4boot-web-starter` 中旧的 `PluginManagerController` 管理接口，只保留 `pf4boot-management-starter` 提供的 `/pf4boot/admin/**` 新插件管理接口。

### Fixed

- `samples/cross-plugin-jpa` runtime smoke 不再写死插件 zip 版本号，改为从 runtime 插件目录动态解析当前构建产物。

## 3.0.0 - 2026-06-17

### Added

- 新增 `pf4boot-management-starter`，提供插件查询、生命周期、部署预检、替换、确认、回滚和 JPA reload HTTP 管理接口。
- 新增管理接口鉴权、幂等、限流、CSRF/Origin 校验、审计记录、统一响应体和稳定错误码。
- 新增插件部署记录、operation store 和部署历史查询能力，支持内存与文件型存储。
- 新增 JPA domain reload 计划、执行、记录查询、drain 协调、provider replacement path 和文件型 reload 记录存储。
- 新增 `pf4boot-actuator` 治理、部署、管理接口和 JPA reload 观测能力。
- 新增 offline-index 插件仓库解析、release request、checksum/trust manifest 和版本范围预检相关模型。
- 新增 `samples/cross-plugin-jpa`，覆盖跨插件 JPA domain、consumer、workflow、管理接口和 runtime smoke。
- 新增 `samples/saga-outbox`，展示 Saga/Outbox 事务补偿示例。
- 新增 `samples/plugin-management-console`，作为插件管理 HTTP API 的前端对接示例。
- 新增插件管理 HTTP 接口合同文档：`docs/design/plugin-management-http-api-contract.md`。

### Changed

- 发布范围收敛为框架模块，`samples/*` 项目不发布到 Maven 仓库。
- 插件治理文档与 sample 示例版本更新为正式版 `3.0.0`。
- `samples/cross-plugin-jpa` runtime smoke 使用正式版插件包名验证管理部署和 JPA provider replacement。

### Compatibility

- 保持 Java 8 兼容。
- HTTP 管理接口默认关闭，必须显式配置 `spring.pf4boot.management.http.enabled=true` 并选择非 `DISABLED` 模式。
- JPA reload 执行受治理配置控制，默认不会在未启用执行模式时产生运行时变更。

### Verification

发布前建议执行：

```powershell
.\gradlew.bat build
.\gradlew.bat :pf4boot-core:test :pf4boot-jpa-starter:test :pf4boot-jpa-domain-starter:test :pf4boot-management-starter:test :pf4boot-actuator:test --rerun-tasks
.\gradlew.bat :samples:plugin-management-console:test --rerun-tasks
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat :samples:saga-outbox:app-run:runtimeSmoke
.\gradlew.bat publishToMavenLocal
```
