package net.xdob.pf4boot.management;

/**
 * 插件管理部署请求.
 */
public class PluginDeploymentRequest {

  private String pluginId;
  private String stagedPluginPath;
  private Boolean dryRun;

  public String getPluginId() {
    return pluginId;
  }

  public void setPluginId(String pluginId) {
    this.pluginId = pluginId;
  }

  public String getStagedPluginPath() {
    return stagedPluginPath;
  }

  public void setStagedPluginPath(String stagedPluginPath) {
    this.stagedPluginPath = stagedPluginPath;
  }

  public Boolean getDryRun() {
    return dryRun;
  }

  public void setDryRun(Boolean dryRun) {
    this.dryRun = dryRun;
  }
}

