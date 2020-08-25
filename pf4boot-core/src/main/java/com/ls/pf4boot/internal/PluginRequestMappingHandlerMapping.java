package com.ls.pf4boot.internal;

import com.ls.pf4boot.Pf4bootPlugin;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
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

  public void registerControllers(Pf4bootPlugin pf4bootPlugin) {
    getControllerBeans(pf4bootPlugin).forEach(bean -> registerController(pf4bootPlugin, bean));
  }

  private void registerController(Pf4bootPlugin pf4bootPlugin, Object controller) {
    String beanName = controller.getClass().getName();
    // unregister RequestMapping if already registered
    unregisterController(pf4bootPlugin, controller);
    pf4bootPlugin.registerBeanToMainContext(beanName, controller);
    detectHandlerMethods(controller);
  }

  public void unregisterControllers(Pf4bootPlugin pf4bootPlugin) {
    getControllerBeans(pf4bootPlugin).forEach(bean -> unregisterController(pf4bootPlugin, bean));
  }
  public Set<Object> getControllerBeans(Pf4bootPlugin pf4bootPlugin) {
    LinkedHashSet<Object> beans = new LinkedHashSet<>();
    ApplicationContext applicationContext = pf4bootPlugin.getApplicationContext();
    //noinspection unchecked

    beans.addAll(applicationContext.getBeansWithAnnotation(Controller.class)
        .entrySet().stream()
        .map(Map.Entry::getValue).collect(Collectors.toList()));
    beans.addAll(applicationContext.getBeansWithAnnotation(RestController.class)
        .entrySet().stream()
        .map(Map.Entry::getValue).collect(Collectors.toList()));
    return beans;
  }

  private void unregisterController(Pf4bootPlugin pf4bootPlugin, Object controller) {
    new HashMap<>(getHandlerMethods()).forEach((mapping, handlerMethod) -> {
      if (controller == handlerMethod.getBean()) super.unregisterMapping(mapping);
    });
    pf4bootPlugin.unregisterBeanFromMainContext(controller);
  }
}
