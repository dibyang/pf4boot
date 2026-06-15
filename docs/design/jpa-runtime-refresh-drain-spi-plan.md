# JPA 运行时刷新 Drain SPI 实施规划

## 1. 范围

本文跟踪 [jpa-runtime-refresh-drain-spi.md](jpa-runtime-refresh-drain-spi.md) 的实施任务。目标是在 JPA domain 重启式刷新执行模式中接入通用 `PluginTrafficDrainer`，让 `DRAINING` 阶段真正摘流、等待在途工作归零，并把结果写入 reload record。

## 2. 阶段总览

| 阶段 | 状态 | 目标 |
| --- | --- | --- |
| D0 设计补齐 | Done | 设计包含字段、构造器、伪代码、配置、错误码、测试和验收 |
| D1 公共模型扩展 | Planned | 扩展 drain report、drainer result 和 reload record |
| D2 Drain coordinator | Planned | 在 JPA starter 中实现通用 drainer 编排 |
| D3 Reload service 接入 | Planned | `DRAINING` 阶段执行 drain，失败不 stop |
| D4 管理接口和 Actuator 摘要 | Planned | record/API/Actuator 输出 drain report 摘要 |
| D5 单元和集成测试 | Planned | 覆盖 no-drainer、timeout、rejected、endDrain、stop/start 失败 |
| D6 Sample runtime smoke | Planned | 覆盖 drain 成功、drain 超时无变更和 Actuator 摘要 |
| D7 文档和验收收口 | Planned | 更新文档、验收清单和英文翻译 |

## 3. D1 公共模型扩展

### 影响模块

- `pf4boot-jpa`

### 任务

1. 新增 `JpaDomainDrainerPhase`：`BEGIN`、`AWAIT`、`END`。
2. 新增 `JpaDomainDrainerResult` 不可变模型。
3. 扩展 `JpaDomainDrainReport`：
   - 保留 `JpaDomainDrainReport(boolean accepted, String message)`；
   - 新增完整构造器；
   - 所有 list 字段 null 转空不可变列表；
   - `durationMillis` 使用 getter 派生。
4. 扩展 `JpaDomainReloadRecord`：
   - 新增 `drainReport` 字段；
   - 保留旧构造器；
   - 新增带 `drainReport` 的构造器；
   - 新增 `getDrainReport()`。
5. `message` 长度裁剪到 512 字符，避免管理接口输出过长异常信息。

### 验证

```powershell
.\gradlew.bat :pf4boot-jpa:compileJava
```

## 4. D2 Drain coordinator

### 影响模块

- `pf4boot-jpa-starter`

### 任务

1. 新增 `JpaDomainReloadDrainCoordinator`。
2. 注入 `ObjectProvider<PluginTrafficDrainer>` 和 `Pf4bootJpaProperties`。
3. 计算 impact plugin ids：`plan.stopOrder + plan.providerPluginId`，去重且顺序稳定。
4. 收集 drainer：
   - 保留 Spring 注入顺序；
   - 尽量获取 bean name；拿不到时使用类名。
5. 实现 `beginDrain`：
   - 逐个调用；
   - 已 begin drainer 记录到本次执行上下文；
   - 任一异常返回 `DRAIN_REJECTED`。
6. 实现 `awaitDrain`：
   - 多 drainer 共享总 timeout；
   - 返回 false 映射 `DRAIN_TIMEOUT`；
   - `InterruptedException` 恢复中断位并映射 `DRAIN_REJECTED`；
   - RuntimeException 映射 `DRAIN_REJECTED`。
7. 实现 `endDrain`：
   - 反向调用已 begin drainer；
   - 失败只进入 warnings。
8. 无 drainer：
   - `require-drainer=false` 返回 accepted + warning；
   - `require-drainer=true` 返回 `DRAIN_REJECTED`。

### 验证

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test
```

## 5. D3 Reload service 接入

### 影响模块

- `pf4boot-jpa-starter`

### 任务

1. `JpaDomainReloadAutoConfiguration` 注册 `JpaDomainReloadDrainCoordinator`。
2. `DefaultJpaDomainReloadService` 构造器新增 coordinator，同时保留旧构造器或兼容测试构造。
3. 在 `states.add(DRAINING)` 后调用 coordinator。
4. drain report `accepted=false` 时：
   - 追加 `FAILED` transition；
   - 写入 failure code；
   - 保存 record；
   - 不调用任何 `stopPlugin/startPlugin`。
5. drain success 后继续原 stop/start 流程。
6. stop/start/health 任一失败时仍调用 `endDrain`。
7. 成功路径也调用 `endDrain`。
8. `currentReloads`、global lock、domain lock 的释放顺序保持不变。

### 验证

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test
```

