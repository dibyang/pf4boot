package com.ls.pf4boot.internal;

import com.ls.pf4boot.Pf4bootPluginManager;
import com.ls.pf4boot.Pf4bootPluginService;
import org.pf4j.ExtensionFactory;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.support.GenericApplicationContext;

/**
 * SpringExtensionFactory
 *
 * @author yangzj
 * @version 1.0
 */
public class SpringExtensionFactory implements ExtensionFactory {

  private static final Logger log = LoggerFactory.getLogger(SpringExtensionFactory.class);

  private Pf4bootPluginManager pluginManager;

  public SpringExtensionFactory(Pf4bootPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @Override
  public <T> T create(Class<T> extensionClass) {
    GenericApplicationContext pluginApplicationContext = getApplicationContext(extensionClass);
    Object extension = null;
    try {
      extension = pluginApplicationContext.getBean(extensionClass);
    } catch (NoSuchBeanDefinitionException ignored) {
    } // do nothing
    if (extension == null) {
      Object extensionBean = createWithoutSpring(extensionClass);
      pluginApplicationContext.getBeanFactory().registerSingleton(
          extensionClass.getName(), extensionBean);
      extension = extensionBean;
    }
    //noinspection unchecked
    return (T) extension;
  }

  public String getExtensionBeanName(Class<?> extensionClass) {
    String[] beanNames = getApplicationContext(extensionClass)
        .getBeanNamesForType(extensionClass);
    return beanNames.length > 0 ? beanNames[0] : null;
  }

  private Object createWithoutSpring(Class<?> extensionClass) {
    try {
      return extensionClass.newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  private GenericApplicationContext getApplicationContext(Class<?> extensionClass) {
    PluginWrapper pluginWrapper = pluginManager.whichPlugin(extensionClass);
    Pf4bootPluginService plugin = (Pf4bootPluginService) pluginWrapper.getPlugin();
    return plugin.getApplicationContext();
  }
}
