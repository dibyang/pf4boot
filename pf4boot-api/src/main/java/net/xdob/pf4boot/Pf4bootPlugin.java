package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootAnnotationConfigApplicationContext;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.util.ClassUtils;

import java.util.*;
import java.util.function.Consumer;

import static net.xdob.pf4boot.Pf4bootPluginManager.BEAN_PLUGIN;

/**
 * Pf4bootPlugin
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPlugin extends Plugin {
  static final Logger LOG = LoggerFactory.getLogger(Pf4bootPlugin.class);
  private final List<Consumer<Pf4bootPlugin>> releaseHooks = new ArrayList<>();

  protected volatile AnnotationConfigApplicationContext pluginContext;

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

  public List<Consumer<Pf4bootPlugin>> getReleaseHooks() {
    return releaseHooks;
  }

  public void addReleaseHook(Consumer<Pf4bootPlugin> hook){
    releaseHooks.add(hook);
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

    Class<?>[] primarySources = getPluginStarter().map(PluginStarter::value).orElse(new Class[]{});

    if (pluginClassLoader instanceof PluginClassLoader4boot) {
      if (pluginFirstClasses != null) {
        ((PluginClassLoader4boot) pluginClassLoader).setPluginFirstClasses(pluginFirstClasses);
      }
      if (pluginOnlyResources != null) {
        ((PluginClassLoader4boot) pluginClassLoader).setPluginOnlyResources(pluginOnlyResources);
      }
    }
		closePluginContext();
    DefaultListableBeanFactory beanFactory = new PluginListableBeanFactory(pluginClassLoader);
    pluginContext = new Pf4bootAnnotationConfigApplicationContext(beanFactory, this);
    pluginContext.setId("plugin-"+getPluginId());
    pluginContext.setClassLoader(pluginClassLoader);
    pluginContext.setParent(platformContext);
    //仅共享Bean定义，不继承事件监听链1
    pluginContext.getBeanFactory().setParentBeanFactory(platformContext.getBeanFactory());
    pluginContext.register(primarySources);
    pluginContext.getBeanFactory().registerSingleton(BEAN_PLUGIN, this);
    pluginContext.getBeanFactory().autowireBean(this);
    LOG.info("[PF4BOOT] create plugin context for {} context parent = {}", getPluginId(), platformContext.getId());
    return pluginContext;
  }

	public void closePluginContext(){
		if (pluginContext != null){
			pluginContext.close();
			pluginContext = null;
		}
	}

  public Optional<PluginStarter> getPluginStarter() {
		return Optional.ofNullable(getClass().getAnnotation(PluginStarter.class));
  }

  public String getGroup() {
		return getPluginStarter().map(PluginStarter::group).orElse(PluginStarter.DEFAULT);
  }

  public void publishEvent(Object event){
    getPluginManager().publishEvent(this.pluginContext, event);
  }

}
