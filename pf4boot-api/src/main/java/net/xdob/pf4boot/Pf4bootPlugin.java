package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootAnnotationConfigApplicationContext;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.xdob.pf4boot.Pf4bootPluginManager.BEAN_PLUGIN;

/**
 * Pf4bootPlugin
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPlugin extends Plugin {

  protected AnnotationConfigApplicationContext pluginContext;

  private final ClassLoader pluginClassLoader;



  //private final HashSet<String> sharedBeanNames = new HashSet<>();

  //private final HashSet<String> importedBeanNames = new HashSet<>();

  private final Map<String, Object> presetProperties = new HashMap<>();

  private List<String> pluginFirstClasses;

  private List<String> pluginOnlyResources;

//  public Pf4bootApplication getApplication() {
//    return application;
//  }

  public ConfigurableApplicationContext getPluginContext() {
    return pluginContext;
  }

//  public void setApplicationContext(ConfigurableApplicationContext applicationContext) {
//    this.applicationContext = applicationContext;
//  }

  /**
   * 插件初始化,可以在spring初始化之前执行一些环境准备类的工作。
   * 比如设置或读取系统配置啥的
   */
  public void initiate() {

  }

  /**
   * 插件关闭，可以在spring关闭后执行一些清理工作。
   */
  public void closed(){

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
    pluginClassLoader = wrapper.getPluginClassLoader();
  }

  public String getPluginId(){
    return getWrapper().getPluginId();
  }

  public ConfigurableApplicationContext createPluginContext(ConfigurableApplicationContext platformContext) {

    PluginStarter pluginStarter = getClass().getAnnotation(PluginStarter.class);
    Class<?>[] primarySources = pluginStarter.value();

    if (pluginClassLoader instanceof PluginClassLoader4boot) {
      if (pluginFirstClasses != null) {
        ((PluginClassLoader4boot) pluginClassLoader).setPluginFirstClasses(pluginFirstClasses);
      }
      if (pluginOnlyResources != null) {
        ((PluginClassLoader4boot) pluginClassLoader).setPluginOnlyResources(pluginOnlyResources);
      }
    }

    DefaultListableBeanFactory beanFactory = new PluginListableBeanFactory(pluginClassLoader);
    pluginContext = new Pf4bootAnnotationConfigApplicationContext(beanFactory, this);

    pluginContext.setClassLoader(pluginClassLoader);
    pluginContext.setParent(platformContext);
    pluginContext.register(primarySources);
    pluginContext.getBeanFactory().registerSingleton(BEAN_PLUGIN, this);
    pluginContext.getBeanFactory().autowireBean(this);
    return pluginContext;
  }


}
