# 跨插件 JPA 复杂示例

该示例用于演示共享 JPA domain 下的跨插件事务协作，并保持清晰职责边界：

- `model-user-book`：只放用户、图书实体。
- `model-workflow-audit`：只放工作流审计实体。
- `plugin-demo-jpa-domain`：只提供 `domain.demo` 的 DataSource、EntityManagerFactory、TransactionManager。
- `plugin-user-book-service`：定义用户/图书 Repository 和导出的 `UserBookService`。
- `plugin-workflow`：通过 `UserBookService` 编排业务，定义自己的 audit Repository 和 HTTP 演示接口。
- `plugin-unrelated-service`：无 JPA 依赖的无关插件，用于验证 JPA provider/consumer 故障不会影响无关插件。
- `demo-host`：示例宿主应用和运行配置。
- `app-run`：示例运行时打包项目，组装 host 运行依赖、配置、启动脚本和插件 zip。

## 模板来源

该 sample 是 3.3 官方模板矩阵的主要来源：

| 模板 | 来源模块 | 可复制内容 | 不建议直接复制 |
| --- | --- | --- | --- |
| `service-plugin` | `plugin-unrelated-service` | 无 JPA 依赖的插件结构、健康检查、导出服务写法 | sample 端口、测试数据和演示命名 |
| `jpa-domain-plugin` | `plugin-demo-jpa-domain` | `JpaDomainDefinitionProvider`、domain provider 职责边界、starter 依赖方式 | 把业务 Repository、Controller 或 service 放进 provider |
| `jpa-consumer-plugin` | `plugin-user-book-service` | Repository 包扫描、共享 EMF/TM 绑定、导出业务 service | 创建本地 EMF/TM 或直接依赖其它插件内部 Repository |
| `workflow-plugin` | `plugin-workflow` | 跨插件 service 编排、外层事务回滚、audit 演示 | 跨插件注入内部 Repository、把跨数据源写入当成强事务 |
| `sample-host` | `demo-host` | 宿主启用插件管理、JPA 管理和 actuator 的配置方式 | `sample-token`、开放远程访问、演示用 H2 路径 |
| `runtime-package` | `app-run` | runtime 目录、插件 zip 收集、smoke 入口 | 把 `build/runtime` 当作源码模板 |

`build/`、`work/`、`logs/` 和历史版本 jar 都是生成产物，不是模板源码。

## 验证命令

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:compileJava `
  :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:plugin-demo-jpa-domain:pf4boot `
  :samples:cross-plugin-jpa:plugin-user-book-service:pf4boot `
  :samples:cross-plugin-jpa:plugin-workflow:pf4boot `
  :samples:cross-plugin-jpa:plugin-unrelated-service:pf4boot
```

运行时打包：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:sampleDistZip
```

产物：

- `samples/cross-plugin-jpa/app-run/build/runtime`
- `samples/cross-plugin-jpa/app-run/build/distributions/pf4boot-cross-plugin-jpa-sample-*.zip`

3.3 模板验收建议至少执行：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:plugin-unrelated-service:compileJava `
  :samples:cross-plugin-jpa:plugin-demo-jpa-domain:compileJava `
  :samples:cross-plugin-jpa:plugin-user-book-service:compileJava `
  :samples:cross-plugin-jpa:plugin-workflow:compileJava `
  :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

`assembleSamplePlugins` 会自动执行 `verifySamplePluginPackages`，生成插件包校验报告：

```text
samples/cross-plugin-jpa/demo-host/build/reports/plugin-package-verification/result.json
```

该报告检查 `plugin.properties` 必填字段、host API 是否被误打进插件包，以及 checksum/trust sidecar 是否存在。
任务会为 sample 插件 zip 自动生成同名旁路文件：

```text
*.zip.sha256
*.zip.pf4boot-trust.json
```

这些旁路文件使用示例信任根 `sample-release-key` 和兼容范围 `[3.3.0,3.4.0)`，用于演示生产 profile 下的信任链 ENFORCE。真实生产环境应由发布流水线生成 checksum、trust manifest 和签名字段。

## 运行日志

`app-run` 和 RPM 安装后的服务默认写文件日志：

```text
logs/pf4boot-cross-plugin-jpa-sample.log
```

RPM 安装路径下对应：

```text
/opt/pf4boot/cross-plugin-jpa-sample/logs/pf4boot-cross-plugin-jpa-sample.log
```

日志仍会输出到控制台，因此 systemd 环境也可以通过 `journalctl -u pf4boot-cross-plugin-jpa-sample -f` 查看。
可以通过启动前设置 `LOG_DIR` 或 `JAVA_OPTS` 调整日志目录和 JVM 参数。

示例 H2 数据库默认写入运行目录下的 `work/h2/`，不依赖 Linux 用户的 home 目录。

## HTTP smoke

宿主启动后可访问：

