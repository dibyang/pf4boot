package net.xdob.pf4boot.deployment;

import org.pf4j.PluginState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署回滚快照。
 *
 * <p>快照记录替换前的旧包位置和影响链启动状态，后续真正执行替换时可据此恢复。</p>
 */
public class RollbackSnapshot {

  private final String pluginId;
  private final String pluginPath;
  private final String version;
  private final PluginState pluginState;
  private final List<String> startedPluginIds;
  private final Map<String, String> pluginPaths;

  public RollbackSnapshot(
      String pluginId,
      String pluginPath,
      String version,
      PluginState pluginState,
      List<String> startedPluginIds) {
    this(pluginId, pluginPath, version, pluginState, startedPluginIds, null);
  }

  public RollbackSnapshot(
      String pluginId,
      String pluginPath,
      String version,
      PluginState pluginState,
      List<String> startedPluginIds,
      Map<String, String> pluginPaths) {
    this.pluginId = pluginId;
    this.pluginPath = pluginPath;
    this.version = version;
    this.pluginState = pluginState;
    this.startedPluginIds = unmodifiableCopy(startedPluginIds);
    this.pluginPaths = unmodifiableMap(pluginPaths);
  }

  private static List<String> unmodifiableCopy(List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }

  private static Map<String, String> unmodifiableMap(Map<String, String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  public String getPluginId() {
    return pluginId;
  }

  public String getPluginPath() {
    return pluginPath;
  }

  public String getVersion() {
    return version;
  }

  public PluginState getPluginState() {
    return pluginState;
  }

  public List<String> getStartedPluginIds() {
    return startedPluginIds;
  }

  public Map<String, String> getPluginPaths() {
    return pluginPaths;
  }
}
