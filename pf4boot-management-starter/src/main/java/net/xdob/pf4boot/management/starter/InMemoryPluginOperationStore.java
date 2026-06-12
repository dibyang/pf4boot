package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginOperationRecord;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPluginOperationStore implements PluginOperationStore {

  private final Map<String, PluginOperationRecord> byOperationId = new ConcurrentHashMap<>();
  private final Map<String, String> byIdempotencyKey = new ConcurrentHashMap<>();
  private final Map<String, String> byDeploymentId = new ConcurrentHashMap<>();

  @Override
  public PluginOperationRecord save(PluginOperationRecord record) {
    if (record == null || !StringUtils.hasText(record.getOperationId())) {
      return null;
    }
    long now = System.currentTimeMillis();
    if (record.getCreatedAt() == 0) {
      record.setCreatedAt(now);
    }
    record.setUpdatedAt(now);
    byOperationId.put(record.getOperationId(), record);
    if (StringUtils.hasText(record.getIdempotencyKey())) {
      byIdempotencyKey.put(record.getIdempotencyKey(), record.getOperationId());
    }
    if (StringUtils.hasText(record.getDeploymentId())) {
      byDeploymentId.put(record.getDeploymentId(), record.getOperationId());
    }
    return record;
  }

  @Override
  public PluginOperationRecord saveIfIdempotencyKeyAbsent(PluginOperationRecord record) {
    if (record == null || !StringUtils.hasText(record.getOperationId())
        || !StringUtils.hasText(record.getIdempotencyKey())) {
      save(record);
      return null;
    }
    long now = System.currentTimeMillis();
    if (record.getCreatedAt() == 0) {
      record.setCreatedAt(now);
    }
    record.setUpdatedAt(now);
    byOperationId.put(record.getOperationId(), record);
    String existingOperationId = byIdempotencyKey.putIfAbsent(record.getIdempotencyKey(), record.getOperationId());
    if (existingOperationId != null) {
      byOperationId.remove(record.getOperationId());
      return byOperationId.get(existingOperationId);
    }
    if (StringUtils.hasText(record.getDeploymentId())) {
      byDeploymentId.put(record.getDeploymentId(), record.getOperationId());
    }
    return null;
  }

  @Override
  public PluginOperationRecord findById(String operationId) {
    return byOperationId.get(operationId);
  }

  @Override
  public PluginOperationRecord findByIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) {
      return null;
    }
    String operationId = byIdempotencyKey.get(idempotencyKey);
    if (operationId == null) {
      return null;
    }
    return byOperationId.get(operationId);
  }

  @Override
  public PluginOperationRecord findByDeploymentId(String deploymentId) {
    if (!StringUtils.hasText(deploymentId)) {
      return null;
    }
    String operationId = byDeploymentId.get(deploymentId);
    if (operationId == null) {
      return null;
    }
    return byOperationId.get(operationId);
  }

  @Override
  public List<PluginOperationRecord> recent(int limit) {
    int size = limit <= 0 ? Integer.MAX_VALUE : limit;
    List<PluginOperationRecord> records = new ArrayList<>(byOperationId.values());
    records.sort(Comparator.comparingLong(PluginOperationRecord::getUpdatedAt).reversed());
    if (records.size() <= size) {
      return records;
    }
    return records.subList(0, size);
  }

  @Override
  public List<PluginOperationRecord> scanRecoverableRecords() {
    List<PluginOperationRecord> records = new ArrayList<>();
    for (PluginOperationRecord record : byOperationId.values()) {
      if ("STARTED".equals(record.getState())
          || "EXECUTING".equals(record.getState())
          || "ROLLING_BACK".equals(record.getState())) {
        records.add(record);
      }
    }
    records.sort(Comparator.comparingLong(PluginOperationRecord::getUpdatedAt));
    return records;
  }
}