```text
GET /
GET /api/sample/workflow/place?username=alice&password=123&bookName=book-a
GET /api/sample/workflow/place?username=bob&password=123&bookName=book-b&failAfterAudit=true
GET /api/sample/workflow/summary
GET /api/sample/workflow/audit?username=alice
GET /api/sample/unrelated/health
```

## 插件控制台

示例宿主内置一个开箱即用的插件控制台，启动 `demo-host` 或 `app-run` runtime 后访问：

```text
http://127.0.0.1:7791/
```

该页面覆盖：

- 插件列表、状态摘要、依赖、插件路径、失败信息和 start/stop/restart/reload/enable/disable 操作。
- staged 插件部署预检、替换和部署记录查询。
- JPA reload plan、reload 执行和 current 记录查询。
- 正常下单、强制失败回滚、审计查询和无关插件健康检查，用于验证管理操作后的业务可用性。
- Actuator 治理摘要和 JPA reload 摘要。

控制台默认使用 sample 配置中的 `sample-token`，并随 sample host 一起打包。仓库中仍保留独立的
`samples/plugin-management-console` 静态管理控制台示例；`cross-plugin-jpa` 的内置控制台作为更完整的产品化演示入口。

注意：`sample-token`、`allow-loopback-only: false`、H2 文件库、固定端口 `7791` 都是演示配置。生产环境应替换为受控网络、强 token 或 `REMOTE_DELEGATED` 鉴权，并按实际部署目录设置 staging/cache 路径。

## 生产 profile 示例

示例宿主提供了生产 profile 配置：

```text
samples/cross-plugin-jpa/demo-host/src/main/resources/application-production.yml
samples/cross-plugin-jpa/app-run/config/application-production.yml
```

启用方式：

```powershell
$env:SPRING_PROFILES_ACTIVE="production"
```

生产 profile 会启用：

- `spring.pf4boot.production-profile-enabled=true`
- 插件包 checksum、trust manifest、compatibility、capability 和 repository trust 的 ENFORCE 有效模式
- `plugin-package-trust-roots: sample-release-key`
- repository release gate：`attributes.releaseGate=passed`
- 管理操作记录 file store 和 fail-closed

真实 repository replace 仍保持显式开关：

```powershell
$env:PF4BOOT_REPOSITORY_REPLACE_ENABLED="true"
```

不开该开关时，生产 profile 仍允许 repository plan/dry-run，但不会执行真实替换。

JPA domain 的实体扫描包不在宿主配置中声明。示例由 `plugin-demo-jpa-domain` 插件主类实现
`JpaDomainDefinitionProvider`，声明 entity packages、DataSource 和 ddl 策略，避免宿主替插件维护实体包清单。
业务插件在各自插件主类中实现 `JpaConsumerBindingProvider` 并返回 `JpaConsumerBinding.shared("demo")`；
宿主只保留 `spring.pf4boot.jpa.reload.*` 这类 JPA reload 管理配置，不替插件开启或绑定 JPA。

预期行为：

- 正常 `place` 会写入用户、图书和审计。
- `failAfterAudit=true` 会演示主事务失败路径：用户、图书随外层跨插件事务回滚；audit writer 使用独立 bean 承载 `REQUIRES_NEW`，审计记录独立提交。
- workflow 不直接注入 user-book 插件内部 Repository，只通过导出的 `UserBookService` 协作。
- unrelated 插件不依赖 `pf4boot-jpa-starter`、datasource provider 或 JPA consumer；provider 停止后仍应保持可用。

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
        allow-loopback-only: false
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
  -d '{"pluginId":"sample-workflow","stagedPluginPath":"build/sample-plugins/plugin-workflow-3.0.0.zip"}' \
  http://127.0.0.1:7791/pf4boot/admin/deployments/plan

curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" \
  -H "X-Idempotency-Key: repository-plan-01" \
  -H "Content-Type: application/json" \
  -d '{"pluginId":"sample-workflow","repositoryVersion":"3.0.0","dryRun":true}' \
  http://127.0.0.1:7791/pf4boot/admin/deployments/plan

curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" \
  -H "X-Idempotency-Key: repository-replace-01" \
  -H "Content-Type: application/json" \
  -d '{"pluginId":"sample-workflow","repositoryVersion":"3.0.0","dryRun":false}' \
  http://127.0.0.1:7791/pf4boot/admin/deployments/replace

curl -X POST -H "X-PF4Boot-Admin-Token: sample-token" \
  -H "Content-Type: application/json" \
  -d '{"pluginId":"sample-workflow","stagedPluginPath":"build/sample-plugins/plugin-workflow-3.0.0.zip","dryRun":false}' \
  http://127.0.0.1:7791/pf4boot/admin/deployments/replace

curl -X GET -H "X-PF4Boot-Admin-Token: sample-token" \
  http://127.0.0.1:7791/pf4boot/admin/deployments
