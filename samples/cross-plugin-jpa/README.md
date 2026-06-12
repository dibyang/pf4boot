# 跨插件 JPA 复杂示例

该示例用于演示共享 JPA domain 下的跨插件事务协作，并保持清晰职责边界：

- `model-user-book`：只放用户、图书实体。
- `model-workflow-audit`：只放工作流审计实体。
- `plugin-demo-jpa-domain`：只提供 `domain.demo` 的 DataSource、EntityManagerFactory、TransactionManager。
- `plugin-user-book-service`：定义用户/图书 Repository 和导出的 `UserBookService`。
- `plugin-workflow`：通过 `UserBookService` 编排业务，定义自己的 audit Repository 和 HTTP 演示接口。
- `demo-host`：示例宿主应用和运行配置。
- `app-run`：示例运行时打包项目，组装 host 运行依赖、配置、启动脚本和插件 zip。

## 验证命令

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava `
  :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:plugin-demo-jpa-domain:pf4boot `
  :samples:cross-plugin-jpa:plugin-user-book-service:pf4boot `
  :samples:cross-plugin-jpa:plugin-workflow:pf4boot
```

运行时打包：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:sampleDistZip
```

产物：

- `samples/cross-plugin-jpa/app-run/build/runtime`
- `samples/cross-plugin-jpa/app-run/build/distributions/pf4boot-cross-plugin-jpa-sample-*.zip`

## HTTP smoke

宿主启动后可访问：

```text
GET /api/sample/workflow/place?username=alice&password=123&bookName=book-a
GET /api/sample/workflow/place?username=bob&password=123&bookName=book-b&failAfterAudit=true
GET /api/sample/workflow/summary
GET /api/sample/workflow/audit?username=alice
```

预期行为：

- 正常 `place` 会写入用户、图书和审计。
- `failAfterAudit=true` 会演示主事务失败路径：用户、图书随外层跨插件事务回滚；audit writer 使用独立 bean 承载 `REQUIRES_NEW`，审计记录独立提交。
- workflow 不直接注入 user-book 插件内部 Repository，只通过导出的 `UserBookService` 协作。

## 插件管理 HTTP API（示例）

示例宿主默认启用本地令牌模式（`LOCAL_TOKEN`）：

```yaml
spring:
  pf4boot:
    management:
      http:
        enabled: true
        mode: LOCAL_TOKEN
        token: ${PF4BOOT_ADMIN_TOKEN:sample-token}
        staging-root: build/sample-plugins
```

在 `http://127.0.0.1:7791` 上执行：

```bash
curl -H "X-PF4Boot-Admin-Token: sample-token" http://127.0.0.1:7791/pf4boot/admin/plugins

curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" \
  http://127.0.0.1:7791/pf4boot/admin/plugins/sample-workflow/start

curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" \
  http://127.0.0.1:7791/pf4boot/admin/plugins/sample-workflow/stop

curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" \
  -H "X-Idempotency-Key: deploy-plan-01" \
  -H "Content-Type: application/json" \
  -d '{"pluginId":"sample-workflow","stagedPluginPath":"build/sample-plugins/plugin-workflow-3.0.0-SNAPSHOT.zip"}' \
  http://127.0.0.1:7791/pf4boot/admin/deployments/plan

curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" \
  -H "Content-Type: application/json" \
  -d '{"pluginId":"sample-workflow","stagedPluginPath":"build/sample-plugins/plugin-workflow-3.0.0-SNAPSHOT.zip","dryRun":false}' \
  http://127.0.0.1:7791/pf4boot/admin/deployments/replace

curl -X GET -H "X-PF4Boot-Admin-Token: sample-token" \
  http://127.0.0.1:7791/pf4boot/admin/deployments
```

说明：`plan/replace` 会校验 `stagedPluginPath` 必须位于 `staging-root` 下，避免目录穿越。

### REMOTE_DELEGATED 示例

提供了一个远端鉴权示例实现（仅示例用途）：

- 实现类：`samples/cross-plugin-jpa/demo-host/src/main/java/net/xdob/sample/host/SampleRemoteManagementAuthorizer.java`
- 配置文件：`samples/cross-plugin-jpa/demo-host/src/main/resources/application-management-remote-sample.yml`

启用方式：启动时激活 `management-remote-sample` profile。

示例 token：

- `ops-token`：具备 `pf4boot:admin:all`
- `reader-token`：具备 `pf4boot:plugin:read`

建议在生产环境中用企业身份体系替换该示例类，迁移到正式 `REMOTE_DELEGATED` 鉴权模型。

## 验收状态

编译、打包、HTTP smoke、管理 API 示例流程已同步更新。验收记录见：

- `docs/design/en/plugin-http-management-api-acceptance.md`
