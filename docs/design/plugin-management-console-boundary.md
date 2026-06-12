# 插件管理控制台 UI 边界决策

## 背景

pf4boot 已有 HTTP 管理接口、鉴权/安全治理、幂等、部署记录、Actuator 只读观测和 runtime smoke。用户可能需要图形化控制台查看插件状态、执行部署和查看诊断。但 UI 容易把前端资源、安全会话、权限模型、审计展示和远程访问策略带入核心框架。

本决策用于明确控制台 UI 是否进入框架范围，以及 UI 与 HTTP API、Actuator、core/starter 的依赖边界。

## 目标

- 明确是否在 core/starter 内置 UI。
- 锁定 UI 只能消费 HTTP 管理 API 和 Actuator 只读 endpoint。
- 避免 UI 反向影响插件生命周期、部署编排和安全边界。
- 为未来独立 sample UI 或外部控制台提供接口边界。

## 非目标

- P6 不实现 UI。
- 不在 `pf4boot-core`、`pf4boot-starter` 或 `pf4boot-management-starter` 内打包前端控制台。
- 不引入前端框架、Session 管理或 Spring Security 强依赖。
- 不改变 HTTP 管理 API 的鉴权要求。

## 现状/已有流程

| 能力 | 当前状态 |
| --- | --- |
| 写接口 | `/pf4boot/admin/**`，支持本地 token、远程授权 SPI、幂等 |
| 只读观测 | `/actuator/pf4bootplugins`、`/actuator/pf4bootgovernance`、metrics |
| 安全 | 本地调用仍默认建议 token；远程模式需委托授权 |
| smoke | runtime smoke 已验证 token、幂等、失败记录和 actuator |
| 缺口 | 没有 UI 交互层和 API 契约文档化输出 |

## 核心约束

- UI 不得成为 core/starter 依赖。
- UI 不得绕过 HTTP 管理接口直接调用 Java manager。
- UI 写操作必须携带现有鉴权与幂等 header。
- Actuator 仍只读，不能承载写操作。
- 控制台部署必须显示风险、预检和回滚状态，不允许隐藏失败细节。

## 备选方案

| 方案 | 描述 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| A. 不内置 UI | 框架只提供 API/Actuator | 边界最清晰，维护成本低 | 用户需要自己接 UI | 推荐默认 |
| B. 独立 sample UI | 在 `samples/*` 提供演示 UI，只消费 HTTP API | 可演示完整流程，不污染核心 | 仍需维护 sample | 未来可选 |
| C. starter 内置静态 UI | management starter 打包静态资源 | 开箱即用 | 安全、版本、前端依赖进入 starter | 拒绝第一阶段 |
| D. 外部控制台服务 | 独立应用或平台服务接入多个宿主 | 适合生产多实例治理 | 需要认证、租户、网络治理 | 未来独立项目 |

## 推荐结论

推荐：框架不内置管理控制台 UI。若需要演示，未来只在 `samples/plugin-management-console` 或独立仓库提供 sample UI。生产控制台应作为外部应用，只通过 HTTP 管理 API 和 Actuator endpoint 访问宿主。

## 接口边界草案

UI 允许调用：

| 类型 | 路径 | 说明 |
| --- | --- | --- |
| 管理写/读 | `/pf4boot/admin/**` | 插件启停、部署 plan/replace/rollback、部署记录查询 |
| 只读观测 | `/actuator/pf4bootplugins` | 插件快照 |
| 治理摘要 | `/actuator/pf4bootgovernance` | trust/capability/deployment/cleanup 摘要 |
| metrics | `/actuator/metrics/pf4boot.*` | 指标 |

UI 禁止调用：

- `Pf4bootPluginManager` Java Bean。
- `PluginDeploymentService` Java Bean。
- 插件内部 Repository 或 service。
- Actuator 以外的内部诊断对象。

## 配置草案

如果未来提供 sample UI，仅允许在 sample 内配置：

```yaml
sample:
  pf4boot-console:
    api-base-url: http://127.0.0.1:7791
    token-header: X-PF4Boot-Admin-Token
```

框架模块不新增 `spring.pf4boot.console.enabled` 这类开关，避免暗示内置 UI 是框架能力。

## 状态机

UI 展示部署状态时必须按后端 `DeploymentState` 显示：

```text
PLANNED -> PRECHECKING -> DRAINING -> STOPPING -> REPLACING
  -> STARTING -> HEALTH_CHECKING -> SUCCEEDED
任一执行态 -> ROLLING_BACK -> ROLLED_BACK / MANUAL_INTERVENTION
```

UI 不得自定义后端状态语义。

## 时序流程

1. UI 读取 `/actuator/pf4bootgovernance` 显示总体风险。
2. UI 调用 `/pf4boot/admin/plugins` 获取插件列表。
3. 用户上传或选择 staged 包后，UI 先调用 deployment plan。
4. UI 展示预检结果、影响范围和回滚信息。
5. 用户确认后，UI 调用 replace，并附带 `X-Idempotency-Key`。
6. UI 轮询 deployment record，展示成功、回滚或人工介入。

## 异常处理

| 异常 | UI 行为 |
| --- | --- |
| 401/403 | 提示鉴权失败，不重试写操作 |
| 409 幂等冲突 | 显示已有 operation/deployment 信息 |
| 预检失败 | 展示错误码、影响范围和修复建议 |
| MANUAL_INTERVENTION | 显示部署记录和日志定位信息 |

## 兼容性

- 不内置 UI，因此现有应用无行为变化。
- API 仍是唯一写入口。
- Actuator 继续只读。
- sample UI 若未来存在，不影响发布模块。

## 回滚策略

若未来 sample UI 出现问题，可删除 sample 或关闭外部控制台，不影响框架运行。后端 API 和部署记录仍可通过 curl/脚本使用。

## 测试方案

未来 sample UI 需要：

- API contract 测试。
- 鉴权失败和幂等冲突展示测试。
- 部署 plan/replace/rollback 流程端到端 smoke。
- 只读 actuator 与写接口权限分离测试。

## 风险点

| 风险 | 缓解 |
| --- | --- |
| UI 诱导用户暴露本地管理 token | sample 标明仅本地演示，生产接企业鉴权 |
| UI 绕过后端预检 | 禁止直接调用 Java Bean，只能走 HTTP API |
| 前端依赖污染 starter | UI 不进入 starter |
| UI 状态与后端状态不一致 | UI 只展示后端状态枚举和部署记录 |

## 进入实施规划条件

- HTTP 管理 API 契约稳定，并有 OpenAPI 或等价接口文档。
- Actuator 只读治理摘要稳定。
- 有明确用户场景需要 sample UI。
- UI 作为 sample 或外部服务维护，不进入 core/starter。

## 最终决策

本专题暂不实现 UI。pf4boot 框架保持 API-first；控制台 UI 不进入 core/starter/management starter。未来若需要演示，只做独立 sample UI；生产控制台应作为外部应用接入 HTTP 管理 API 和 Actuator。