```

说明：`plan/replace` 会校验 `stagedPluginPath` 必须位于 `staging-root` 下，避免目录穿越。

示例 UI 支持通过服务器 IP 远程访问，因此 sample 配置显式设置 `allow-loopback-only: false`。生产环境不要直接复用该演示配置，应改用受控网络、强 token 或 `REMOTE_DELEGATED` 鉴权。

离线仓库索引示例位于：

```text
samples/cross-plugin-jpa/repository/repository-index.example.json
```

示例中的 `packageSha256` 需要替换为实际插件 zip 的小写 sha256 后才能用于 dry-run 或真实 replace。生产 profile 下还要求 index 顶层 `signature` 存在、release 指向 trust manifest，并且 release `attributes.releaseGate` 等于 `passed`。真实 repository replace 默认关闭，需要显式配置 `spring.pf4boot.plugin-repository-replace-enabled=true`，并建议配置 `spring.pf4boot.plugin-repository-cache-directory`。

## Runtime smoke 脚本

可以用一条命令完成运行时打包、启动宿主、调用业务接口、调用本地 token 管理接口、检查 actuator
治理摘要和 metrics，并在结束时关闭宿主进程：

```powershell
powershell -ExecutionPolicy Bypass -File samples/cross-plugin-jpa/scripts/runtime-smoke.ps1
```

如果已经完成运行时打包，也可以跳过组装步骤，便于快速复测：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:assembleSampleRuntime
powershell -ExecutionPolicy Bypass -File samples\cross-plugin-jpa\scripts\runtime-smoke.ps1 -SkipAssemble
```

也可以通过 Gradle 入口运行，适合本地复验和 CI：

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

Gradle smoke 会生成机器可读报告：

```text
samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json
samples/cross-plugin-jpa/app-run/build/test-results/runtimeSmoke/TEST-runtime-smoke.xml
```

脚本会输出以下关键证据：

- `SMOKE_PLUGIN_ZIPS`：确认 domain、user-book、workflow、unrelated 插件 zip 已打包。
- `SMOKE_HOST_READY`：宿主已在本地端口就绪。
- `SMOKE_WORKFLOW_OK`：正常跨插件 JPA workflow 成功。
- `SMOKE_WORKFLOW_ROLLBACK`：强制失败 workflow 触发主事务回滚，审计记录保留。
- `SMOKE_UNRELATED_PLUGIN_ALIVE`：无关插件可访问，且 workflow rollback 后仍可访问。
- `SMOKE_MANAGEMENT_OPERATION`：使用 `X-PF4Boot-Admin-Token` 和 `X-Idempotency-Key` 调用管理接口。
- `SMOKE_IDEMPOTENCY_REPLAY`：重复幂等请求返回同一个 operation id。
- `SMOKE_JPA_RELOAD_DISABLED_NO_MUTATION`：JPA reload 禁用态下请求返回 `RELOAD_DISABLED`，且不影响无关插件。
- `SMOKE_JPA_RELOAD_PLAN`：JPA reload plan 接口返回 provider、consumer 和影响范围。
- `SMOKE_JPA_RELOAD_SUCCESS`：执行重启式 JPA domain 刷新后业务仍可访问。
- `SMOKE_JPA_RELOAD_IDEMPOTENCY`：重复 JPA reload 请求返回同一个 reload id。
- `SMOKE_JPA_RELOAD_DRAIN_SUCCESS`：成功 JPA reload record 包含 accepted drain report。
- `SMOKE_JPA_RELOAD_RECORD_PERSISTED`：成功 JPA reload 后重启宿主，Actuator 仍能从文件仓库读到 latest reload。
- `SMOKE_JPA_PROVIDER_REPLACEMENT_PATH`：JPA reload 通过 `providerReplacementPath` 调用 provider staged 包替换，并记录 replacement summary。
- `SMOKE_JPA_RELOAD_DRAIN_TIMEOUT_NO_MUTATION`：drain timeout 返回失败记录，且 workflow/unrelated 插件仍可访问。
- `SMOKE_JPA_PROVIDER_ISOLATION`：停止 JPA provider 后，无关插件仍可访问。
- `SMOKE_FAILURE_CASE`：有效插件包 + 不存在目标插件的部署预检返回失败响应，并可通过部署记录查询。
- `SMOKE_ACTUATOR_GOVERNANCE`：`/actuator/pf4bootgovernance` 和 management metrics 可读。
- `SMOKE_ACTUATOR_JPA_RELOAD`：`/actuator/pf4bootjpareload` 可读。
- `actuatorJpaReloadDrainSummary`：`runtimeSmoke` 报告中记录 Actuator JPA reload drain 摘要和文件记录仓库检查。
- `SMOKE_CLEANUP_OK`：脚本已关闭进程并清理临时运行目录。

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

- `docs/design/plugin-developer-guide.md`
- `docs/design/plugin-developer-experience-3.3-plan.md`
- `docs/design/archive/plugin-http-management-api-acceptance.md`
