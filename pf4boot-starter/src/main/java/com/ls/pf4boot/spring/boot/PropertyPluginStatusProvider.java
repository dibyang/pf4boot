package com.ls.pf4boot.spring.boot;

import org.pf4j.PluginStatusProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PropertyPluginStatusProvider implements PluginStatusProvider {

  private List<String> enabledPlugins;
  private List<String> disabledPlugins;

  public PropertyPluginStatusProvider(Pf4bootProperties sbpProperties) {
    this.enabledPlugins = sbpProperties.getEnabledPlugins() != null
        ? Arrays.asList(sbpProperties.getEnabledPlugins()) : new ArrayList<>();
    this.disabledPlugins = sbpProperties.getDisabledPlugins() != null
        ? Arrays.asList(sbpProperties.getDisabledPlugins()) : new ArrayList<>();
  }

  public static boolean isPropertySet(Pf4bootProperties sbpProperties) {
    return sbpProperties.getEnabledPlugins() != null && sbpProperties.getEnabledPlugins().length > 0
        || sbpProperties.getDisabledPlugins() != null && sbpProperties.getDisabledPlugins().length > 0;
  }

  @Override
  public boolean isPluginDisabled(String pluginId) {
    if (disabledPlugins.contains(pluginId)) return true;
    return !enabledPlugins.isEmpty() && !enabledPlugins.contains(pluginId);
  }

  @Override
  public void disablePlugin(String pluginId) {
    if (isPluginDisabled(pluginId)) return;
    disabledPlugins.add(pluginId);
    enabledPlugins.remove(pluginId);
  }

  @Override
  public void enablePlugin(String pluginId) {
    if (!isPluginDisabled(pluginId)) return;
    enabledPlugins.add(pluginId);
    disabledPlugins.remove(pluginId);
  }
}
