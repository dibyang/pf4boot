# 设计文档

此目录保存当前仍然有效的设计说明。中文文档为准，英文翻译版放在 [en/](en/)。

历史计划、验收记录和阶段性路线图已经移入 [archive/](archive/)，仅用于追溯，不作为当前实现依据。长期架构决策放在 [decisions/](decisions/)。

## 阅读入口

新参与开发时，建议按以下顺序阅读：

1. [architecture.md](architecture.md)：模块布局和运行时架构。
2. [plugin-loading-and-packaging.md](plugin-loading-and-packaging.md)：插件仓库、加载器、Gradle 插件打包和应用装配。
3. [plugin-lifecycle.md](plugin-lifecycle.md)：插件加载、启动、停止、重载和清理流程。
4. [context-and-bean-sharing.md](context-and-bean-sharing.md)：Spring 上下文层级、导出 Bean、扩展点和事件。
5. [plugin-developer-guide.md](plugin-developer-guide.md)：插件开发、依赖作用域、包校验、观测、JPA 和升级回滚指南。
6. [plugin-ecosystem-3.3-roadmap.md](plugin-ecosystem-3.3-roadmap.md)：3.3 插件生态路线图。
7. [plugin-developer-experience-3.3-design.md](plugin-developer-experience-3.3-design.md)：3.3 前三项插件开发者体验设计。
8. [plugin-developer-experience-3.3-plan.md](plugin-developer-experience-3.3-plan.md)：3.3 前三项实施规划与验收。
9. [plugin-ecosystem-3.3-late-stage-design.md](plugin-ecosystem-3.3-late-stage-design.md)：3.3 后三项插件生态设计。
10. [plugin-ecosystem-3.3-late-stage-plan.md](plugin-ecosystem-3.3-late-stage-plan.md)：3.3 后三项实施规划与验收。
11. [plugin-app-quickstart-template.md](plugin-app-quickstart-template.md)：快速搭建宿主应用和最小插件模板。

## 按主题阅读

### 基础框架

- [architecture.md](architecture.md)：整体架构。
- [plugin-lifecycle.md](plugin-lifecycle.md)：生命周期和清理边界。
- [context-and-bean-sharing.md](context-and-bean-sharing.md)：上下文和 Bean 共享。
- [plugin-loading-and-packaging.md](plugin-loading-and-packaging.md)：加载、仓库和打包。
- [plugin-compatibility-matrix.md](plugin-compatibility-matrix.md)：插件兼容矩阵和打包校验报告。
- [starter-boundary-split.md](starter-boundary-split.md)：starter 边界拆分。

### Web 与管理接口

- [web-integration.md](web-integration.md)：动态 MVC 映射、拦截器和插件静态资源。
- [plugin-http-management-api.md](plugin-http-management-api.md)：插件 HTTP 管理接口。
- [plugin-http-management-api-hardening.md](plugin-http-management-api-hardening.md)：管理接口加固。
- [decisions/plugin-management-console-boundary.md](decisions/plugin-management-console-boundary.md)：管理控制台与后端接口边界。

### JPA 与事务

- [jpa-integration.md](jpa-integration.md)：插件 JPA starter 行为和实体扫描规则。
- [jpa-plugin-owned-configuration-plan.md](jpa-plugin-owned-configuration-plan.md)：JPA 插件自治配置整改计划。
- [autoexport-jpa-boundary.md](autoexport-jpa-boundary.md)：AutoExport 分组和 JPA 动态元数据边界。
- [cross-plugin-jpa-transaction-capability.md](cross-plugin-jpa-transaction-capability.md)：跨插件 JPA 事务能力。
- [cross-plugin-jpa-transaction-improvement.md](cross-plugin-jpa-transaction-improvement.md)：跨插件 JPA 事务改进。
- [cross-plugin-jpa-transaction-complex-sample.md](cross-plugin-jpa-transaction-complex-sample.md)：复杂示例拆分。
- [cross-plugin-jpa-transaction-migration.md](cross-plugin-jpa-transaction-migration.md)：迁移指南和配置示例。
- [jpa-runtime-refresh.md](jpa-runtime-refresh.md)：JPA 运行时刷新。
- [jpa-runtime-refresh-drain-spi.md](jpa-runtime-refresh-drain-spi.md)：JPA 刷新的 drain SPI。
- [jpa-management-starter-boundary.md](jpa-management-starter-boundary.md)：JPA 管理 starter 可选边界。
- [decisions/cross-datasource-transaction-decision.md](decisions/cross-datasource-transaction-decision.md)：跨数据源事务边界决策。
- [decisions/jpa-runtime-refresh-decision.md](decisions/jpa-runtime-refresh-decision.md)：JPA runtime refresh 决策。

