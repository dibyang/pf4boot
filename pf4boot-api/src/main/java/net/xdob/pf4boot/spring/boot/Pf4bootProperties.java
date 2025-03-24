package net.xdob.pf4boot.spring.boot;

import org.pf4j.PluginLoader;
import org.pf4j.RuntimeMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = Pf4bootProperties.PREFIX)
public class Pf4bootProperties {

  public static final String PREFIX = "spring.pf4boot";
  /**
   * properties define under this property will be passed to
   * plugin `ApplicationContext` environment.
   */
  Map<String, Object> pluginProperties = new HashMap<>();
  private boolean enabled = false;

  private boolean pluginAdminEnabled = true;
  /**
   * Auto start plugin when main app is ready
   */
  private boolean autoStartPlugin = true;
  /**
   * Plugins disabled by default
   */
  private String[] disabledPlugins;
  /**
   * Plugins enabled by default, prior to `disabledPlugins`
   */
  private String[] enabledPlugins;
  /**
   * Set to true to allow requires expression to be exactly x.y.z. The default is
   * false, meaning that using an exact version x.y.z will implicitly mean the
   * same as >=x.y.z
   */
  private boolean exactVersionAllowed = false;
  /**
   * Extended Plugin Class Directory
   */
  private List<String> classesDirectories = new ArrayList<>();
  /**
   * Extended Plugin Jar Directory
   */
  private List<String> libDirectories = new ArrayList<>();
  /**
   * Runtime Mode：development/deployment
   */
  private RuntimeMode runtimeMode = RuntimeMode.DEPLOYMENT;
  /**
   * Plugin root directory: default “plugins”; when non-jar mode plugin, the value
   * should be an absolute directory address
   */
  private String pluginsRoot = "plugins";
  /**
   * Allows to provide custom plugin loaders
   */
  private Class<PluginLoader> customPluginLoader;
  /**
   * Profile for plugin Spring {@link ApplicationContext}
   */
  private String[] pluginProfiles = new String[]{"plugin"};
  /**
   * The system version used for comparisons to the plugin requires attribute.
   */
  private String systemVersion = "0.0.0";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isPluginAdminEnabled() {
    return pluginAdminEnabled;
  }

  public void setPluginAdminEnabled(boolean pluginAdminEnabled) {
    this.pluginAdminEnabled = pluginAdminEnabled;
  }

  public boolean isAutoStartPlugin() {
    return autoStartPlugin;
  }

  public void setAutoStartPlugin(boolean autoStartPlugin) {
    this.autoStartPlugin = autoStartPlugin;
  }

  public String[] getDisabledPlugins() {
    return disabledPlugins;
  }

  public void setDisabledPlugins(String[] disabledPlugins) {
    this.disabledPlugins = disabledPlugins;
  }

  public String[] getEnabledPlugins() {
    return enabledPlugins;
  }

  public void setEnabledPlugins(String[] enabledPlugins) {
    this.enabledPlugins = enabledPlugins;
  }

  public boolean isExactVersionAllowed() {
    return exactVersionAllowed;
  }

  public void setExactVersionAllowed(boolean exactVersionAllowed) {
    this.exactVersionAllowed = exactVersionAllowed;
  }

  public List<String> getClassesDirectories() {
    return classesDirectories;
  }

  public void setClassesDirectories(List<String> classesDirectories) {
    this.classesDirectories = classesDirectories;
  }

  public List<String> getLibDirectories() {
    return libDirectories;
  }

  public void setLibDirectories(List<String> libDirectories) {
    this.libDirectories = libDirectories;
  }

  public RuntimeMode getRuntimeMode() {
    return runtimeMode;
  }

  public void setRuntimeMode(String runtimeMode) {
    this.runtimeMode = RuntimeMode.byName(runtimeMode);
  }

  public String getPluginsRoot() {
    return pluginsRoot;
  }

  public void setPluginsRoot(String pluginsRoot) {
    this.pluginsRoot = pluginsRoot;
  }

  public Class<PluginLoader> getCustomPluginLoader() {
    return customPluginLoader;
  }

  public void setCustomPluginLoader(Class<PluginLoader> customPluginLoader) {
    this.customPluginLoader = customPluginLoader;
  }

  public String[] getPluginProfiles() {
    return pluginProfiles;
  }

  public void setPluginProfiles(String[] pluginProfiles) {
    this.pluginProfiles = pluginProfiles;
  }

  public Map<String, Object> getPluginProperties() {
    return pluginProperties;
  }

  public void setPluginProperties(Map<String, Object> pluginProperties) {
    this.pluginProperties = pluginProperties;
  }

  public String getSystemVersion() {
    return systemVersion;
  }

  public void setSystemVersion(String systemVersion) {
    this.systemVersion = systemVersion;
  }
}
