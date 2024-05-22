package org.springframework.web.servlet.mvc.method;

import com.google.common.base.Strings;
import net.xdob.pf4boot.Pf4bootPlugin;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PluginRequestMappingHandlerMapping
 *
 * @author yangzj
 * @version 1.0
 */
public class PluginRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

  /**
   * {@inheritDoc}
   */
  @Override
  public void detectHandlerMethods(Object controller) {
    super.detectHandlerMethods(controller);
  }

  public void registerControllers(Pf4bootPlugin pf4BootPluginService) {
    getControllerBeans(pf4BootPluginService).forEach(bean -> registerController(pf4BootPluginService, bean));
  }

  private void registerController(Pf4bootPlugin pf4BootPluginService, Object controller) {
    String beanName = controller.getClass().getName();
    // unregister RequestMapping if already registered
    unregisterController(pf4BootPluginService, controller);

    registerBeanToMainContext(beanName, controller);
    detectHandlerMethods(controller);
  }

  public void unregisterControllers(Pf4bootPlugin pf4BootPluginService) {
    getControllerBeans(pf4BootPluginService).forEach(bean -> unregisterController(pf4BootPluginService, bean));
  }
  public Set<Object> getControllerBeans(Pf4bootPlugin pf4BootPluginService) {
    LinkedHashSet<Object> beans = new LinkedHashSet<>();
    ApplicationContext applicationContext = pf4BootPluginService.getApplicationContext();
    //noinspection unchecked

    beans.addAll(applicationContext.getBeansWithAnnotation(Controller.class)
        .entrySet().stream()
        .map(Map.Entry::getValue).collect(Collectors.toList()));
    beans.addAll(applicationContext.getBeansWithAnnotation(RestController.class)
        .entrySet().stream()
        .map(Map.Entry::getValue).collect(Collectors.toList()));
    return beans;
  }

  private void unregisterController(Pf4bootPlugin pf4BootPluginService, Object controller) {
    new HashMap<>(getHandlerMethods()).forEach((mapping, handlerMethod) -> {
      if (controller == handlerMethod.getBean()) super.unregisterMapping(mapping);
    });
    unregisterBeanFromMainContext(controller);
  }

  public void registerBeanToMainContext(String beanName, Object bean) {
    Assert.notNull(bean, "bean must not be null");
    beanName = Strings.isNullOrEmpty(beanName) ? bean.getClass().getName() : beanName;
    AutowireCapableBeanFactory beanFactory = this.obtainApplicationContext().getAutowireCapableBeanFactory();
    ((AbstractAutowireCapableBeanFactory)beanFactory).registerSingleton(beanName, bean);
  }

  public void unregisterBeanFromMainContext(String beanName) {
    Assert.notNull(beanName, "bean must not be null");
    AutowireCapableBeanFactory beanFactory = this.obtainApplicationContext().getAutowireCapableBeanFactory();
    ((AbstractAutowireCapableBeanFactory)beanFactory).destroySingleton(beanName);
  }

  public void unregisterBeanFromMainContext(Object bean) {
    Assert.notNull(bean, "bean must not be null");
    String beanName = bean.getClass().getName();
    AutowireCapableBeanFactory beanFactory = this.obtainApplicationContext().getAutowireCapableBeanFactory();
    ((AbstractAutowireCapableBeanFactory)beanFactory).destroySingleton(beanName);
  }
}
