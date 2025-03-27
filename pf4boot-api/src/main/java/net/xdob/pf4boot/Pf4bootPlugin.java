package net.xdob.pf4boot;

import com.google.common.base.Preconditions;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootApplication;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Pf4bootPlugin
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPlugin extends Plugin {

  private Pf4bootApplication application;

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

  /**
   * 插件初始化,可以在spring初始化之前执行一些环境准备类的工作。
   * 比如设置或读取系统配置啥的
   */
  public void initiate() {

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
    Preconditions.checkState(pluginStarter != null, "PluginStarter annotation is missing.");
    Class<?>[] starterClasses = pluginStarter.value();
    application = new Pf4bootApplication(this, starterClasses);
  }


}
