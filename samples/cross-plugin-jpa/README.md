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

```text
samples/cross-plugin-jpa/app-run/build/runtime
samples/cross-plugin-jpa/app-run/build/distributions/pf4boot-cross-plugin-jpa-sample-*.zip
```

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

## 验收状态

编译、打包、包边界和 HTTP smoke 已通过。验收记录见 `docs/design/cross-plugin-jpa-transaction-complex-sample-acceptance.md`。
