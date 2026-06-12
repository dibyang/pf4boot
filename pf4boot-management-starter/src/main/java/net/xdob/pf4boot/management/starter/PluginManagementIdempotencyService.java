package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementRequest;
import net.xdob.pf4boot.management.PluginOperationRecord;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.springframework.util.StringUtils;

/**
 * 管理接口幂等处理服务。
 * 约定同一 principal/operation/plugin/idempotency-key 下，如果请求参数 hash 一致则允许重放，
 * 否则返回 CONFLICT。
 */
public class PluginManagementIdempotencyService {

  private final Pf4bootManagementProperties properties;
  private final PluginOperationStore operationStore;

  public PluginManagementIdempotencyService(
      Pf4bootManagementProperties properties,
      PluginOperationStore operationStore) {
    this.properties = properties;
    this.operationStore = operationStore;
  }

  public PluginOperationRecord begin(PluginManagementRequest request, PluginManagementOperation operation,
                                   String principalId, String requestHash,
                                   String operationId, String deploymentId) {
    if (!properties.isRequireIdempotencyKey()
        || !StringUtils.hasText(properties.getIdempotencyHeader())) {
      return null;
    }
    String idempotencyKey = request.getIdempotencyKey();
    if (!StringUtils.hasText(idempotencyKey)) {
      throw new PluginManagementException(
          PluginManagementErrorCode.INVALID_REQUEST,
          "Missing idempotency key from header " + properties.getIdempotencyHeader(),
          400);
    }
    String key = idempotencyIndexKey(principalId, operation, request.getPluginId(), idempotencyKey);
    PluginOperationRecord existing = operationStore.findByIdempotencyKey(key);
    if (existing == null) {
      PluginOperationRecord record = new PluginOperationRecord();
      record.setOperationId(operationId);
      record.setRequestId(request.getRequestId());
      record.setOperation(operation);
      record.setPrincipalId(principalId);
      record.setPluginId(request.getPluginId());
      record.setDeploymentId(deploymentId);
      record.setIdempotencyKey(key);
      record.setRequestHash(requestHash);
      record.setState("STARTED");
      existing = operationStore.saveIfIdempotencyKeyAbsent(record);
      if (existing == null) {
        return null;
      }
    }
    if (!requestHash.equals(existing.getRequestHash())) {
      throw new PluginManagementException(
          PluginManagementErrorCode.CONFLICT,
          "Idempotency key was reused with a different request",
          409);
    }
    return existing;
  }

  public void markFinished(PluginOperationRecord record, boolean success, String code, String message,
                          String responseBodySummary) {
    if (record == null) {
      return;
    }
    record.setSuccess(success);
    record.setResponseCode(code);
    record.setResponseMessage(PluginManagementResponseSanitizer.safeText(message));
    record.setResponseBodySummary(PluginManagementResponseSanitizer.safeText(responseBodySummary));
    record.setState(success ? "SUCCEEDED" : "FAILED");
    record.setUpdatedAt(System.currentTimeMillis());
    operationStore.save(record);
  }

  private String idempotencyIndexKey(String principalId, PluginManagementOperation operation, String pluginId, String idempotencyKey) {
    String safePrincipalId = principalId == null ? "anonymous" : principalId;
    String safePluginId = pluginId == null ? "" : pluginId;
    return safePrincipalId + ":" + operation.name() + ":" + safePluginId + ":" + idempotencyKey;
  }
}
