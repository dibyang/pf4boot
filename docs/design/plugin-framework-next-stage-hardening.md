# 插件框架下一阶段生产化增强设计

## 背景

P0-P6 生产级完善主线已经完成，框架具备包信任校验、持久化操作记录、生命周期诊断、能力声明、HTTP 管理接口、Actuator 观测和复杂 sample runtime smoke。当前仍有三项最适合进入下一阶段工程落地：

1. 离线插件仓库治理：把插件包从“本地文件”提升为“可索引、可签名、可灰度、可回滚”的发布物。
2. 版本范围严格预检：把 `pf4bootVersionRange`、`springBootVersionRange`、`capability.versionRange` 从诊断信息推进到可阻断的部署前检查。
3. runtime smoke Gradle/CI 化：把已有 PowerShell smoke 固化为 Gradle 入口和 CI 友好的报告输出。

本设计不重新打开 P0-P6，也不实现远程插件市场、控制台 UI、跨数据源事务或 JPA 在线 metamodel 热刷新。

## 目标

- 为离线索引仓库定义稳定 API、索引格式、解析流程、部署接入点和 sample 验证路径。
- 为版本范围定义 Java 8 兼容的解析器、匹配规则、错误码和预检模式。
- 为 runtime smoke 定义 Gradle task、报告文件、环境隔离和 CI 断言规则。
- 保持默认行为兼容：未启用新功能时历史本地插件目录、直接 staged path 部署和现有 smoke 脚本继续可用。
- 给后续实现模型提供可直接照做的模块边界、任务卡、验收标准和禁止事项。

## 非目标

- 不引入公网插件市场、账号体系、计费、审核流或中心服务。
- 不让 `pf4boot-core` 依赖 HTTP 客户端、对象存储 SDK、制品库 SDK、Spring Security 或 CI 平台 SDK。
- 不替代 PF4J 自身插件 descriptor 与插件依赖解析。
- 不移除 PowerShell smoke；Gradle task 第一阶段作为稳定入口包装现有脚本或复用其流程。
- 不在本阶段支持跨数据源原子事务、JPA runtime reload 或管理控制台 UI。

## 现状/已有流程

| 领域 | 当前状态 | 下一阶段缺口 |
| --- | --- | --- |
| 插件包来源 | 本地目录、zip、link/development repository、管理接口 staged path | 缺少发布索引、版本选择、回滚候选和仓库来源审计 |
| 包信任 | `.pf4boot-trust.json`、checksum、WARN/ENFORCE | 仓库索引本身也需要校验；仓库 release 与包 trust 需要串联 |
| 能力声明 | `PluginCapability`、`PluginCapabilityRequirement.versionRange` 已存在 | 版本范围目前主要进入诊断，未形成统一严格匹配器 |
| 管理部署 | `PluginDeploymentService` 支持 plan/replace/rollback 记录 | 缺少“从仓库 release 解析到 staged package”的 dry-run 和记录字段 |
| runtime smoke | `samples/cross-plugin-jpa/scripts/runtime-smoke.ps1` 已可端到端验证 | 缺少 Gradle task、机器可读报告和 CI 退出码/产物约定 |

## 核心约束

- Java 8 兼容，不使用 Java 9+ API。
- 新公共类型放在 `pf4boot-api`；运行时实现放在 `pf4boot-core`；自动配置放在 `pf4boot-starter`；HTTP 管理扩展放在 `pf4boot-management-starter`；只读观测放在 `pf4boot-actuator`；示例和 smoke 放在 `samples/*`。
- 默认配置必须关闭离线仓库和严格版本预检，避免破坏历史应用。
- 所有写入型管理操作仍必须通过现有鉴权、幂等、审计和路径校验。
- 新增仓库索引、报告和日志不得包含 token、私钥、完整堆栈、用户目录敏感路径或内部绝对路径。
- Gradle/CI smoke 不依赖外部网络、浏览器或私有凭证。

## 模块影响