## 6. D4 管理接口和 Actuator 摘要

### 影响模块

- `pf4boot-management-starter`
- `pf4boot-actuator`

### 任务

1. 管理 reload record 响应自然包含 `drainReport`。
2. `Pf4bootJpaReloadEndpoint` 增加最近 drain 摘要：
   - `lastDrainAccepted`
   - `lastDrainDurationMillis`
   - `lastDrainFailureCode`
   - `lastDrainPluginCount`
   - `lastDrainWarningCount`
3. 输出不包含堆栈、绝对路径、token 或原始请求敏感字段。
4. 没有历史 record 时字段为 null 或 0，不能抛异常。

### 验证

```powershell
.\gradlew.bat :pf4boot-management-starter:test :pf4boot-actuator:test
```

## 7. D5 单元和集成测试

### 影响模块

- `pf4boot-jpa-starter`
- `pf4boot-web-starter`
- `pf4boot-core`

### 任务

1. `DefaultJpaDomainReloadServiceTest` 增加：
   - no drainer 兼容继续执行；
   - `require-drainer=true` 阻断；
   - begin 抛异常；
   - await 返回 false；
   - await 抛异常；
   - `InterruptedException`；
   - endDrain 抛异常但主结果不被覆盖；
   - stop/start 失败时仍 endDrain。
2. coordinator 独立单测覆盖：
   - impact plugin ids 顺序；
   - 多 drainer 剩余 timeout；
   - drainer name 兜底；
   - list 不可变。
3. 复用现有 Web drain 测试，不重复验证底层 Web 计数，只验证 JPA reload 调用了 drainer。

### 验证

```powershell
.\gradlew.bat :pf4boot-jpa-starter:test :pf4boot-web-starter:test :pf4boot-core:test
```

## 8. D6 Sample runtime smoke

### 影响模块

- `samples/cross-plugin-jpa`

### 任务

1. 增加 sample 专用测试 drainer 或长请求入口，用于稳定制造 drain timeout。
2. runtime smoke 增加 `jpaReloadDrainSuccess`。
3. runtime smoke 增加 `jpaReloadDrainTimeoutNoMutation`：
   - reload 返回 `DRAIN_TIMEOUT` 或 `DRAIN_REJECTED`；
   - workflow 插件仍可访问；
   - unrelated 插件仍可访问。
4. runtime smoke 增加 `actuatorJpaReloadDrainSummary`。
5. `result.json` 和 JUnit XML 输出上述检查项。
6. README 更新 smoke 检查说明。

### 验证

```powershell
.\gradlew.bat :samples:cross-plugin-jpa:app-run:runtimeSmoke
```

## 9. D7 文档和验收收口

### 任务

1. 更新 [jpa-runtime-refresh.md](jpa-runtime-refresh.md) 中 V1 drain 从 warning 改为真实 drainer。
2. 更新 [jpa-integration.md](jpa-integration.md) 的 refresh 边界。
3. 更新 [plugin-developer-guide.md](plugin-developer-guide.md) 的 JPA refresh drain 使用说明。
4. 更新本规划和验收清单状态。
5. 同步英文翻译。

### 验证

```powershell
git diff --check
rg -n "U\+FFFD" docs/design docs/design/en samples/cross-plugin-jpa
```

## 10. 完成门槛

| 门槛 | 标准 |
| --- | --- |
| GATE-1 | `PluginTrafficDrainer` 接入 JPA reload execute |
| GATE-2 | drain 失败不停止 consumer/provider |
| GATE-3 | 成功、失败、异常、人工介入路径都调用或记录 `endDrain` |
| GATE-4 | `JpaDomainReloadRecord` 可查询 drain report |
| GATE-5 | 管理接口和 Actuator 不泄露敏感信息 |
| GATE-6 | 单元测试、模块测试和 runtime smoke 通过 |
| GATE-7 | 中文/英文设计、规划、验收同步 |

## 11. 回滚策略

1. JPA reload 默认仍为 `DISABLED`，关闭配置即可回避执行路径。
2. 若 drain 接入有问题，可设置 `require-drainer=false` 保持兼容路径。
3. 若某个 drainer 行为异常，宿主可移除对应 Bean 或调整 Bean 条件。
4. 新增 record 字段为向后兼容扩展；旧记录 `drainReport=null` 可正常读取。
