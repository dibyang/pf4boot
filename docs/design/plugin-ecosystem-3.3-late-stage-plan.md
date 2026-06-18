# 插件生态 3.3 后三项实施规划与验收

## 范围

本文追踪 3.3 后三项：

1. E4 兼容矩阵和打包校验。
2. E5 插件仓库/分发设计。
3. E6 管理控制台 sample UI。

设计见 [plugin-ecosystem-3.3-late-stage-design.md](plugin-ecosystem-3.3-late-stage-design.md)。

## 阶段计划

| 阶段 | 目标 | 交付物 | 状态 |
| --- | --- | --- | --- |
| P0 | 后三项设计冻结 | 中文/英文设计、规划、入口索引 | 已完成 |
| P1 | E4 兼容矩阵文档 | 兼容矩阵格式、规则清单、WARN/ENFORCE 语义 | 已完成 |
| P2 | E4 打包校验原型 | sample 插件包校验报告、host API 误打包检测 | 已完成 |
| P2.1 | E4.1 生产级兼容预检 | trust manifest 兼容范围接入部署 precheck，支持 WARN/ENFORCE | 已完成 |
| P3 | E5 离线仓库 dry-run | index schema、resolver、repository release 到 deployment plan | 已完成 |
| P4 | E5 repository replace 接入 | repository release 复制到 cache/staging 后复用 replace/rollback | 已完成 |
| P5 | E6 console sample 补齐 | replace、rollback、JPA reload、审计/错误展示 | 已完成 |
| P6 | 后三项 smoke 收敛 | 包校验、repository dry-run/replace、console UI smoke | 已验证 |

## P1 E4 兼容矩阵文档

### 修改文件

- `docs/design/plugin-loading-and-packaging.md`
- `docs/design/plugin-developer-guide.md`
- 新增或补充兼容矩阵文档。
- 英文翻译同步。

### 必做内容

- 定义 `pf4boot`、`pf4boot-plugin`、Spring Boot、PF4J、JDK、插件包格式的矩阵字段。
- 定义 WARN/ENFORCE 切换策略。
- 明确旧插件包如何被兼容、警告或拒绝。

### 验收

- 开发者能从文档判断某个插件包是否适配当前 host。
- 文档明确 `pf4boot-plugin 1.7.0` 是 3.3 当前基线。

## P2 E4 打包校验原型

### 可能修改范围

- sample Gradle 任务或脚本。
- `samples/cross-plugin-jpa` 校验报告目录。
- 后续如需要框架公共模型，再进入 `pf4boot-api` 或 `pf4boot-core`。

### 必做内容

- 解包插件 zip，读取 descriptor 和 `lib/`。
- 检查 host API 是否被打包。
- 检查 checksum/trust manifest 是否存在。
- 输出机器可读 JSON 报告。