| 模块 | 职责 |
| --- | --- |
| `pf4boot-api` | 新增 repository release 模型、resolver SPI、version range 模型和 matcher SPI |
| `pf4boot-core` | 离线索引加载、摘要校验、release 解析、staging 接入、版本范围匹配默认实现 |
| `pf4boot-starter` | 新增 `spring.pf4boot.repository.*` 和版本预检配置自动绑定，注册默认 resolver/matcher |
| `pf4boot-management-starter` | 新增仓库 dry-run/plan 入口或扩展现有 plan request，输出仓库 release 摘要 |
| `pf4boot-actuator` | 只读暴露仓库配置摘要、最后索引加载状态、版本预检统计 |
| `samples/cross-plugin-jpa` | 提供 sample repository index、插件包索引生成命令、Gradle smoke task |
| `docs/design` | 维护本设计、实施规划、验收追踪和中英文开发指南 |

## 接口设计

### 离线插件仓库

公共 API 建议放入 `net.xdob.pf4boot.repository`：

```java
public interface PluginRepositoryResolver {
  PluginRepositoryIndex loadIndex();

  PluginReleaseRecord resolve(PluginReleaseRequest request);
}
```

```java
public class PluginReleaseRequest {
  private String pluginId;
  private String version;
  private String versionRange;
  private boolean rollback;
  private Map<String, String> attributes;
}
```

```java
public class PluginRepositoryIndex {
  private int schemaVersion;
  private String repositoryId;
  private long generatedAt;
  private List<PluginReleaseRecord> releases;
  private String signature;
}
```

```java
public class PluginReleaseRecord {
  private String repositoryId;
  private String pluginId;
  private String version;
  private String packagePath;
  private String packageSha256;
  private String trustManifestPath;
  private String rolloutPolicy;
  private boolean rollbackCandidate;
  private Map<String, String> attributes;
}
```

第一阶段只支持 `type=offline-index`，索引路径为本地或内网挂载目录，不在 core 内下载远程包。

### 版本范围

公共 API 建议放入 `net.xdob.pf4boot.version`：

```java
public interface VersionRangeMatcher {
  VersionRange parse(String expression);

  boolean matches(String version, VersionRange range);
}
```

```java
public class VersionRange {
  private String expression;
  private VersionBoundary lower;
  private VersionBoundary upper;
  private boolean exact;
}
```

第一阶段支持 Maven 风格常用范围：

| 表达式 | 含义 |
| --- | --- |
| `1.2.3` | 精确匹配 |
| `[1.0,2.0)` | 大于等于 1.0 且小于 2.0 |
| `(1.0,2.0]` | 大于 1.0 且小于等于 2.0 |
| `[1.0,)` | 大于等于 1.0 |
| `(,2.0]` | 小于等于 2.0 |

版本比较第一阶段按点号分段的数字优先规则实现，允许尾部限定符但只做稳定排序。无法解析的范围在 `WARN` 模式输出 warning，在 `ENFORCE` 模式阻断部署。

### Gradle/CI smoke

在 `samples/cross-plugin-jpa` 增加 Gradle task：

```text
:samples:cross-plugin-jpa:app-run:runtimeSmoke
```

task 第一阶段可以包装 `scripts/runtime-smoke.ps1`，但必须提供稳定输出：

| 产物 | 路径 |
| --- | --- |
| 文本日志 | `samples/cross-plugin-jpa/app-run/build/tmp/runtime-smoke/runtime.log` |
| 机器可读报告 | `samples/cross-plugin-jpa/app-run/build/reports/runtime-smoke/result.json` |
| JUnit XML 可选报告 | `samples/cross-plugin-jpa/app-run/build/test-results/runtimeSmoke/TEST-runtime-smoke.xml` |

## 数据结构

`repository-index.json` 第一阶段格式：

