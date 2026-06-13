package net.xdob.pf4boot.repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件仓库中的单个 release 记录。
 */
public class PluginReleaseRecord {

  private String repositoryId;
  private String pluginId;
  private String version;
  private String packagePath;
  private String packageSha256;
  private String trustManifestPath;
  private String rolloutPolicy;
  private boolean rollbackCandidate;
  private Map<String, String> attributes = new LinkedHashMap<>();

  public String getRepositoryId() {
    return repositoryId;
  }

  public void setRepositoryId(String repositoryId) {
    this.repositoryId = repositoryId;
  }

  public String getPluginId() {
    return pluginId;
  }

  public void setPluginId(String pluginId) {
    this.pluginId = pluginId;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getPackagePath() {
    return packagePath;
  }

  public void setPackagePath(String packagePath) {
    this.packagePath = packagePath;
  }

  public String getPackageSha256() {
    return packageSha256;
  }

  public void setPackageSha256(String packageSha256) {
    this.packageSha256 = packageSha256;
  }

  public String getTrustManifestPath() {
    return trustManifestPath;
  }

  public void setTrustManifestPath(String trustManifestPath) {
    this.trustManifestPath = trustManifestPath;
  }

  public String getRolloutPolicy() {
    return rolloutPolicy;
  }

  public void setRolloutPolicy(String rolloutPolicy) {
    this.rolloutPolicy = rolloutPolicy;
  }

  public boolean isRollbackCandidate() {
    return rollbackCandidate;
  }

  public void setRollbackCandidate(boolean rollbackCandidate) {
    this.rollbackCandidate = rollbackCandidate;
  }

  public Map<String, String> getAttributes() {
    return Collections.unmodifiableMap(attributes);
  }

  public void setAttributes(Map<String, String> attributes) {
    this.attributes = attributes == null
        ? new LinkedHashMap<String, String>()
        : new LinkedHashMap<String, String>(attributes);
  }
}
