package net.xdob.pf4boot.deployment;

/**
 * 插件部署指标快照。
 */
public class PluginDeploymentMetricsSnapshot {

  private final long deploymentTotal;
  private final long rollbackTotal;
  private final long failedTotal;
  private final long lastDurationMillis;

  public PluginDeploymentMetricsSnapshot(
      long deploymentTotal,
      long rollbackTotal,
      long failedTotal,
      long lastDurationMillis) {
    this.deploymentTotal = deploymentTotal;
    this.rollbackTotal = rollbackTotal;
    this.failedTotal = failedTotal;
    this.lastDurationMillis = lastDurationMillis;
  }

  public long getDeploymentTotal() {
    return deploymentTotal;
  }

  public long getRollbackTotal() {
    return rollbackTotal;
  }

  public long getFailedTotal() {
    return failedTotal;
  }

  public long getLastDurationMillis() {
    return lastDurationMillis;
  }
}
