package com.ls.pf4boot.spring.boot;


import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = Pf4bootPluginProperties.PREFIX)
public class Pf4bootPluginProperties {

  public static final String PREFIX = "pf4boot-plugin";

  /**
   * Load these classes from plugin classpath first,
   * e.g Spring Boot AutoConfiguration used in plugin only.
   */
  private String[] pluginFirstClasses = {};
  /**
   * Load these resource from plugin classpath only
   */
  private String[] pluginOnlyResources = {};

  public String[] getPluginFirstClasses() {
    return pluginFirstClasses;
  }

  public void setPluginFirstClasses(String[] pluginFirstClasses) {
    this.pluginFirstClasses = pluginFirstClasses;
  }

  public String[] getPluginOnlyResources() {
    return pluginOnlyResources;
  }

  public void setPluginOnlyResources(String[] pluginOnlyResources) {
    this.pluginOnlyResources = pluginOnlyResources;
  }
}
