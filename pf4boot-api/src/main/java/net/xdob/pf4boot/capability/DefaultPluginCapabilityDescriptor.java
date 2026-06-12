package net.xdob.pf4boot.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认不可变能力描述符实现。
 */
public class DefaultPluginCapabilityDescriptor implements PluginCapabilityDescriptor {

  private final String pluginId;
  private final List<PluginCapability> provides;
  private final List<PluginCapabilityRequirement> requires;

  public DefaultPluginCapabilityDescriptor(
      String pluginId,
      List<PluginCapability> provides,
      List<PluginCapabilityRequirement> requires) {
    this.pluginId = pluginId;
    this.provides = immutableList(provides);
    this.requires = immutableList(requires);
  }

  @Override
  public String getPluginId() {
    return pluginId;
  }

  @Override
  public List<PluginCapability> provides() {
    return provides;
  }

  @Override
  public List<PluginCapabilityRequirement> requires() {
    return requires;
  }

  private static <T> List<T> immutableList(List<T> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }
}
