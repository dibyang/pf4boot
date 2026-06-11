package net.xdob.pf4boot.deployment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认内存部署记录器。
 *
 * <p>该实现用于第一阶段观测和 actuator 指标，不承担长期审计持久化职责。</p>
 */
public class DefaultPluginDeploymentRecorder
    implements PluginDeploymentRecorder, PluginDeploymentMetricsProvider {

  private final Map<String, DeploymentRecord> records = new ConcurrentHashMap<>();
  private final AtomicLong deploymentTotal = new AtomicLong();
  private final AtomicLong rollbackTotal = new AtomicLong();
  private final AtomicLong failedTotal = new AtomicLong();
  private final AtomicLong lastDurationMillis = new AtomicLong(-1);

  @Override
  public void record(DeploymentRecord record) {
    if (record == null) {
      return;
    }
    records.put(record.getDeploymentId(), record);
    if (!DeploymentState.PRECHECKED.equals(record.getState())) {
      deploymentTotal.incrementAndGet();
    }
    if (record.getStateHistory().contains(DeploymentState.ROLLING_BACK)) {
      rollbackTotal.incrementAndGet();
    }
    if (DeploymentState.FAILED.equals(record.getState())
        || DeploymentState.MANUAL_INTERVENTION.equals(record.getState())) {
      failedTotal.incrementAndGet();
    }
    if (record.getDurationMillis() >= 0) {
      lastDurationMillis.set(record.getDurationMillis());
    }
  }

  public DeploymentRecord getRecord(String deploymentId) {
    return records.get(deploymentId);
  }

  @Override
  public PluginDeploymentMetricsSnapshot snapshot() {
    return new PluginDeploymentMetricsSnapshot(
        deploymentTotal.get(),
        rollbackTotal.get(),
        failedTotal.get(),
        lastDurationMillis.get());
  }
}
