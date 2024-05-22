package net.xdob.pf4boot;

import net.xdob.pf4boot.modal.PluginStartingError;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginManager;
import org.pf4j.PluginRepository;
import org.pf4j.PluginState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

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

  ConfigurableApplicationContext getMainApplicationContext();

  boolean isMainApplicationStarted();

  void restartPlugins();

  PluginState restartPlugin(String pluginId);

  void reloadPlugins(boolean restartStartedOnly);

  PluginState reloadPlugins(String pluginId);

  void setExactVersionAllowed(boolean exactVersionAllowed);

  PluginRepository getPluginRepository();

  PluginDescriptorFinder getPluginDescriptorFinder();

  PluginStartingError getPluginStartingError(String pluginId);

  Pf4bootEventBus getPf4bootEventBus();

  void post(Object event);

  /**
   * 注册bean到主上文
   * @param beanName
   * @param bean
   */
  void registerBeanToMainContext(String beanName, Object bean);

  /**
   * 取消注册bean从主上文
   * @param beanName
   */
  void unregisterBeanFromMainContext(String beanName);
}
