# 生产 profile 与发布门禁

## 问题

框架已经具备插件包 checksum、trust manifest、兼容范围、capability precheck、离线仓库和热替换部署能力，但这些能力默认保持兼容旧插件，很多开关仍为 `DISABLED` 或 `WARN`。生产环境需要一个显式 profile，把插件加载、仓库 release 和真实 replace 收敛到 fail-closed 的门禁路径。

## 影响模块

- `pf4boot-api`：在 `Pf4bootProperties` 中增加生产 profile 与仓库 release gate 配置。
- `pf4boot-core`：默认插件包信任校验、离线仓库解析和部署服务执行生产门禁。
- `pf4boot-starter`：继续消费同一份 `Pf4bootProperties`，不新增 starter 边界。
- `docs/design/en`：同步英文说明。

## 设计方案

新增 `spring.pf4boot.production-profile-enabled`。默认 `false`，保持历史兼容；开启后，以下治理模式按生产有效值执行：

- `plugin-package-verification-mode=ENFORCE`
- `plugin-package-trust-mode=ENFORCE`
- `plugin-compatibility-verification-mode=ENFORCE`
- `plugin-capability-precheck-mode=ENFORCE`
- `plugin-compatibility-precheck-mode=ENFORCE`
- `plugin-repository-trust-mode=ENFORCE`
- `plugin-repository-release-gate-enabled=true`
- `plugin-package-signature-required=true`

生产 profile 不自动开启 `plugin-repository-replace-enabled`。真实 repository replace 仍需应用显式打开该开关；打开后还必须通过 release gate。

仓库 release gate 默认读取 release `attributes.releaseGate`，值必须为 `passed`。可通过：

- `spring.pf4boot.plugin-repository-release-gate-attribute`
- `spring.pf4boot.plugin-repository-release-gate-value`

调整字段和值。

信任链 ENFORCE 包含三层：

1. 插件包 checksum sidecar 必须存在并匹配。
2. trust manifest 必须存在，plugin id、version、package sha256 必须匹配。
3. 生产 profile 下 trust manifest 必须包含完整 signature metadata，且 `keyId` 必须能被 `plugin-package-trust-roots` 或 `PluginTrustRootProvider` 识别。

仓库 release 进入受控 cache/staging 后，部署服务负责为缓存包写入 checksum sidecar，并把 repository index 指向的 trust manifest 复制为缓存包旁路文件。这样后续统一复用 `PluginDeploymentService.replace(...)` 和加载前校验，不绕过既有部署、回滚和 health check。

## 兼容性

- 默认不开生产 profile，既有应用和旧插件行为不变。
- 开启生产 profile 后，缺少 checksum、trust manifest、signature metadata、trust root、repository index signature 或 release gate 的插件包会被阻断。
- 自定义 `PluginPackageTrustVerifier` 仍可作为额外校验器；默认校验器先执行基础清单、checksum 和 trust root 门禁。
- `plugin-repository-replace-enabled=false` 时，即使生产 profile 开启也只允许 plan/dry-run，不执行真实替换。

## 验证

最小验证：

```powershell
.\gradlew.bat :pf4boot-core:test :pf4boot-api:test
```

涉及管理接口时继续运行：

```powershell
.\gradlew.bat :pf4boot-management-starter:test
```

## 未决问题

- 当前默认校验器只强制 signature metadata 和 trust root 识别，不定义具体签名原文和算法验签格式；完整加密签名格式应作为独立信任根专题继续设计。
- 生产 profile 的推荐配置是否需要生成样例 `application-production.yml`，可在 sample 文档整理时补充。
