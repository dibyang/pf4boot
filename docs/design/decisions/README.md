# 架构决策

此目录保存长期有效的架构决策。决策文档用于说明已经定稿的边界、取舍和后续约束；具体实现细节仍以 `docs/design` 顶层当前设计文档为准。

## 决策记录

- [cross-datasource-transaction-decision.md](cross-datasource-transaction-decision.md)：跨数据源事务边界、Saga/Outbox 和 XA 可选模块决策。
- [jpa-runtime-refresh-decision.md](jpa-runtime-refresh-decision.md)：JPA 运行时刷新和 EntityManagerFactory 重建决策。
- [plugin-management-console-boundary.md](plugin-management-console-boundary.md)：插件管理控制台 UI 与 HTTP API/Actuator 边界决策。
- [plugin-repository-governance-decision.md](plugin-repository-governance-decision.md)：插件离线仓库、签名发布、灰度和回滚治理决策。

## 新增规则

只有当决策会长期约束公共 API、模块边界、插件生命周期、类加载、自动配置、打包或运维治理时，才新增决策文档。一次性实施计划和验收清单应放入 `docs/design/archive` 或保留在 issue、PR、提交说明中。
