package com.ls.pf4boot;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * Pf4bootPlugin
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPlugin extends Plugin {
  /**
   * Constructor to be used by plugin manager for plugin instantiation.
   * Your plugins have to provide constructor with this exact signature to
   * be successfully loaded by manager.
   *
   * @param wrapper
   */
  public Pf4bootPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }
}