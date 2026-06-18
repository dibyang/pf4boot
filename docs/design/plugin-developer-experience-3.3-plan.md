# 插件开发者体验 3.3 实施规划与验收

## 范围

本文只追踪 3.3 前三项目标：

1. 官方插件开发指南重写。
2. `pf4boot-plugin 1.7.0` 能力基线对齐。
3. 官方模板/复杂 samples 梳理。

3.3 后三项（兼容矩阵和打包校验、插件仓库/分发设计、管理控制台 sample UI）已纳入 [plugin-ecosystem-3.3-roadmap.md](plugin-ecosystem-3.3-roadmap.md)，但不在本文展开实施。

## 阶段计划

| 阶段 | 目标 | 交付物 | 状态 |
| --- | --- | --- | --- |
| P0 | 规划冻结 | 路线图、设计、规划和英文翻译 | 已完成 |
| P1 | 开发指南结构重写 | `plugin-developer-guide.md` 新结构和英文翻译 | 已完成 |
| P2 | 1.7.0 能力基线表 | 能力表、sample 使用点、验证命令 | 已完成 |
| P3 | 模板矩阵落地 | 开发指南模板矩阵、sample README 模板说明 | 已完成 |
| P4 | sample 文档清理 | `cross-plugin-jpa`、`saga-outbox`、console README 对齐 | 已完成 |
| P5 | 验收收敛 | 文档链接、编码检查、sample 验证命令 | 已完成 |

## P1 官方插件开发指南重写

### 修改文件

- `docs/design/plugin-developer-guide.md`
- `docs/design/en/plugin-developer-guide.md`
- 必要时更新 `docs/design/plugin-loading-and-packaging.md` 和英文翻译中的交叉引用。

### 必做内容

- 增加快速开始路径。
- 按插件类型拆章节：service、web、JPA domain、JPA consumer、workflow、management client。
- 增加依赖作用域决策表和反例。
- 增加 JPA 分层和多 domain 包扫描说明。
- 增加管理、热替换、JPA reload 和 package verification 的运维入口。
- 增加故障排查表。

### 验收

| 验收项 | 标准 |
| --- | --- |
| 指南结构 | 新开发者能从快速开始进入具体插件类型 |
| 作用域说明 | 每种依赖作用域都有适用场景和禁止场景 |
| JPA 说明 | 明确 provider 不放 entity/repository/service，consumer 按包绑定 EMF/TM |
| 运维说明 | 管理 API、热替换、rollback、JPA reload 都有入口链接 |
| 英文同步 | 英文翻译和中文结构一致 |

## P2 `pf4boot-plugin 1.7.0` 能力基线对齐

### 修改文件

- `docs/design/plugin-developer-guide.md`
- `docs/design/plugin-loading-and-packaging.md`
- `docs/design/en/*` 对应翻译
- sample `build.gradle` 仅在发现旧写法或误导写法时修改。

### 必做内容

- 增加能力基线表。
- 确认根 `build.gradle` 的 `pf4boot-plugin` 版本为 `1.7.0`。
- 列出官方 sample 应用 `net.xdob.pf4boot-plugin` 的位置。
- 说明 `plugin.properties` 与 Gradle DSL 的推荐关系和兼容关系。
- 明确 1.7.0 若存在模板/校验任务，文档优先引用；若本仓库无法确认，标记待确认。

### 验收

| 验收项 | 标准 |
| --- | --- |
| 版本基线 | 文档明确 3.3 基线是 `pf4boot-plugin 1.7.0` |
| 使用点 | sample 插件工程声明可被 `rg` 检查 |
| 能力边界 | 已确认事实和待确认能力不混写 |
| 外部边界 | 不修改 `pf4boot-plugin` 外部仓库 |

## P3 官方模板/复杂 samples 梳理

### 修改文件

