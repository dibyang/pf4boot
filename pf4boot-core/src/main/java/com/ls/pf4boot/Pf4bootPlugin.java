package com.ls.pf4boot;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.ls.pf4boot.autoconfigure.Export;
import com.ls.pf4boot.autoconfigure.PluginStarter;
import com.ls.pf4boot.autoconfigure.SpringBootPlugin;
import com.ls.pf4boot.internal.PluginRequestMappingHandlerMapping;
import com.ls.pf4boot.internal.SpringExtensionFactory;
import com.ls.pf4boot.spring.boot.Pf4bootApplication;
import com.ls.pf4boot.spring.boot.Pf4bootPluginRestartedEvent;
import com.ls.pf4boot.spring.boot.Pf4bootPluginStartedEvent;
import com.ls.pf4boot.spring.boot.Pf4bootPluginStoppedEvent;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.boot.Banner;
import org.springframework.cglib.core.NamingPolicy;
import org.springframework.cglib.core.Predicate;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Base Pf4j Plugin for Spring Boot.
 * <p>
 * ----
 * <p>
 * ### Following actions will be taken after plugin is started:
 * * Use {@link Pf4bootApplication} to initialize Spring environment
 * in spring-boot style. Some AutoConfiguration need to be excluded explicitly
 * to make sure plugin resource could be inject to main {@link ApplicationContext}
 * * Share beans from main {@link ApplicationContext} to
 * plugin {@link ApplicationContext} in order to share resources.
 * This is done by {@link Pf4bootApplication}
 * * Register {@link Controller} and @{@link RestController} beans to
 * RequestMapping of main {@link ApplicationContext}, so Spring will forward
 * request to plugin controllers correctly.
 * * Register {@link Extension} to main ApplicationContext
 * <p>
 * ----
 * <p>
 * ### And following actions will be taken when plugin is stopped:
 * * Unregister {@link Extension} in main {@link ApplicationContext}
 * * Unregister controller beans from main RequestMapping
 * * Close plugin {@link ApplicationContext}
 *
 * @see Pf4bootApplication
 */
public abstract class Pf4bootPlugin extends Plugin {

  private final Pf4bootApplication pf4bootApplication;

  private ApplicationContext applicationContext;

  public Pf4bootPlugin(PluginWrapper wrapper) {
    super(wrapper);
    PluginStarter pluginStarter = this.getClass().getAnnotation(PluginStarter.class);
    Preconditions.checkState(pluginStarter!=null,"PluginStarter annotation is missing.");
    Class<?>[] starterClasses = pluginStarter.value();
    pf4bootApplication = new Pf4bootApplication(this, starterClasses);
  }

  private PluginRequestMappingHandlerMapping getMainRequestMapping() {
    return (PluginRequestMappingHandlerMapping)
        getMainApplicationContext().getBean("requestMappingHandlerMapping");
  }

  /**
   * Release plugin holding release on stop.
   */
  public void releaseResource() {
  }

  @Override
  public void start() {
    if (getWrapper().getPluginState() == PluginState.STARTED) return;

    long startTs = System.currentTimeMillis();
    log.debug("Starting plugin {} ......", getWrapper().getPluginId());
    pf4bootApplication.setBannerMode(Banner.Mode.OFF);
    applicationContext = pf4bootApplication.run();
    getMainRequestMapping().registerControllers(this);

    //unregister ShareServices
    registerShareServices();

    // register Extensions
    Set<String> extensionClassNames = getWrapper().getPluginManager()
        .getExtensionClassNames(getWrapper().getPluginId());
    for (String extensionClassName : extensionClassNames) {
      try {
        log.debug("Register extension <{}> to main ApplicationContext", extensionClassName);
        Class<?> extensionClass = getWrapper().getPluginClassLoader().loadClass(extensionClassName);
        SpringExtensionFactory extensionFactory = (SpringExtensionFactory) getWrapper()
            .getPluginManager().getExtensionFactory();
        Object bean = extensionFactory.create(extensionClass);
        String beanName = extensionFactory.getExtensionBeanName(extensionClass);
        registerBeanToMainContext(beanName, bean);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }

    ApplicationContextProvider.registerApplicationContext(applicationContext);
    applicationContext.publishEvent(new Pf4bootPluginStartedEvent(applicationContext));
    if (getPluginManager().isMainApplicationStarted()) {
      // if main application context is not ready, don't send restart event
      applicationContext.publishEvent(new Pf4bootPluginRestartedEvent(applicationContext));
    }

    log.debug("Plugin {} is started in {}ms", getWrapper().getPluginId(), System.currentTimeMillis() - startTs);
  }

  private void registerShareServices() {
    Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Export.class);
    for (String beanName : beans.keySet()) {
      Object bean = beans.get(beanName);
      registerBeanToMainContext(beanName, bean);
    }
  }

  @Override
  public void stop() {
    if (getWrapper().getPluginState() != PluginState.STARTED) return;

    log.debug("Stopping plugin {} ......", getWrapper().getPluginId());
    releaseResource();
    //unregister ShareServices
    unregisterShareServices();
    // unregister Extensions
    Set<String> extensionClassNames = getWrapper().getPluginManager()
        .getExtensionClassNames(getWrapper().getPluginId());
    for (String extensionClassName : extensionClassNames) {
      try {
        log.debug("Register extension <{}> to main ApplicationContext", extensionClassName);
        Class<?> extensionClass = getWrapper().getPluginClassLoader().loadClass(extensionClassName);
        SpringExtensionFactory extensionFactory = (SpringExtensionFactory) getWrapper()
            .getPluginManager().getExtensionFactory();
        String beanName = extensionFactory.getExtensionBeanName(extensionClass);
        unregisterBeanFromMainContext(beanName);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }

    getMainRequestMapping().unregisterControllers(this);
    applicationContext.publishEvent(new Pf4bootPluginStoppedEvent(applicationContext));
    ApplicationContextProvider.unregisterApplicationContext(applicationContext);
    ((ConfigurableApplicationContext) applicationContext).close();

    log.debug("Plugin {} is stopped", getWrapper().getPluginId());
  }

  private void unregisterShareServices() {
    Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Export.class);
    for (String beanName : beans.keySet()) {
      unregisterBeanFromMainContext(beanName);
    }
  }

  public GenericApplicationContext getApplicationContext() {
    return (GenericApplicationContext) applicationContext;
  }

  public Pf4bootPluginManager getPluginManager() {
    return (Pf4bootPluginManager) getWrapper().getPluginManager();
  }

  public GenericApplicationContext getMainApplicationContext() {
    return (GenericApplicationContext) getPluginManager().getMainApplicationContext();
  }

  public void registerBeanToMainContext(String beanName, Object bean) {
    Assert.notNull(bean, "bean must not be null");
    beanName = Strings.isNullOrEmpty(beanName) ? bean.getClass().getName() : beanName;
    getMainApplicationContext().getBeanFactory().registerSingleton(beanName, bean);
  }

  public void unregisterBeanFromMainContext(String beanName) {
    Assert.notNull(beanName, "bean must not be null");
    ((AbstractAutowireCapableBeanFactory) getMainApplicationContext().getBeanFactory())
        .destroySingleton(beanName);
  }

  public void unregisterBeanFromMainContext(Object bean) {
    Assert.notNull(bean, "bean must not be null");
    String beanName = bean.getClass().getName();
    ((AbstractAutowireCapableBeanFactory) getMainApplicationContext().getBeanFactory())
        .destroySingleton(beanName);
  }

}
