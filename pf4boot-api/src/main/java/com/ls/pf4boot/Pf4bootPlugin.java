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

  private SpringApplication application = null;

  private ApplicationContext applicationContext = null;


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

  public SpringApplication getApplication() {
    return application;
  }

  public void setApplication(SpringApplication application) {
    this.application = application;
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  public GenericApplicationContext getMainApplicationContext() {
    return (GenericApplicationContext) getPluginManager().getMainApplicationContext();
  }

  private Pf4bootPluginManager getPluginManager() {
    return (Pf4bootPluginManager)this.getWrapper().getPluginManager();
  }
}
