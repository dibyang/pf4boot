package net.xdob.pf4boot;

import net.xdob.pf4boot.modal.PluginError;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginManager;
import org.pf4j.PluginRepository;
import org.pf4j.PluginState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

/**
 * Pf4bootPluginManager
 *
 * @author yangzj
 * @version 1.0
 */
public interface Pf4bootPluginManager extends PluginManager {
  String BEAN_PLUGIN = "pf4j.plugin";
  void setAutoStartPlugin(boolean autoStartPlugin);

  boolean isAutoStartPlugin();

  void setApplicationStarted(boolean mainApplicationStarted);

  void setProfiles(String[] profiles);

  String[] getProfiles();

  void presetProperties(Map<String, Object> presetProperties);

  void presetProperties(String name, Object value);

  Map<String, Object> getPresetProperties();
  /**
   * 根级上下文，全局可见
   */
  ConfigurableApplicationContext getRootContext();

  /**
   * 应用上下文
   */
  ConfigurableApplicationContext getApplicationContext();

  /**
   * 平台级上下文，插件间共享
   */
  ConfigurableApplicationContext getPlatformContext();
  boolean isApplicationStarted();

  void restartPlugins();

  PluginState restartPlugin(String pluginId);

  void reloadPlugins(boolean restartStartedOnly);

  PluginState reloadPlugins(String pluginId);

  void setExactVersionAllowed(boolean exactVersionAllowed);

  PluginRepository getPluginRepository();

  PluginDescriptorFinder getPluginDescriptorFinder();

  PluginError getPluginErrors(String pluginId);

  void publishEvent(Object event);
  /**
   * 注册bean到根级上下文，全局可见
   */
  void registerBeanToRootContext(String beanName, Object bean);

  /**
   * 取消注册bean从根级上下文，全局可见
   * @param beanName bean名称
   */
  void unregisterBeanFromRootContext(String beanName);


  /**
   * 注册bean到平台上文
   * @param beanName bean名称
   * @param bean bean实例
   */
  void registerBeanToPlatformContext(String beanName, Object bean);

  /**
   * 取消注册bean从平台上文
   * @param beanName bean名称
   */
  void unregisterBeanFromPlatformContext(String beanName);

  /**
   * 注册bean到主应用上文
   * @param beanName bean名称
   * @param bean bean实例
   */
  void registerBeanToApplicationContext(String beanName, Object bean);

  /**
   * 取消注册bean从主应用上文
   * @param beanName bean名称
   */
  void unregisterBeanFromApplicationContext(String beanName);
}
