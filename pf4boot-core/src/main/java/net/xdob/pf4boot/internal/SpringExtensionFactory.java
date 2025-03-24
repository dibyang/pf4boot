package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.TypeWrapper;
import org.pf4j.ExtensionFactory;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * SpringExtensionFactory
 *
 * @author yangzj
 * @version 1.0
 */
public class SpringExtensionFactory implements ExtensionFactory {

  private static final Logger log = LoggerFactory.getLogger(SpringExtensionFactory.class);

  private final Pf4bootPluginManager pluginManager;

  public SpringExtensionFactory(Pf4bootPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @Override
  public <T> T create(Class<T> extensionClass) {
    ConfigurableApplicationContext pluginApplicationContext = getApplicationContext(extensionClass);
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

  private ConfigurableApplicationContext getApplicationContext(Class<?> extensionClass) {
    PluginWrapper pluginWrapper = pluginManager.whichPlugin(extensionClass);
    return TypeWrapper.wrapper(pluginWrapper.getPlugin(), Pf4bootPlugin.class)
        .map(Pf4bootPlugin::getApplicationContext)
        .orElse(null);
  }
}
