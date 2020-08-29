package com.ls.pf4boot;

import java.util.List;

/**
 * PluginClassLoader4boot
 *
 * @author yangzj
 * @version 1.0
 */
public interface PluginClassLoader4boot {
  void setPluginFirstClasses(List<String> pluginFirstClasses);

  void setPluginOnlyResources(List<String> pluginOnlyResources);
}
