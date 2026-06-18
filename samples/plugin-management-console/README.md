# Plugin Management Console Sample

这是一个静态管理控制台示例，可直接打开构建后的 `index.html`，默认连接本地 `http://127.0.0.1:7791`。

## 模板定位

该 sample 是 3.3 官方 `management-client` 模板来源：

- 只消费 `/pf4boot/admin/**` 和只读 Actuator 响应。
- 不依赖 `pf4boot-core`、`pf4boot-management-starter` 的内部 Java 类。
- 用于验证管理 HTTP 契约是否足够支撑 UI/CLI/脚本。
- 不作为框架 starter 的内置 UI 发布。

可以复制的内容：

- token header、`X-Idempotency-Key` 和错误响应处理模式。
- 插件列表、生命周期操作、deployment plan/replace/confirm/rollback 的 HTTP 调用形状。
- repository release dry-run/replace 字段，包括 `repositoryVersion`、`repositoryVersionRange`、`repositoryRollback`。
- JPA reload plan/execute/current/record 的 HTTP 调用形状。
- Actuator 摘要读取和前端展示边界。

不建议直接复制的内容：

- 默认地址 `http://127.0.0.1:7791`。
- sample token、演示级样式和静态文件组织。
- 假设管理接口无反向代理或企业鉴权。

```powershell
.\gradlew.bat :samples:plugin-management-console:test
```

示例覆盖：

- 插件列表和 Actuator 摘要读取。
- 插件 start/stop/restart/reload/enable/disable。
- deployment plan/replace/confirm/rollback。
- repository release dry-run/replace。
- JPA reload plan/execute/current/record。
- 401/403、409、precheck failed、manual intervention 等统一错误展示。
- 写操作自动携带 token 和 `X-Idempotency-Key`。

## 3.3 后续目标

- 如需继续增强，可增加 headless UI smoke，把静态契约测试升级为真实浏览器交互验证。
- 保持 sample 或独立项目边界，不进入 core/starter 发布模块。
- 与 `docs/design/plugin-developer-experience-3.3-plan.md` 的 `management-client` 模板验收保持一致。
