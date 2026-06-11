package net.xdob.pf4boot.deployment;

import org.pf4j.PluginState;

/**
 * 插件健康检查上下文。
 */
public class PluginHealthContext {

  private final String deploymentId;
  private final String pluginId;
  private final PluginState pluginState;

  public PluginHealthContext(String deploymentId, String pluginId, PluginState pluginState) {
    this.deploymentId = deploymentId;
    this.pluginId = pluginId;
    this.pluginState = pluginState;
  }

  public String getDeploymentId() {
    return deploymentId;
  }

  public String getPluginId() {
    return pluginId;
  }

  public PluginState getPluginState() {
    return pluginState;
  }
}
