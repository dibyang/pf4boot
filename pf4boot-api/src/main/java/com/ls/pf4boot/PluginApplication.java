package com.ls.pf4boot;

/**
 *
 *
 * @author yangzj
 * @version 1.0
 *  Mark the interface as a plug-in application
 */
public interface PluginApplication {
  public final static String BEAN_PLUGIN = "pf4j.plugin";
  Pf4bootPlugin getPlugin();
}
