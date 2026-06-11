# 设计文档

此目录用于保存非平凡变更编码前的设计说明。根目录下的设计文档以中文为准，英文翻译版放在 [en/](en/)。

## 现有设计

- [architecture.md](architecture.md)：模块布局和运行时架构。
- [plugin-lifecycle.md](plugin-lifecycle.md)：插件加载、启动、停止、重载和清理流程。
- [context-and-bean-sharing.md](context-and-bean-sharing.md)：Spring 上下文层级、导出 Bean、扩展点和事件。
- [web-integration.md](web-integration.md)：动态 MVC 映射、拦截器和插件静态资源。
- [jpa-integration.md](jpa-integration.md)：插件 JPA starter 行为和实体扫描规则。
- [cross-plugin-jpa-transaction-capability.md](cross-plugin-jpa-transaction-capability.md)：跨插件 JPA 事务能力设计与落地计划。
- [cross-plugin-jpa-transaction-capability-plan.md](cross-plugin-jpa-transaction-capability-plan.md)：跨插件 JPA 事务能力实施计划（追踪版）。
- [cross-plugin-jpa-transaction-capability-acceptance.md](cross-plugin-jpa-transaction-capability-acceptance.md)：跨插件 JPA 事务能力验收清单（追踪版）。
- [cross-plugin-jpa-transaction-migration.md](cross-plugin-jpa-transaction-migration.md)：跨插件 JPA 事务迁移指南和配置示例。
- [cross-plugin-jpa-transaction-complex-sample.md](cross-plugin-jpa-transaction-complex-sample.md)：跨插件 JPA 复杂示例拆分方案。
- [cross-plugin-jpa-transaction-complex-sample-plan.md](cross-plugin-jpa-transaction-complex-sample-plan.md)：跨插件 JPA 复杂示例实施规划。
- [cross-plugin-jpa-transaction-complex-sample-acceptance.md](cross-plugin-jpa-transaction-complex-sample-acceptance.md)：跨插件 JPA 复杂示例验收记录。
- [cross-plugin-jpa-transaction-improvement.md](cross-plugin-jpa-transaction-improvement.md)：跨插件 JPA 事务现状改进方案。
- [cross-plugin-jpa-transaction-improvement-plan.md](cross-plugin-jpa-transaction-improvement-plan.md)：跨插件 JPA 事务改进实施规划。
- [plugin-hot-replacement-deployment-improvement.md](plugin-hot-replacement-deployment-improvement.md)：插件热替换部署现状改进方案。
- [plugin-hot-replacement-deployment-improvement-plan.md](plugin-hot-replacement-deployment-improvement-plan.md)：插件热替换部署改进实施规划。
- [plugin-hot-replacement-deployment-acceptance.md](plugin-hot-replacement-deployment-acceptance.md)：插件热替换部署验收记录。
- [plugin-loading-and-packaging.md](plugin-loading-and-packaging.md)：插件仓库、加载器、Gradle 插件打包和应用装配。
- [code-quality-fixes.md](code-quality-fixes.md)：本轮代码质量检查发现的问题和修复方案。
- [lifecycle-cleanup-fix.md](lifecycle-cleanup-fix.md)：插件停止和上下文清理职责边界修复方案。
- [scheduler-sharingbeans-fix.md](scheduler-sharingbeans-fix.md)：自动启动调度幂等和共享 Bean 记录 key 修复方案。
- [autoexport-jpa-boundary.md](autoexport-jpa-boundary.md)：AutoExport 分组和 JPA 动态元数据能力边界。
- [production-readiness-roadmap.md](production-readiness-roadmap.md)：验证闭环、观测诊断、JPA 边界、插件治理和文档体验的生产化完善路线图。
- [plugin-developer-guide.md](plugin-developer-guide.md)：插件开发、依赖作用域、包校验、只读观测、JPA 和升级回滚指南。

英文翻译版：

- [en/architecture.md](en/architecture.md)
- [en/plugin-lifecycle.md](en/plugin-lifecycle.md)
- [en/context-and-bean-sharing.md](en/context-and-bean-sharing.md)
- [en/web-integration.md](en/web-integration.md)
- [en/jpa-integration.md](en/jpa-integration.md)
- [en/cross-plugin-jpa-transaction-capability.md](en/cross-plugin-jpa-transaction-capability.md)
- [en/cross-plugin-jpa-transaction-capability-plan.md](en/cross-plugin-jpa-transaction-capability-plan.md)
- [en/cross-plugin-jpa-transaction-capability-acceptance.md](en/cross-plugin-jpa-transaction-capability-acceptance.md)
- [en/cross-plugin-jpa-transaction-migration.md](en/cross-plugin-jpa-transaction-migration.md)
- [en/cross-plugin-jpa-transaction-complex-sample.md](en/cross-plugin-jpa-transaction-complex-sample.md)
- [en/cross-plugin-jpa-transaction-complex-sample-plan.md](en/cross-plugin-jpa-transaction-complex-sample-plan.md)
- [en/cross-plugin-jpa-transaction-complex-sample-acceptance.md](en/cross-plugin-jpa-transaction-complex-sample-acceptance.md)
- [en/cross-plugin-jpa-transaction-improvement.md](en/cross-plugin-jpa-transaction-improvement.md)
- [en/cross-plugin-jpa-transaction-improvement-plan.md](en/cross-plugin-jpa-transaction-improvement-plan.md)
- [en/plugin-hot-replacement-deployment-improvement.md](en/plugin-hot-replacement-deployment-improvement.md)
- [en/plugin-hot-replacement-deployment-improvement-plan.md](en/plugin-hot-replacement-deployment-improvement-plan.md)
- [en/plugin-hot-replacement-deployment-acceptance.md](en/plugin-hot-replacement-deployment-acceptance.md)
- [en/plugin-loading-and-packaging.md](en/plugin-loading-and-packaging.md)
- [en/code-quality-fixes.md](en/code-quality-fixes.md)
- [en/lifecycle-cleanup-fix.md](en/lifecycle-cleanup-fix.md)
- [en/scheduler-sharingbeans-fix.md](en/scheduler-sharingbeans-fix.md)
- [en/autoexport-jpa-boundary.md](en/autoexport-jpa-boundary.md)
- [en/production-readiness-roadmap.md](en/production-readiness-roadmap.md)
- [en/plugin-developer-guide.md](en/plugin-developer-guide.md)

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
