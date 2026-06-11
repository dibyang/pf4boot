package net.xdob.pf4boot.deployment;

/**
 * 插件部署指标提供者。
 */
public interface PluginDeploymentMetricsProvider {

  /**
   * 返回当前部署指标快照。
   */
  PluginDeploymentMetricsSnapshot snapshot();
}
