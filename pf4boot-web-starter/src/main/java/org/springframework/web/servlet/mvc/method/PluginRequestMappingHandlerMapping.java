package org.springframework.web.servlet.mvc.method;

import com.google.common.base.Strings;
import net.xdob.pf4boot.Pf4bootPlugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * PluginRequestMappingHandlerMapping
 *
 * @author yangzj
 * @version 1.0
 */
public class PluginRequestMappingHandlerMapping extends RequestMappingHandlerMapping {
  static final Logger LOG = LoggerFactory.getLogger(PluginRequestMappingHandlerMapping.class);

  private final List<HandlerInterceptor> dynamicInterceptors = new CopyOnWriteArrayList<>();

  public void addDynamicInterceptor(HandlerInterceptor interceptor) {
    dynamicInterceptors.add(interceptor);
  }

  public void removeDynamicInterceptor(HandlerInterceptor interceptor) {
    dynamicInterceptors.remove(interceptor);
  }

  protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
    HandlerExecutionChain chain = super.getHandlerExecutionChain(handler, request);
    // 添加动态拦截器到执行链的最前面
    if (!dynamicInterceptors.isEmpty()) {
      for (HandlerInterceptor dynamicInterceptor : dynamicInterceptors) {
        chain.addInterceptor(0, dynamicInterceptor);
      }
    }
    return chain;
  }


  public void registerInterceptors(Pf4bootPlugin pf4BootPlugin) {
    getInterceptorBeans(pf4BootPlugin).forEach(this::registerInterceptor);
  }

  private void registerInterceptor(String beanName, final HandlerInterceptor interceptor) {
    unregisterInterceptor(beanName, interceptor);
    registerBeanToMainContext(beanName, interceptor);
    dynamicInterceptors.add(interceptor);
    logger.info("register interceptor=" + interceptor);
  }

  public Map<String, HandlerInterceptor> getInterceptorBeans(Pf4bootPlugin pf4BootPlugin) {
		ApplicationContext applicationContext = pf4BootPlugin.getPluginContext();
		return new HashMap<>(applicationContext.getBeansOfType(HandlerInterceptor.class));
  }

  public void unregisterInterceptors(Pf4bootPlugin pf4BootPlugin) {
    getInterceptorBeans(pf4BootPlugin).forEach(this::unregisterInterceptor);
  }

  private void unregisterInterceptor(String beanName, final HandlerInterceptor interceptor) {
    if(dynamicInterceptors.removeIf(e -> e == interceptor)) {
      unregisterBeanFromMainContext(beanName, interceptor);
      logger.info("unregister interceptor=" + interceptor);
    }
  }

  @Override
  protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
    super.registerHandlerMethod(handler, method, mapping);
    HandlerMethod handlerMethod = this.getHandlerMethods().get(mapping);
    this.logger.info("register mapping=" + mapping + ", handlerMethod=" + handlerMethod);
  }

  @Override
  public void unregisterMapping(RequestMappingInfo mapping) {
    super.unregisterMapping(mapping);
    this.logger.info("unregister mapping=" + mapping);
  }


  public void registerControllers(Pf4bootPlugin pf4BootPlugin) {
    getControllerBeans(pf4BootPlugin).forEach(this::registerController);
    this.handlerMethodsInitialized(getHandlerMethods());
  }

  private void registerController(String beanName, Object controller) {
    //this.logger.info("register controller=" + controller);
    // unregister RequestMapping if already registered
    unregisterController(beanName, controller);

    registerBeanToMainContext(beanName, controller);
    detectHandlerMethods(controller);
  }

  public void unregisterControllers(Pf4bootPlugin pf4BootPlugin) {
    //this.logger.info("unregister controller=" + controller);
    getControllerBeans(pf4BootPlugin).forEach(this::unregisterController);
		PluginWrapper pluginWrapper = pf4BootPlugin.getPluginManager().getPlugin(pf4BootPlugin.getPluginId());
		unregisterPluginControllers(pluginWrapper.getPluginClassLoader());
  }

	public void unregisterPluginControllers(ClassLoader cl) {
		getHandlerMethods().forEach((info, method) -> {
			if (method.getBeanType().getClassLoader() == cl) {
				unregisterMapping(info);
			}
		});
	}
  public Map<String, Object> getControllerBeans(Pf4bootPlugin pf4BootPlugin) {
    Map<String, Object> beans = new HashMap<>();
    ApplicationContext applicationContext = pf4BootPlugin.getPluginContext();
    beans.putAll(applicationContext.getBeansWithAnnotation(Controller.class));
    beans.putAll(applicationContext.getBeansWithAnnotation(RestController.class));
    return beans;
  }

  private void unregisterController(String beanName, final Object controller) {

    getHandlerMethods().forEach((mapping, handlerMethod) -> {
      if (controller == handlerMethod.getBean()) {
        this.unregisterMapping(mapping);
      }
    });
    unregisterBeanFromMainContext(beanName, controller);
  }

  public void registerBeanToMainContext(String beanName, Object bean) {
    Assert.notNull(bean, "bean must not be null");
    beanName = Strings.isNullOrEmpty(beanName) ? bean.getClass().getName() : beanName;
    AutowireCapableBeanFactory beanFactory = this.obtainApplicationContext().getAutowireCapableBeanFactory();
    ((AbstractAutowireCapableBeanFactory)beanFactory).registerSingleton(beanName, bean);
  }

//  public void unregisterBeanFromMainContext(String beanName) {
//    Assert.notNull(beanName, "bean must not be null");
//    AutowireCapableBeanFactory beanFactory = this.obtainApplicationContext().getAutowireCapableBeanFactory();
//    ((AbstractAutowireCapableBeanFactory)beanFactory).destroySingleton(beanName);
//  }

  public void unregisterBeanFromMainContext(String beanName, Object bean) {
    Assert.notNull(bean, "bean must not be null");
    AutowireCapableBeanFactory beanFactory = this.obtainApplicationContext().getAutowireCapableBeanFactory();
    ((AbstractAutowireCapableBeanFactory)beanFactory).destroySingleton(beanName);
  }
}
