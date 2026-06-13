package net.xdob.pf4boot.repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件仓库 release 解析请求。
 */
public class PluginReleaseRequest {

  private String pluginId;
  private String version;
  private String versionRange;
  private boolean rollback;
  private Map<String, String> attributes = new LinkedHashMap<>();

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

  public String getVersionRange() {
    return versionRange;
  }

  public void setVersionRange(String versionRange) {
    this.versionRange = versionRange;
  }

  public boolean isRollback() {
    return rollback;
  }

  public void setRollback(boolean rollback) {
    this.rollback = rollback;
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
