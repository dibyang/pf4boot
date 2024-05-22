package net.xdob.pf4boot;

import com.google.common.base.Preconditions;
import net.xdob.pf4boot.annotation.EventListener;
import net.xdob.pf4boot.annotation.Export;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootApplication;
import org.pf4j.Plugin;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Map;
import java.util.Set;

/**
 * Pf4bootPlugin
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPlugin extends Plugin {

  protected final Pf4bootApplication application;

  protected ConfigurableApplicationContext applicationContext;

  public Pf4bootApplication getApplication() {
    return application;
  }

  public ConfigurableApplicationContext getApplicationContext() {
    return applicationContext;
  }

  public void setApplicationContext(ConfigurableApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  public Pf4bootPluginManager getPluginManager(){
    return TypeWrapper.wrapper(this.getWrapper().getPluginManager(), Pf4bootPluginManager.class)
        .orElse(null);
  }

  /**
   * Constructor to be used by plugin manager for plugin instantiation.
   * Your plugins have to provide constructor with this exact signature to
   * be successfully loaded by manager.
   *
   * @param wrapper
   */
  public Pf4bootPlugin(PluginWrapper wrapper) {
    super(wrapper);
    PluginStarter pluginStarter = getClass().getAnnotation(PluginStarter.class);
    Preconditions.checkState(pluginStarter!=null,"PluginStarter annotation is missing.");
    Class<?>[] starterClasses = pluginStarter.value();
    application = new Pf4bootApplication(this, starterClasses);
  }


}