- `docs/design/plugin-developer-guide.md`
- `samples/cross-plugin-jpa/README.md`
- `samples/saga-outbox/README.md`
- `samples/plugin-management-console/README.md`
- 英文翻译视仓库现状补充到 `docs/design/en/`，sample README 如无英文版本可先不新增。

### 必做内容

- 在开发指南加入模板矩阵。
- 在 `cross-plugin-jpa` README 标明各模块对应模板。
- 在 `saga-outbox` README 标明它是补偿一致性 sample，不是 XA。
- 在 `plugin-management-console` README 标明它是管理 API consumer sample。
- 标出 demo-only 代码和可复制模板代码。
- 每个 sample README 给出最小验证命令。

### 验收

| 验收项 | 标准 |
| --- | --- |
| 模板矩阵 | 6 类模板都有来源、依赖和验证命令 |
| sample 职责 | 每个 sample README 说明目标和非目标 |
| demo-only 标识 | 不适合复制的运行脚本、硬编码端口、测试数据被明确标出 |
| 验证命令 | 文档中的命令能在当前仓库执行或明确说明依赖 |

## P4 sample 文档清理

### 必做内容

- 清理 sample README 中过时版本号、旧模块名称和旧根级 demo 引用。
- 确保 `samples/*/build` 产物不会被 README 当作源码路径引用。
- 检查 `settings.gradle` 中 sample 模块与 README 清单一致。

### 验收

```powershell
rg -n "demo-app|demo-lib|plugin1|plugin2|root-level|根级" docs samples -g "*.md"
```

结果中不得出现仍作为当前示例入口的旧根级 demo 描述。

## P5 验收收敛

### 必跑检查

```powershell
git diff --check
Select-String -Path docs\design\*.md,docs\design\en\*.md,samples\*\README.md -Pattern ([char]0xFFFD) -Encoding UTF8
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:plugin-management-console:test
```

### 建议检查

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat :samples:saga-outbox:app-run:runtimeSmoke
```

## 完成定义

E1-E3 完成必须满足：

1. `plugin-developer-guide.md` 已按任务路径重写。
2. `pf4boot-plugin 1.7.0` 能力基线表已完成。
3. 官方模板矩阵已进入开发指南。
4. 三个 sample README 已说明模板来源、目标、非目标和验证命令。
5. 中文和英文设计文档同步。
6. 必跑检查通过；若某个 Gradle 任务因环境失败，最终说明中记录命令、失败原因和下一步。

## 当前落地状态

| 项目 | 状态 | 证据 |
| --- | --- | --- |
| 3.3 路线图 | 已完成 | `plugin-ecosystem-3.3-roadmap.md` 和英文翻译 |
| 前三项设计 | 已完成 | `plugin-developer-experience-3.3-design.md` 和英文翻译 |
| 前三项规划 | 已完成 | 本文和英文翻译 |
| 开发指南重写 | 已完成 | `plugin-developer-guide.md` 已按快速开始、1.7.0 基线、模板矩阵、插件类型、JPA、运维和排障重写 |
| 1.7.0 基线 | 已完成 | 开发指南记录根构建 `pf4boot-plugin:1.7.0` 和 sample 使用点 |
| sample 梳理 | 已完成 | `samples/cross-plugin-jpa/README.md`、`samples/saga-outbox/README.md`、`samples/plugin-management-console/README.md` 已补模板定位 |
| 验收收敛 | 已完成 | `git diff --check`、UTF-8 替换字符检查、`assembleSamplePlugins`、console test、两个 runtime smoke 均已通过 |

## 已执行验收

```powershell
git diff --check
Select-String -Path docs\design\*.md,docs\design\en\*.md,samples\*\README.md -Pattern ([char]0xFFFD) -Encoding UTF8
.\gradlew.bat :samples:cross-plugin-jpa:demo-host:assembleSamplePlugins
.\gradlew.bat :samples:plugin-management-console:test
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
.\gradlew.bat :samples:saga-outbox:app-run:runtimeSmoke
```
