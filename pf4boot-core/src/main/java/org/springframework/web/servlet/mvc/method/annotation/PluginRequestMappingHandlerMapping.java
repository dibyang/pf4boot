package org.springframework.web.servlet.mvc.method.annotation;

import net.xdob.pf4boot.Pf4bootPluginHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

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

  public void registerControllers(Pf4bootPluginHandler pf4BootPluginService) {
    getControllerBeans(pf4BootPluginService).forEach(bean -> registerController(pf4BootPluginService, bean));
  }

  private void registerController(Pf4bootPluginHandler pf4BootPluginService, Object controller) {
    String beanName = controller.getClass().getName();
    // unregister RequestMapping if already registered
    unregisterController(pf4BootPluginService, controller);
    pf4BootPluginService.registerBeanToMainContext(beanName, controller);
    detectHandlerMethods(controller);
  }

  public void unregisterControllers(Pf4bootPluginHandler pf4BootPluginService) {
    getControllerBeans(pf4BootPluginService).forEach(bean -> unregisterController(pf4BootPluginService, bean));
  }
  public Set<Object> getControllerBeans(Pf4bootPluginHandler pf4BootPluginService) {
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

  private void unregisterController(Pf4bootPluginHandler pf4BootPluginService, Object controller) {
    new HashMap<>(getHandlerMethods()).forEach((mapping, handlerMethod) -> {
      if (controller == handlerMethod.getBean()) super.unregisterMapping(mapping);
    });
    pf4BootPluginService.unregisterBeanFromMainContext(controller);
  }
}
