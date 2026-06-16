# Plugin Management Console Sample

这是一个静态管理控制台示例，可直接打开构建后的 `index.html`，默认连接本地 `http://127.0.0.1:7791`。

```powershell
.\gradlew.bat :samples:plugin-management-console:test
```

示例覆盖：

- 插件列表和 Actuator 摘要读取。
- 插件 start/stop。
- deployment plan。
- JPA reload plan/reload。
- 写操作自动携带 token 和 `X-Idempotency-Key`。
