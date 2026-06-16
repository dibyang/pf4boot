# Saga/Outbox Sample

该示例演示跨边界业务的最终一致性模式：

- 订单创建只写订单表和 outbox 表。
- dispatcher tick 读取 outbox 事件并调用 billing 处理。
- billing 使用 inbox 表按 event key 幂等去重。
- 失败事件进入 `RETRY`，后续 tick 可重试成功。

## 运行

```powershell
.\gradlew.bat :samples:saga-outbox:app-run:runtimeSmoke
```

验收点：

- `SAGA_ORDER_PAID`：正常订单经 dispatcher 后变为 `PAID`。
- `SAGA_INBOX_IDEMPOTENT`：重复 tick 不会重复扣款。
- `SAGA_RETRY_SUCCESS`：首次失败的 billing 事件可重试成功。