### 验收

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
```

并能生成或检查包校验报告；错误样例必须能触发明确规则名。

### 落地结果

- `samples/cross-plugin-jpa/demo-host` 新增 `verifySamplePluginPackages`。
- `assembleSamplePlugins` 组装后自动输出 `build/reports/plugin-package-verification/result.json`。
- 当前规则覆盖 descriptor 必填字段、host API 误打包、checksum sidecar 和 trust manifest sidecar。
- ERROR 规则失败会阻断构建，WARN 规则进入报告但不阻断 sample。

## P2.1 E4.1 生产级兼容预检

### 必做内容

- trust manifest 支持声明 pf4boot、pf4boot-plugin、Spring Boot、PF4J、JDK、package format 版本范围。
- 部署预检根据 `plugin-compatibility-precheck-mode` 输出 WARN 或 ERROR。
- staged path 和 repository release 最终都通过 `PluginDeploymentService.planReplacement(...)`，不得绕过兼容预检。

### 落地结果

- `PluginTrustManifest` 新增 `pf4jVersionRange`、`pf4bootPluginVersionRange`、`jdkVersionRange`、`packageFormatVersionRange`。
- `DefaultPluginDeploymentService` 在兼容预检中统一校验上述范围。
- `Pf4bootProperties` 增加实际版本配置项，默认对齐 3.3 基线。
- `.\gradlew.bat :pf4boot-core:test` 已覆盖 WARN/ENFORCE 行为。

## P3 E5 离线仓库 dry-run

### 可能修改范围

- `pf4boot-core` repository resolver。
- `pf4boot-management-starter` deployment request 解析。
- `samples/cross-plugin-jpa/repository/repository-index.example.json`。

### 必做内容

- 解析 `repository-index.json`。
- 按 `repositoryVersion` 或 `repositoryVersionRange` 解析 release。
- 校验包路径必须在 repository root 下。
- dry-run 时生成 deployment plan，不修改运行态。

### 验收

- release not found 返回稳定错误。
- 路径越界被拒绝。
- checksum mismatch 被拒绝或 WARN，取决于配置。
- 直接 staged path 的旧部署请求仍可用。

### 落地结果

- `pf4boot-core` 已提供离线仓库 resolver，支持 `repository-index.json`、`repositoryVersion`、`repositoryVersionRange`、路径越界拒绝和 checksum 校验。
- `pf4boot-management-starter` 的部署请求已支持 repository release dry-run。
- `samples/cross-plugin-jpa/repository/repository-index.example.json` 已更新到 3.3.0 示例，并写入兼容矩阵标识。

## P4 E5 repository replace 接入

### 必做内容

- 把 release 包复制到受控 cache/staging。
- 复用 `PluginDeploymentService.replace(...)`。
- deployment record 写入 repository id、release version 和安全摘要。
- rollback 选择 release rollback candidate。

### 验收

- repository dry-run 与 replace 使用同一 plan/precheck 逻辑。
- replace 失败复用既有 rollback。
- HTTP 响应不暴露 cache 绝对路径。

### 落地结果

- `PluginDeploymentService` 已提供 repository release 版本的 `planReplacement` 与 `replace`。
- repository release 会进入受控 cache/staging 后复用既有 replace/precheck/rollback 逻辑。
- 管理接口通过 `repositoryVersion`、`repositoryVersionRange`、`repositoryRollback` 字段接入该能力。

## P5 E6 console sample 补齐

### 修改范围

- `samples/plugin-management-console`。
- 必要时更新 `samples/cross-plugin-jpa` 内置控制台说明。
- 不修改 core/starter 发布模块。

### 必做内容

- 插件列表和详情。
- deployment plan、replace、confirm、rollback。
- repository release dry-run。
- JPA reload plan、execute、record/current。
- Actuator governance/JPA reload 摘要。
- 401/403、409、precheck failed、manual intervention 展示。

### 验收

```powershell
.\gradlew.bat :samples:plugin-management-console:test
```

后续如果有 headless UI smoke，再补：

```powershell
.\gradlew.bat :samples:plugin-management-console:uiSmoke
```

### 落地结果

- 静态 sample UI 已补齐插件列表、生命周期操作、部署 plan/replace/confirm/rollback、repository release dry-run/replace、JPA reload、Actuator 摘要和统一错误展示。
- 契约测试覆盖关键路径、token header、`X-Idempotency-Key`、repository 字段和禁止 `localStorage` 持久化 token。
- 当前没有引入 starter 内置 UI，仍保持独立 sample 边界。

## P6 后三项 smoke 收敛

### 必跑命令

```powershell
git diff --check
Select-String -Path docs\design\*.md,docs\design\en\*.md,samples\*\README.md -Pattern ([char]0xFFFD) -Encoding UTF8
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat :samples:plugin-management-console:test
```

### 本轮验证结果

- `.\gradlew.bat :pf4boot-core:test`：通过。
- `.\gradlew.bat :pf4boot-management-starter:test`：通过。
- `.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins`：通过，生成包校验报告。
- `.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke`：通过。
- `.\gradlew.bat :samples:plugin-management-console:test`：通过。

### 建议命令

```powershell
.\gradlew.bat :samples:saga-outbox:app-run:runtimeSmoke
```

## 完成定义

E4-E6 完成必须满足：

1. 兼容矩阵可指导插件开发者判断可运行范围。
2. 官方 sample 插件包能输出或通过包校验报告。
3. repository release dry-run 和 replace 复用部署服务，不绕过 precheck/rollback。
4. 管理控制台 sample UI 覆盖部署、JPA reload、Actuator 摘要和错误展示。
5. 所有新增中文设计同步英文翻译。
6. 必跑命令通过；如环境失败，记录命令、原因和下一步。
