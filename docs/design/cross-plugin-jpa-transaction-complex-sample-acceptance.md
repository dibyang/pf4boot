# 跨插件 JPA 复杂示例验收记录

## 1. 验收时间

- 日期：2026-06-11
- 范围：`samples/cross-plugin-jpa`

## 2. 结论

编译、插件打包、运行时分发包、包结构边界和运行态 HTTP smoke 已通过。shared JPA bridge 已能让消费插件在本地 BeanFactory 中暴露 `domain.demo.entityManagerFactory` 与 `domain.demo.transactionManager` BeanDefinition，service/workflow 插件的 Spring Data Repository 可以正常启动。

失败路径验收口径如下：

- `WorkflowServiceImpl.place()` 使用 `domain.demo.transactionManager` 包住跨插件主流程。
- `UserBookServiceImpl.registerUserWithBook()` 与 workflow 外层事务使用同一个 domain 事务管理器，强制异常时 user/book 会随外层事务回滚。
- `WorkflowAuditWriterImpl.append()` 明确使用独立 bean + `REQUIRES_NEW`，因此失败路径会保留审计记录，用于演示独立审计事务和事务代理边界。

## 3. 已通过项

| 项 | 结果 | 证据 |
| --- | --- | --- |
| sample 模块接入根构建 | 通过 | `settings.gradle` 已包含 `samples:cross-plugin-jpa:*` |
| 编译验证 | 通过 | `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins` |
| 插件打包验证 | 通过 | `assembleSamplePlugins` 产出 3 个插件 zip |
| 运行时分发包 | 通过 | `:samples:cross-plugin-jpa:app-run:sampleDistZip` 产出 `build/runtime` 和 `pf4boot-cross-plugin-jpa-sample-*.zip` |
| provider 职责单一 | 通过 | provider 源码不包含 entity、Repository、Controller、业务 service |
| entity 独立模型模块 | 通过 | `model-user-book`、`model-workflow-audit` |
| provider 包边界 | 通过 | provider zip 只携带自身 jar 与两个 model jar |
| consumer 包边界 | 通过 | service/workflow zip 不 bundle Spring/JPA/PF4J/`pf4boot-api` |
| 依赖元数据 | 通过 | service 依赖 domain；workflow 依赖 domain 和 service |
| shared JPA bridge | 通过 | `pf4boot-core` 动态共享 Bean 同步注册 BeanDefinition；`pf4boot-jpa-starter` 本地占位 BeanDefinition 可被 Spring Data JPA 识别 |
| HTTP smoke | 通过 | `OK_STATUS=200`，成功路径返回 `{"books":1,"audits":1,"users":1}`；失败路径返回 500 后 summary 为 `{"books":1,"audits":2,"users":1}` |
| 事务边界演示 | 通过 | 失败路径 user/book 回滚，`REQUIRES_NEW` audit 独立提交 |

## 4. 未完成项

| 项 | 结果 | 当前表现 | 后续处理 |
| --- | --- | --- | --- |
| provider 失败隔离运行验收 | 部分通过 | `PluginJPAStarterTest.providerMissingFailureDoesNotAffectUnrelatedNonJpaPluginContext` 覆盖 provider 缺失时 shared consumer 失败、无关非 JPA 插件上下文仍可启动；当前 sample 仍无无关插件运行态对照 | 后续可补 no-jpa/unrelated sample 插件做端到端运行态对照 |

## 5. 运行态发现

1. 插件不能 bundle `pf4boot-api` 或 PF4J，否则 `@PluginStarter` 注解类型会被插件私有 classloader 遮蔽。
2. 插件不能私有携带另一套 Spring Boot/Spring 自动配置类，否则会出现自动配置类型不一致。
3. 当前可运行边界是 host classpath 提供框架/JPA 基础库，provider 插件携带 model jar 并激活 domain。
4. Spring Data JPA 会递归扫描 parent BeanFactory 中的 `EntityManagerFactory`，并反查对应 BeanDefinition；平台导出的 JPA Bean 不能只有 singleton，也必须具备 BeanDefinition。
5. `REQUIRES_NEW` 示例是为了演示事务代理边界和审计留痕，不是默认业务推荐模式。

## 6. 验证命令与结果

### 编译与打包

```powershell
.\gradlew.bat :pf4boot-core:compileJava :pf4boot-core:test :pf4boot-jpa-starter:compileJava :pf4boot-jpa-starter:test
```

结果：通过。

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

结果：通过。

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:sampleDistZip
```

结果：通过，运行目录包含 `bin`、`config`、`lib`、`plugins`，分发 zip 已生成。

### HTTP smoke

```text
GET /api/sample/workflow/place?username=smoke-ok&password=123&bookName=BookA&failAfterAudit=false
=> 200 {"books":1,"audits":1,"users":1}

GET /api/sample/workflow/place?username=smoke-fail&password=123&bookName=BookB&failAfterAudit=true
=> 500

GET /api/sample/workflow/summary
=> {"books":1,"audits":2,"users":1}

GET /api/sample/workflow/audit?username=smoke-ok
=> 1 条审计

GET /api/sample/workflow/audit?username=smoke-fail
=> 1 条审计
```

说明：失败路径中 `smoke-fail` 的 user/book 未提交，审计因 `REQUIRES_NEW` 独立事务保留。
