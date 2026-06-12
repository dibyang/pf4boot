package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.deployment.DeploymentRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local in-memory deployment record storage.
 */
public class InMemoryPluginDeploymentRecordStore implements PluginDeploymentRecordStore {

  private final Map<String, DeploymentRecord> byId = new ConcurrentHashMap<>();

  @Override
  public DeploymentRecord save(DeploymentRecord record) {
    if (record == null || record.getDeploymentId() == null) {
      return null;
    }
    byId.put(record.getDeploymentId(), record);
    return record;
  }

  @Override
  public DeploymentRecord findById(String deploymentId) {
    if (deploymentId == null || deploymentId.trim().isEmpty()) {
      return null;
    }
    return byId.get(deploymentId);
  }

  @Override
  public List<DeploymentRecord> recent(int limit) {
    int max = limit <= 0 ? Integer.MAX_VALUE : limit;
    List<DeploymentRecord> records = new ArrayList<>(byId.values());
    records.sort(Comparator.comparingLong(DeploymentRecord::getUpdatedAt).reversed());
    if (records.size() <= max) {
      return records;
    }
    return records.subList(0, max);
  }
}
