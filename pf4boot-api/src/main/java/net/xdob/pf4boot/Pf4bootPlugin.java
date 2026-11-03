package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.util.PluginCleanup;

import org.springframework.context.support.Pf4bootAnnotationConfigApplicationContext;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoaderHelp;

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

  protected volatile AnnotationConfigApplicationContext pluginContext = null;

  private final ClassLoader pluginClassLoader;



  private final Set<String> propNames = new HashSet<>();

  private List<String> pluginFirstClasses;

  private List<String> pluginOnlyResources;

  private final Object lock = new Object();

  public ConfigurableApplicationContext getPluginContext() {
    return pluginContext;
  }

	public void setProperty(String name, String value){
		propNames.add(name);
		System.setProperty(name, value);
	}

	public String getProperty(String name){
		return getProperty(name, null);
	}

	public String getProperty(String name, String defaultValue){
		return System.getProperty(name, defaultValue);
	}

	public Map<String, String> getProperties(){
		Map<String, String> properties = new HashMap<>();
		for (String name : propNames) {
			String property = getProperty(name);
			properties.put(name, property);
		}
		return properties;
	}

	public void clearProperties(String name){
		if(propNames.remove(name)){
			System.clearProperty(name);
		}
	}

	public void clearAllProperties(){
		List<String> names = new ArrayList<>(propNames);
		if(!names.isEmpty()) {
			LOG.info("[PF4BOOT] clear properties for {}", getPluginId());
			for (String name : names) {
				clearProperties(name);
			}
		}
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

  protected ConfigurableApplicationContext createPluginContext(ConfigurableApplicationContext platformContext) {
		closePluginContext();
    Class<?>[] primarySources = getPluginStarter().map(PluginStarter::value).orElse(new Class[]{});

    if (pluginClassLoader instanceof PluginClassLoader4boot) {
      if (pluginFirstClasses != null) {
        ((PluginClassLoader4boot) pluginClassLoader).setPluginFirstClasses(pluginFirstClasses);
      }
      if (pluginOnlyResources != null) {
        ((PluginClassLoader4boot) pluginClassLoader).setPluginOnlyResources(pluginOnlyResources);
      }
    }
    synchronized (lock) {
			if (pluginContext == null) {
        ClassLoader classLoader = this.pluginClassLoader;

        PluginListableBeanFactory beanFactory = new PluginListableBeanFactory(classLoader);

				beanFactory.setParentBeanFactory(platformContext.getBeanFactory());
				pluginContext = new Pf4bootAnnotationConfigApplicationContext(beanFactory, this);
				pluginContext.setId("plugin-" + getPluginId());
        pluginContext.setClassLoader(classLoader);
				//pluginContext.setParent(platformContext);
				//仅共享Bean定义，不继承事件监听链1
				//pluginContext.getBeanFactory().setParentBeanFactory(platformContext.getBeanFactory());
				pluginContext.register(primarySources);
				pluginContext.getBeanFactory().registerSingleton(BEAN_PLUGIN, this);
        beanFactory.registerDisposableBean(BEAN_PLUGIN, () -> beanFactory.destroyBean(this));
				pluginContext.getBeanFactory().autowireBean(this);
				LOG.info("[PF4BOOT] create plugin context for {} context parent = {}", getPluginId(), platformContext.getId());
			}
			return pluginContext;
		}
  }

	public void closePluginContext(){
		synchronized (lock) {
			if (pluginContext != null){
        PluginCleanup cleanup = new PluginCleanup();
        cleanup.run("close context", () -> freeContext(pluginContext));
        cleanup.run("clear properties", this::clearAllProperties);
        // 失败时保留上下文以便再次清理，禁止把关闭失败伪装成已释放。
        cleanup.throwIfFailed();
        //释放插件上下文
				pluginContext = null;

				LOG.info("[PF4BOOT] close plugin context for {}", getPluginId());
			}
		}
	}

  private void freeContext( AnnotationConfigApplicationContext context) {
    DefaultListableBeanFactory beanFactory = context.getDefaultListableBeanFactory();
    PluginCleanup cleanup = new PluginCleanup();
    if (!context.isActive() && beanFactory instanceof PluginListableBeanFactory) {
      cleanup.run("retry destruction", () -> ((PluginListableBeanFactory) beanFactory).retryDestruction());
    }
    cleanup.run("close Spring context", context::close);
    if (beanFactory instanceof PluginListableBeanFactory) {
      cleanup.run("verify destruction", () -> ((PluginListableBeanFactory) beanFactory).verifyDestruction());
    }
    cleanup.run("clear metadata", beanFactory::clearMetadataCache);

    // stop 后还会复用原始 loader；只有 unload 才关闭它，保留 CGLIB 类命名缓存。
    cleanup.run("clear Spring factories cache", SpringFactoriesLoaderHelp::clearCache);
    cleanup.throwIfFailed();
    context.setClassLoader(null);
  }


  public Optional<PluginStarter> getPluginStarter() {
		return Optional.ofNullable(getClass().getAnnotation(PluginStarter.class));
  }

  public String getGroup() {
		return getPluginStarter().map(PluginStarter::group).orElse(PluginStarter.DEFAULT);
  }

  public void publishEvent(Object event){
    getPluginManager().publishEvent(this.getPluginContext(), event);
  }

}
