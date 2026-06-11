package net.xdob.pf4boot.modal;

import org.pf4j.PluginState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件运行时快照。
 *
 * <p>用于 actuator 等只读观测面返回插件状态，不包含任何可触发生命周期变更的命令字段。</p>
 */
public class PluginRuntimeSnapshot {

  private String pluginId;
  private String version;
  private PluginState state;
  private String pluginPath;
  private long lastStartDurationMillis = -1;
  private List<String> dependencies = new ArrayList<>();
  private Map<String, Integer> resourceCounts = new LinkedHashMap<>();
  private String errorMessage;
  private String errorDetail;

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

  public PluginState getState() {
    return state;
  }

  public void setState(PluginState state) {
    this.state = state;
  }

  public String getPluginPath() {
    return pluginPath;
  }

  public void setPluginPath(String pluginPath) {
    this.pluginPath = pluginPath;
  }

  public long getLastStartDurationMillis() {
    return lastStartDurationMillis;
  }

  public void setLastStartDurationMillis(long lastStartDurationMillis) {
    this.lastStartDurationMillis = lastStartDurationMillis;
  }

  public List<String> getDependencies() {
    return dependencies;
  }

  public void setDependencies(List<String> dependencies) {
    this.dependencies = dependencies;
  }

  public Map<String, Integer> getResourceCounts() {
    return resourceCounts;
  }

  public void setResourceCounts(Map<String, Integer> resourceCounts) {
    this.resourceCounts = resourceCounts;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public void setErrorDetail(String errorDetail) {
    this.errorDetail = errorDetail;
  }
}