```json
{
  "schemaVersion": 1,
  "repositoryId": "sample-offline",
  "generatedAt": 1781280000000,
  "releases": [
    {
      "pluginId": "sample-workflow",
      "version": "3.0.0",
      "packagePath": "plugins/plugin-workflow-3.0.0.zip",
      "packageSha256": "lowercase-sha256",
      "trustManifestPath": "plugins/plugin-workflow-3.0.0.zip.pf4boot-trust.json",
      "rolloutPolicy": "manual",
      "rollbackCandidate": true,
      "attributes": {
        "channel": "stable"
      }
    }
  ],
  "signature": "base64-signature"
}
```

`runtime-smoke/result.json` 第一阶段格式：

```json
{
  "status": "PASSED",
  "startedAt": 1781280000000,
  "finishedAt": 1781280060000,
  "port": 7791,
  "checks": [
    {"name": "hostReady", "status": "PASSED", "message": "HTTP 200"},
    {"name": "managementIdempotency", "status": "PASSED", "message": "operation replayed"}
  ]
}
```

## 状态机

### 仓库 release 到部署

```text
DISABLED
ENABLED -> INDEX_LOADING -> INDEX_READY / INDEX_FAILED
INDEX_READY -> RELEASE_RESOLVED -> PACKAGE_VERIFIED -> STAGED
STAGED -> DEPLOYMENT_PLANNED -> DEPLOYED / REJECTED / MANUAL_INTERVENTION
```

非法转换：

- `INDEX_FAILED` 不得继续 staging。
- `PACKAGE_VERIFIED` 失败不得进入 `STAGED`。
- 仓库 dry-run 不得改变运行时插件状态。

### 版本预检

```text
NOT_CONFIGURED -> SKIPPED
RANGE_PARSED -> MATCHED / MISMATCHED / INVALID_RANGE
MISMATCHED + WARN -> WARNING_RECORDED
MISMATCHED + ENFORCE -> DEPLOYMENT_REJECTED
INVALID_RANGE + WARN -> WARNING_RECORDED
INVALID_RANGE + ENFORCE -> DEPLOYMENT_REJECTED
```

## 时序流程

### 离线仓库部署 dry-run

1. 管理 API 接收 pluginId/version 或 pluginId/versionRange。
2. 鉴权、幂等和参数校验通过。
3. `PluginRepositoryResolver.loadIndex()` 读取索引。
4. 校验索引 schema、相对路径、签名摘要和 release 记录。
5. `resolve()` 选择 release，检查 rollback candidate。
6. 校验包 sha256 和 trust manifest。
7. 把包复制到 staging 目录。
8. 调用现有 `PluginDeploymentService.planReplacement()`。
9. 响应包含 repositoryId、release version、staged path 摘要和 deployment checks。

### 版本范围预检

1. 解析 staged 插件 trust manifest。
2. 检查 `pf4bootVersionRange` 是否匹配当前框架版本。
3. 检查 `springBootVersionRange` 是否匹配当前 Spring Boot 版本。
4. 检查每个 `PluginCapabilityRequirement.versionRange` 是否匹配候选 provider capability version。
5. 将结果转换为 `DeploymentCheckResult`。
6. 根据 `pluginCapabilityPrecheckMode` 或新增版本预检模式决定 warning 或 reject。

### Gradle runtime smoke

1. `runtimeSmoke` 依赖 sample runtime assemble。
2. task 准备独立 work 目录和端口。
3. 启动 app-run runtime。
4. 复用现有 HTTP smoke 顺序执行业务、管理、Actuator、失败路径和清理。
5. 写入 `result.json` 和日志。
6. 任一必选 check 失败时 task 退出非 0。

## 异常处理

| 场景 | 行为 |
| --- | --- |
| 仓库未启用 | 历史流程继续，Actuator 显示 `repository.enabled=false` |
| 索引不存在或解析失败 | repository 功能不可用；不影响本地目录加载 |
| 索引路径越界 | 阻断 staging，返回安全错误摘要 |
| 包摘要不匹配 | 阻断 staging，记录 `PFR-003` |
| 版本范围无法解析 | WARN 模式记录 warning；ENFORCE 模式阻断 |
| Gradle smoke 启动超时 | 打印日志尾部，写失败报告，清理进程 |
| CI 无 PowerShell | task 检测并给出明确失败；后续可补 Java smoke runner |

