package net.xdob.pf4boot.management;

/**
 * 插件管理部署请求.
 */
public class PluginDeploymentRequest {

  private String pluginId;
  private String stagedPluginPath;
  private String repositoryVersion;
  private String repositoryVersionRange;
  private Boolean repositoryRollback;
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

  public String getRepositoryVersion() {
    return repositoryVersion;
  }

  public void setRepositoryVersion(String repositoryVersion) {
    this.repositoryVersion = repositoryVersion;
  }

  public String getRepositoryVersionRange() {
    return repositoryVersionRange;
  }

  public void setRepositoryVersionRange(String repositoryVersionRange) {
    this.repositoryVersionRange = repositoryVersionRange;
  }

  public Boolean getRepositoryRollback() {
    return repositoryRollback;
  }

  public void setRepositoryRollback(Boolean repositoryRollback) {
    this.repositoryRollback = repositoryRollback;
  }

  public Boolean getDryRun() {
    return dryRun;
  }

  public void setDryRun(Boolean dryRun) {
    this.dryRun = dryRun;
  }
}
