package com.ls.pf4boot;

import org.pf4j.Plugin;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

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
