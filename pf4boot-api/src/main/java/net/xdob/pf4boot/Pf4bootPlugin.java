package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.Pf4bootAnnotationConfigApplicationContext;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoaderHelp;

import java.io.Closeable;
import java.io.IOException;
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

  public ConfigurableApplicationContext createPluginContext(ConfigurableApplicationContext platformContext) {
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
        DelegatingPluginClassLoader classLoader = new DelegatingPluginClassLoader(this.getPluginId(), this.pluginClassLoader);

        DefaultListableBeanFactory beanFactory = new PluginListableBeanFactory(classLoader);

				beanFactory.setParentBeanFactory(platformContext.getBeanFactory());
				pluginContext = new Pf4bootAnnotationConfigApplicationContext(beanFactory, this);
				pluginContext.setId("plugin-" + getPluginId());
        pluginContext.setClassLoader(classLoader);
				//pluginContext.setParent(platformContext);
				//仅共享Bean定义，不继承事件监听链1
				//pluginContext.getBeanFactory().setParentBeanFactory(platformContext.getBeanFactory());
				pluginContext.register(primarySources);
				pluginContext.getBeanFactory().registerSingleton(BEAN_PLUGIN, this);
				pluginContext.getBeanFactory().autowireBean(this);
				LOG.info("[PF4BOOT] create plugin context for {} context parent = {}", getPluginId(), platformContext.getId());
			}
			return pluginContext;
		}
  }

	public void closePluginContext(){
		synchronized (lock) {
			if (pluginContext != null){
				try {
					//释放插件注册资源
					this.getPluginManager().releasePlugin(this);
				} catch (Exception e) {
					LOG.warn("[PF4BOOT] release plugin error", e);
				}
        DefaultListableBeanFactory beanFactory = pluginContext.getDefaultListableBeanFactory();
        try {
					beanFactory.destroyBean(BEAN_PLUGIN);
				} catch (Exception e) {
					LOG.warn("[PF4BOOT] destroy bean error", e);
				}

        try {
          for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition bd = beanFactory.getBeanDefinition(name);
            String beanClassName = bd.getBeanClassName();
            if (beanClassName != null &&
                beanClassName.startsWith("com.sun.proxy.$Proxy")) {
              beanFactory.removeBeanDefinition(name);
              LOG.info("[PF4BOOT] remove BeanDefinition for {}, beanClassName = {}", name, beanClassName);
            }
          }
        } catch (Exception e) {
          LOG.warn("[PF4BOOT] remove BeanDefinition error", e);
        }

				try {
					pluginContext.close();
				} catch (Exception e) {
					LOG.warn("[PF4BOOT] close plugin context error", e);
				}

        try {
          beanFactory.clearMetadataCache();
        } catch (Exception e) {
          LOG.warn("[PF4BOOT] clearMetadataCache error", e);
        }

        ClassLoader classLoader = pluginContext.getClassLoader();
        pluginContext.setClassLoader( null);
        if(classLoader!=null){
          if(classLoader instanceof Closeable){
            try {
              ((Closeable) classLoader).close();
            } catch (IOException e) {
              LOG.warn("[PF4BOOT] close plugin classloader error", e);
            }
          }
        }

        SpringFactoriesLoaderHelp.clearCache();
				//释放插件上下文
				pluginContext = null;
				if(pluginClassLoader instanceof Cleaner) {
					((Cleaner) pluginClassLoader).cleanup();
				}

				clearAllProperties();
				LOG.info("[PF4BOOT] close plugin context for {}", getPluginId());
			}
		}
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
