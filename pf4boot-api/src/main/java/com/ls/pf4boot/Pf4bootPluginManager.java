package com.ls.pf4boot;

import com.ls.pf4boot.modal.PluginStartingError;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginManager;
import org.pf4j.PluginRepository;
import org.pf4j.PluginState;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * Pf4bootPluginManager
 *
 * @author yangzj
 * @version 1.0
 */
public interface Pf4bootPluginManager extends PluginManager {
  void setAutoStartPlugin(boolean autoStartPlugin);

  boolean isAutoStartPlugin();

  void setMainApplicationStarted(boolean mainApplicationStarted);

  void setProfiles(String[] profiles);

  String[] getProfiles();

  void presetProperties(Map<String, Object> presetProperties);

  void presetProperties(String name, Object value);

  Map<String, Object> getPresetProperties();

  abstract ApplicationContext getMainApplicationContext();

  abstract boolean isMainApplicationStarted();

  void restartPlugins();

  PluginState restartPlugin(String pluginId);

  void reloadPlugins(boolean restartStartedOnly);

  PluginState reloadPlugins(String pluginId);

  void setExactVersionAllowed(boolean exactVersionAllowed);

  PluginRepository getPluginRepository();

  PluginDescriptorFinder getPluginDescriptorFinder();

  PluginStartingError getPluginStartingError(String pluginId);
}
