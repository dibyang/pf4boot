package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.EventListener;
import net.xdob.pf4boot.annotation.Export;
import net.xdob.pf4boot.internal.SpringExtensionFactory;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.Set;

public class DefaultPf4bootPluginSupport implements Pf4bootPluginSupport{
  static final Logger log = LoggerFactory.getLogger(DefaultPf4bootPluginSupport.class);

  @Override
  public int getPriority() {
    return Pf4bootPluginSupport.HEIGHT_PRIORITY;
  }

  @Override
  public void startPlugin(Pf4bootPlugin pf4bootPlugin) {

    //register EventListeners
    registerEventListeners(pf4bootPlugin);
    //register ShareServices
    registerShareServices(pf4bootPlugin);
    // register Extensions
    registerExtensions(pf4bootPlugin);


  }

  private void registerEventListeners(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    Map<String, Object> beans = pf4bootPlugin.getApplicationContext().getBeansWithAnnotation(EventListener.class);
    for (String beanName : beans.keySet()) {
      Object bean = beans.get(beanName);
      pluginManager.getPf4bootEventBus().register(bean);
    }
  }

  private void registerExtensions(Pf4bootPlugin pf4bootPlugin) {
    PluginWrapper wrapper = pf4bootPlugin.getWrapper();
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    Set<String> extensionClassNames = pluginManager.getExtensionClassNames(wrapper.getPluginId());
    for (String extensionClassName : extensionClassNames) {
      try {
        log.debug("Register extension <{}> to main ApplicationContext", extensionClassName);
        Class<?> extensionClass = wrapper.getPluginClassLoader().loadClass(extensionClassName);
        SpringExtensionFactory extensionFactory = (SpringExtensionFactory) wrapper
            .getPluginManager().getExtensionFactory();
        Object bean = extensionFactory.create(extensionClass);
        String beanName = extensionFactory.getExtensionBeanName(extensionClass);
        pluginManager.registerBeanToMainContext(beanName, bean);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }
  }

  private void registerShareServices(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    ApplicationContext applicationContext = pf4bootPlugin.getApplicationContext();
    Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Export.class);
    for (String beanName : beans.keySet()) {
      Object bean = beans.get(beanName);
      pluginManager.registerBeanToMainContext(beanName, bean);
    }
  }



  @Override
  public void stoppedPlugin(Pf4bootPlugin pf4bootPlugin) {
    // unregister Extensions
    unregisterExtensions(pf4bootPlugin);
    //unregister ShareServices
    unregisterShareServices(pf4bootPlugin);
    //unregister PluginListeners
    unregisterEventListeners(pf4bootPlugin);
  }

  private void unregisterExtensions(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    PluginWrapper wrapper = pf4bootPlugin.getWrapper();
    Set<String> extensionClassNames = pluginManager
        .getExtensionClassNames(wrapper.getPluginId());
    for (String extensionClassName : extensionClassNames) {
      try {
        log.debug("Register extension <{}> to main ApplicationContext", extensionClassName);
        Class<?> extensionClass = wrapper.getPluginClassLoader().loadClass(extensionClassName);
        SpringExtensionFactory extensionFactory = (SpringExtensionFactory) wrapper
            .getPluginManager().getExtensionFactory();
        String beanName = extensionFactory.getExtensionBeanName(extensionClass);
        pluginManager.unregisterBeanFromMainContext(beanName);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }
  }

  private void unregisterShareServices(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    ApplicationContext applicationContext = pf4bootPlugin.getApplicationContext();
    Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Export.class);
    for (String beanName : beans.keySet()) {
      pluginManager.unregisterBeanFromMainContext(beanName);
    }
  }

  private void unregisterEventListeners(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    ApplicationContext applicationContext = pf4bootPlugin.getApplicationContext();
    Map<String, Object> beans = applicationContext.getBeansWithAnnotation(EventListener.class);
    for (String beanName : beans.keySet()) {
      Object bean = beans.get(beanName);
      pluginManager.getPf4bootEventBus().unregister(bean);
    }
  }

}
