# 插件仓库治理决策

## 背景

当前 pf4boot 已支持从本地 zip/link/development repository 加载插件，生产级完善已补齐包 trust manifest、部署记录、HTTP 管理接口、热替换编排和 runtime smoke。缺口是插件包从“开发者本地文件”进入“可治理发布物”的路径：如何记录版本、签名、灰度、回滚、索引和仓库来源。

本决策只定义仓库治理边界，不实现远程市场或中心服务。

## 目标

- 明确第一阶段插件仓库形态。
- 定义包索引、发布记录、签名和回滚包治理边界。
- 保持当前本地插件目录和部署管理接口兼容。
- 不引入强制远程服务依赖。

## 非目标

- 不实现公网插件市场。
- 不引入账号、计费、审核流或远程控制台。
- 不让 core 强依赖 HTTP 客户端、对象存储 SDK 或制品库 SDK。
- 不替代现有 PF4J 插件 descriptor 和依赖解析。

## 现状/已有流程

| 领域 | 当前状态 |
| --- | --- |
| 插件加载 | 本地目录、zip、link、development repository |
| 包校验 | checksum、trust manifest、WARN/ENFORCE |
| 部署管理 | HTTP 管理接口支持 plan/replace/rollback 查询 |
| 记录 | operation/deployment/idempotency store |
| 缺口 | 缺少仓库索引、发布元数据、灰度策略和回滚包选择规则 |

## 核心约束

- 插件包仍是部署单位。
- 包 trust 校验必须在加载/部署前执行。
- 仓库治理默认不改变历史本地插件目录行为。
- 远程中心服务不能成为框架启动前提。
- 发布索引不得包含私钥、token 或内部绝对路径。

## 备选方案

| 方案 | 描述 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| A. 本地目录仓库 | 继续手工放置 zip，依赖目录扫描 | 简单、兼容 | 版本和回滚治理弱 | 保留兼容 |
| B. 离线索引仓库 | 本地或内网目录包含 `repository-index.json` 和插件包 | 可签名、可审计、无远程强依赖 | 需要索引生成和校验工具 | 推荐第一阶段 |
| C. 远程中心仓库 | 框架直接连接中心服务查询/下载插件 | 功能完整 | 网络、认证、可用性和安全治理复杂 | 暂缓 |
| D. 镜像/缓存仓库 | 宿主从离线索引或远程源同步到本地缓存 | 适合生产隔离网络 | 需要同步工具和缓存清理 | 第二阶段候选 |

## 推荐结论

推荐：第一阶段采用“离线索引仓库”。宿主显式配置一个本地或内网只读仓库目录，目录包含签名索引和插件包。部署动作仍由管理 API 或运维脚本显式触发，不引入强制远程中心服务。

## 接口与配置草案

```java
public interface PluginRepositoryResolver {
  PluginRepositoryIndex loadIndex();

  PluginReleaseRecord resolve(String pluginId, String version);
}
```

```java
public class PluginRepositoryIndex {
  private int schemaVersion;
  private String repositoryId;
  private List<PluginReleaseRecord> releases;
  private String signature;
}
```

```java
public class PluginReleaseRecord {
  private String pluginId;
  private String version;
  private String packagePath;
  private String packageSha256;
  private String trustManifestPath;
  private String rolloutPolicy;
  private boolean rollbackCandidate;
}
```

```yaml
spring:
  pf4boot:
    repository:
      enabled: false
      type: offline-index
      location: ${PF4BOOT_PLUGIN_REPOSITORY:}
      trust-mode: WARN # WARN, ENFORCE
      cache-directory: ${PF4BOOT_PLUGIN_CACHE:}
```

默认 `enabled=false`。

## 数据结构

`repository-index.json` 示例：

```json
{
  "schemaVersion": 1,
  "repositoryId": "local-prod",
  "generatedAt": 1781280000000,
  "releases": [
    {
      "pluginId": "sample-workflow",
      "version": "3.0.0",
      "packagePath": "plugins/plugin-workflow-3.0.0.zip",
      "packageSha256": "lowercase-sha256",
      "trustManifestPath": "plugins/plugin-workflow-3.0.0.zip.pf4boot-trust.json",
      "rolloutPolicy": "manual",
      "rollbackCandidate": true
    }
  ],
  "signature": "base64-signature"
}
```

## 状态机

```text
INDEX_LOADED -> PACKAGE_RESOLVED -> PACKAGE_VERIFIED -> STAGED
STAGED -> PLANNED -> DEPLOYED / REJECTED
PACKAGE_VERIFIED -> REJECTED
```

仓库解析只负责到 `STAGED` 前，不直接启动或替换插件。

## 时序流程

1. 宿主或管理 API 请求部署某插件版本。
2. `PluginRepositoryResolver` 读取索引。
3. 校验索引签名、包摘要、trust manifest。
4. 将包复制或解析到 staging 目录。
5. 调用现有 deployment plan/replace 流程。
6. 部署记录保存仓库 ID、release record 摘要和回滚候选包。

## 异常处理

| 异常 | 行为 |
| --- | --- |
| 索引缺失 | repository 功能不可用，不影响历史本地目录加载 |
| 索引签名失败 | `ENFORCE` 阻断，`WARN` 只允许 dry-run |
| 包摘要不匹配 | 阻断 staging |
| 回滚包缺失 | 部署预检 warning 或失败，取决于策略 |

## 兼容性

- 默认关闭，不影响现有 plugin root。
- deployment API 可以继续接受直接 staged path。
- 仓库 release record 是增强元数据，不替代 PF4J descriptor。

## 回滚策略

部署前必须解析并记录 rollback candidate。仓库不可用时，已 staged 的旧包仍可按部署记录回滚；若旧包不可用，进入人工介入。

## 灰度/迁移

1. 先提供离线索引文档和 sample index。
2. 管理 API 增加 dry-run 解析能力。
3. 运维脚本接入索引生成和签名。
4. 后续再评估镜像/缓存仓库。

## 测试方案

- 索引解析和签名校验单测。
- 包摘要不匹配拒绝测试。
- release record 到 deployment plan 的 dry-run 测试。
- 回滚包缺失诊断测试。
- sample 离线索引 smoke。

## 风险点

| 风险 | 缓解 |
| --- | --- |
| 远程服务复杂度过早进入核心 | 第一阶段只做离线索引 |
| 索引与包不一致 | 摘要和 trust manifest 双重校验 |
| 回滚包丢失 | deployment plan 阶段必须检查 rollback candidate |
| 敏感信息泄露 | 索引只记录相对路径、摘要和安全元数据 |

## 进入实施规划条件

- P1 trust manifest 和 P2 持久化记录已稳定。
- 需要真实发布流程或 sample 验证索引生成。
- 管理 API 已能处理 repository release record 到 staged package 的 dry-run。

## 最终决策

本专题暂不实现远程插件市场。未来若进入实现，第一阶段只做离线索引仓库和签名发布治理；远程中心服务、镜像缓存和 UI 市场均作为后续独立专题。