## 幂等性

- 仓库 dry-run 使用现有管理接口幂等 key；相同请求 hash replay 相同结果。
- staging 目录按 operationId 或 request hash 隔离，重复请求不得覆盖其它 operation 的包。
- `runtimeSmoke` 每次使用独立 build tmp 目录，结束时清理进程和临时数据；失败保留日志和报告。

## 回滚策略

- 离线仓库部署前必须解析 rollback candidate；缺失时在 WARN 模式输出 warning，ENFORCE 模式可阻断。
- 若新包部署失败，继续复用现有 rollback 编排；部署记录补充 repository release 摘要。
- 新配置默认关闭；回滚到旧版本时删除新增配置即可回到 P0-P6 行为。
- Gradle smoke task 出问题时保留 PowerShell 脚本作为手动兜底。

## 兼容性

- 默认不开启离线仓库，不改变历史本地插件目录扫描。
- 版本范围严格预检默认不阻断；已有 manifest 中的范围字段在默认模式仍可只作诊断。
- 新增 API 只能追加，不删除或改签名现有 `PluginCapability*`、`PluginDeploymentService` 和管理接口模型。
- sample 新增 Gradle task 不影响已有 `assembleSampleRuntime` 和 `runtime-smoke.ps1`。

## 灰度/迁移

1. 文档和 sample index 先行，生产代码默认 no-op。
2. 版本范围以 `WARN` 模式接入部署预检，收集误报。
3. 离线仓库先支持 dry-run，再允许 replace。
4. smoke task 先在本地手动执行，再接入 CI。
5. 稳定后再把 sample 配置切到更严格的 ENFORCE 示例。

## 测试方案

| 层级 | 覆盖 |
| --- | --- |
| 单元测试 | version range parse/match、索引解析、路径越界、摘要不匹配、release 选择 |
| 集成测试 | deployment plan 接入 repository release、version precheck 到 `DeploymentCheckResult` |
| starter 测试 | 配置绑定、默认关闭、Bean 条件装配 |
| management 测试 | dry-run 鉴权、幂等、错误响应脱敏 |
| actuator 测试 | repository/version precheck 摘要只读输出 |
| sample smoke | Gradle task 成功路径、失败路径、报告和清理 |

## 风险点

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 仓库治理范围膨胀成远程市场 | 核心复杂度和安全面扩大 | 第一阶段只做 offline-index，本地路径，不引入 HTTP 下载 |
| 版本比较规则与用户预期不一致 | 误阻断插件部署 | 先 WARN，文档明确支持范围；复杂语义后续再扩 |
| Gradle task 依赖 PowerShell 导致跨平台不足 | Linux CI 不可用 | 第一阶段显式检测；第二阶段补 Java runner 或跨平台脚本 |
| 索引路径处理不严 | 可能读取仓库外文件 | 所有 package/trust path 必须是相对路径并 resolve 后保持在 repository root 内 |
| 报告泄露敏感信息 | 安全风险 | 报告只写摘要、错误码和相对路径 |

## 分阶段实施计划

详细任务、验收状态和证据分别维护在：

- [plugin-framework-next-stage-hardening-plan.md](plugin-framework-next-stage-hardening-plan.md)
- [plugin-framework-next-stage-hardening-acceptance.md](plugin-framework-next-stage-hardening-acceptance.md)

## 开放问题

- `pf4bootVersionRange` 的当前框架版本取 `gradle.properties version` 还是运行时 package implementation version：建议实现时优先使用 package implementation version，缺失时回退 `Pf4bootProperties` 可配置值。
- Spring Boot 版本来源是否允许宿主覆盖：建议允许 `spring.pf4boot.compatibility.spring-boot-version` 覆盖，默认从 `SpringBootVersion.getVersion()` 读取。
- 是否在第一阶段生成 JUnit XML：建议可选；`result.json` 和 task 非 0 退出是必选。
