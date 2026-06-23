# 贡献指南

感谢你愿意改进 pf4boot。这个项目是 Java 8 / Gradle 多模块项目，核心目标是为 Spring Boot 应用提供基于 PF4J 的插件化运行、打包、治理和示例能力。

## 开始之前

- 先搜索已有 Issue、PR 和 `docs/design/`，确认问题没有被重复讨论。
- 涉及公共 API、插件生命周期、类加载、Spring 自动配置、依赖范围、打包格式或跨模块契约的改动，先补充或更新 `docs/design/` 下的中文设计说明；如有英文副本，同步更新 `docs/design/en/`。
- 普通文档、示例说明、拼写和小范围构建脚本修正可以直接提交 PR。

## 本地开发

```powershell
.\gradlew.bat :pf4boot-api:compileJava
.\gradlew.bat :pf4boot-core:compileJava
```

按改动范围优先运行窄任务。涉及共享行为、插件加载、部署、信任链或示例打包时，至少运行相关模块测试或 smoke：

```powershell
.\gradlew.bat :pf4boot-api:test :pf4boot-core:test
.\gradlew.bat :pf4boot-management-starter:test
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

注意：根构建中会禁用名称包含 `test` 的任务，不要假设 `.\gradlew.bat build` 已经执行了有意义的测试。

## 代码约束

- 保持 Java 8 兼容。
- 保持 UTF-8 编码，避免无关格式化。
- 公共 API 放在 `pf4boot-api`。
- 运行时实现放在 `pf4boot-core`。
- Spring Boot 自动配置放在对应 starter 模块。
- Web/JPA 集成分别留在 `pf4boot-web-*` 和 `pf4boot-jpa*` 模块。
- sample 专用逻辑只放在 `samples/*`。

## 提交 PR

PR 描述请包含：

- 变更目的和影响范围。
- 是否涉及兼容性、配置项、插件包格式或迁移。
- 已运行的验证命令。
- 若跳过验证，说明原因和建议的最小验证命令。

请不要在同一个 PR 中混入无关重构、生成产物、IDE 配置或本地缓存文件。