### 生产化与运维

- [next-version-production-goals.md](next-version-production-goals.md)：下一版本生产化目标。
- [next-version-production-design.md](next-version-production-design.md)：下一版本生产化实施设计。
- [plugin-hot-replacement-deployment-improvement.md](plugin-hot-replacement-deployment-improvement.md)：插件热替换部署改进。
- [runtime-safety-phase3.md](runtime-safety-phase3.md)：运行时安全增强。
- [verification-foundation.md](verification-foundation.md)：验证基础设施。
- [production-profile-and-release-gates.md](production-profile-and-release-gates.md)：生产 profile、信任链 ENFORCE 和仓库 release gate。
- [decisions/plugin-repository-governance-decision.md](decisions/plugin-repository-governance-decision.md)：插件仓库治理决策。

### 插件生态与开发体验

- [plugin-ecosystem-3.3-roadmap.md](plugin-ecosystem-3.3-roadmap.md)：3.3 六项目标路线图。
- [plugin-developer-experience-3.3-design.md](plugin-developer-experience-3.3-design.md)：官方开发指南、`pf4boot-plugin 1.7.0` 基线和模板/sample 梳理设计。
- [plugin-developer-experience-3.3-plan.md](plugin-developer-experience-3.3-plan.md)：3.3 前三项的实施阶段、验收和追踪。
- [plugin-ecosystem-3.3-late-stage-design.md](plugin-ecosystem-3.3-late-stage-design.md)：兼容矩阵、打包校验、插件仓库/分发和管理控制台 sample UI 设计。
- [plugin-ecosystem-3.3-late-stage-plan.md](plugin-ecosystem-3.3-late-stage-plan.md)：3.3 后三项的实施阶段、验收和追踪。
- [plugin-compatibility-matrix.md](plugin-compatibility-matrix.md)：3.3 兼容矩阵和 sample 插件包校验规则。
- [plugin-app-quickstart-template.md](plugin-app-quickstart-template.md)：快速应用模板、basic host/plugin 和 JPA 升级路径。

## 文档治理

- [document-governance.md](document-governance.md)：设计文档分层、归档和新增规则。
- [archive/README.md](archive/README.md)：历史文档索引。
- [decisions/README.md](decisions/README.md)：长期决策索引。

## 何时新增或更新设计文档

当变更影响以下内容时，请创建或更新设计文档：

- `pf4boot-api` 中的公共 API 或注解；
- `pf4boot-core` 中的插件生命周期、类加载、仓库、加载器或事件行为；
- starter 模块中的 Spring Boot 自动配置；
- Web 或 JPA 集成契约；
- Gradle 依赖作用域、插件打包或 sample host 装配；
- sample 应用和示例插件共享的行为。

小的机械修复可以在对话中写一个简短设计，但实现仍必须遵守 `docs/constraints/README.md`。

## 模板

```markdown
# 标题

## 问题

说明需要改变什么，以及为什么需要改变。

## 影响模块

- `module-name`：该模块在变更中的职责。

## 设计方案

说明目标行为、模块边界，以及重要的备选方案。

## 兼容性

说明源码、二进制、运行时、插件打包和配置兼容性影响。

## 验证

列出最小可用的 Gradle 命令和必要的手动检查。

## 未决问题

记录仍需确认的不确定点或决策。
```
