package net.xdob.pf4boot.management;

import java.util.List;

/**
 * 管理操作记录与幂等数据存储 SPI.
 */
public interface PluginOperationStore {

  PluginOperationRecord save(PluginOperationRecord record);

  /**
   * Atomically reserves an idempotency key for a new operation.
   *
   * @return the existing record when the key has already been reserved, otherwise {@code null}
   */
  PluginOperationRecord saveIfIdempotencyKeyAbsent(PluginOperationRecord record);

  PluginOperationRecord findById(String operationId);

  PluginOperationRecord findByIdempotencyKey(String idempotencyKey);

  PluginOperationRecord findByDeploymentId(String deploymentId);

  List<PluginOperationRecord> recent(int limit);
}
