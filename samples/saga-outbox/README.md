# Saga/Outbox Sample

该示例演示跨边界业务的最终一致性模式：

- 订单创建只写订单表和 outbox 表。
- dispatcher tick 读取 outbox 事件并调用 billing 处理。
- billing 使用 inbox 表按 event key 幂等去重。
- 失败事件进入 `RETRY`，后续 tick 可重试成功。

## 模板定位

该 sample 是 3.3 插件生态规划中的“跨数据源不做强事务”参考样例：

- 适用于需要跨业务边界协作，但不能或不应使用框架级 XA/JTA 的场景。
- 展示 outbox、dispatcher、inbox、幂等和失败重试的基本形状。
- 不代表 `pf4boot` 支持跨数据源原子提交。
- 不应把 sample 的内存调度、测试端口、演示数据库路径直接复制到生产。

后续如果业务插件同时访问多个 JPA domain，应优先采用这种业务补偿模式，而不是假设 `@Transactional` 可以覆盖多个数据源。

## 运行

```powershell
.\gradlew.bat :samples:saga-outbox:app-run:runtimeSmoke
```

验收点：

- `SAGA_ORDER_PAID`：正常订单经 dispatcher 后变为 `PAID`。
- `SAGA_INBOX_IDEMPOTENT`：重复 tick 不会重复扣款。
- `SAGA_RETRY_SUCCESS`：首次失败的 billing 事件可重试成功。

## 3.3 验收要求

- README 必须保持“补偿一致性 sample”定位，不得描述为 XA 或跨数据源强事务。
- runtime smoke 必须能机器判断成功/失败。
- 如果后续把它纳入官方模板矩阵，应补充模块职责表、可复制代码和 demo-only 代码边界。
