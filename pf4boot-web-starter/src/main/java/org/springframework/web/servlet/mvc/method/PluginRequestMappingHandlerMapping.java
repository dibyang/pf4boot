package org.springframework.web.servlet.mvc.method;

import com.google.common.base.Strings;
import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.deployment.DeploymentCheckResult;
import net.xdob.pf4boot.deployment.PluginCleanupVerifier;
import net.xdob.pf4boot.deployment.PluginHealthContext;
import net.xdob.pf4boot.deployment.PluginHealthVerifier;
import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PluginRequestMappingHandlerMapping
 *
 * @author yangzj
 * @version 1.0
 */
public class PluginRequestMappingHandlerMapping extends RequestMappingHandlerMapping
    implements PluginTrafficDrainer, PluginCleanupVerifier, PluginHealthVerifier {
  static final Logger LOG = LoggerFactory.getLogger(PluginRequestMappingHandlerMapping.class);
  private static final String IN_FLIGHT_PLUGIN_ATTRIBUTE =
      PluginRequestMappingHandlerMapping.class.getName() + ".pluginId";

  private final List<HandlerInterceptor> dynamicInterceptors = new CopyOnWriteArrayList<>();
  private final Map<Object, String> pluginIdsByHandler =
      Collections.synchronizedMap(new IdentityHashMap<>());
  private final Map<HandlerInterceptor, String> pluginIdsByInterceptor =
      Collections.synchronizedMap(new IdentityHashMap<>());
  private final Set<String> drainingPluginIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Map<String, AtomicInteger> inFlightRequests = new ConcurrentHashMap<>();

  public void addDynamicInterceptor(HandlerInterceptor interceptor) {
    dynamicInterceptors.add(interceptor);
  }

  public void removeDynamicInterceptor(HandlerInterceptor interceptor) {
    dynamicInterceptors.remove(interceptor);
  }

  public int getDynamicInterceptorCount() {
    return dynamicInterceptors.size();
  }

  protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
    HandlerExecutionChain chain = super.getHandlerExecutionChain(handler, request);
    if (chain == null) {
      return null;
    }
    String pluginId = pluginIdForHandler(handler);
    // 添加动态拦截器到执行链的最前面
    if (!dynamicInterceptors.isEmpty()) {
      for (HandlerInterceptor dynamicInterceptor : dynamicInterceptors) {
        chain.addInterceptor(0, dynamicInterceptor);
      }
    }
    if (pluginId != null) {
      chain.addInterceptor(0, new PluginDrainInterceptor(pluginId));
    }
    return chain;
  }


  public void registerInterceptors(Pf4bootPlugin pf4BootPlugin) {
    getInterceptorBeans(pf4BootPlugin)
        .forEach((beanName, interceptor) ->
            registerInterceptor(pf4BootPlugin.getPluginId(), beanName, interceptor));
  }

  private void registerInterceptor(String pluginId, String beanName, final HandlerInterceptor interceptor) {
    unregisterInterceptor(beanName, interceptor);
    registerBeanToMainContext(beanName, interceptor);
    dynamicInterceptors.add(interceptor);
    pluginIdsByInterceptor.put(interceptor, pluginId);
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
      pluginIdsByInterceptor.remove(interceptor);
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
    getControllerBeans(pf4BootPlugin)
        .forEach((beanName, controller) ->
            registerController(pf4BootPlugin.getPluginId(), beanName, controller));
    this.handlerMethodsInitialized(getHandlerMethods());
  }

  private void registerController(String pluginId, String beanName, Object controller) {
    //this.logger.info("register controller=" + controller);
    // unregister RequestMapping if already registered
    unregisterController(beanName, controller);

    registerBeanToMainContext(beanName, controller);
    pluginIdsByHandler.put(controller, pluginId);
    detectHandlerMethods(controller);
  }

  public void unregisterControllers(Pf4bootPlugin pf4BootPlugin) {
    //this.logger.info("unregister controller=" + controller);
    getControllerBeans(pf4BootPlugin).forEach(this::unregisterController);
		PluginWrapper pluginWrapper = pf4BootPlugin.getPluginManager().getPlugin(pf4BootPlugin.getPluginId());
		unregisterPluginControllers(pluginWrapper.getPluginClassLoader());
  }

	public void unregisterPluginControllers(ClassLoader cl) {
		List<RequestMappingInfo> mappings = new ArrayList<>();
		getHandlerMethods().forEach((info, method) -> {
			if (method.getBeanType().getClassLoader() == cl) {
				mappings.add(info);
			}
		});
		mappings.forEach(this::unregisterMapping);
	}
  public Map<String, Object> getControllerBeans(Pf4bootPlugin pf4BootPlugin) {
    Map<String, Object> beans = new HashMap<>();
    ApplicationContext applicationContext = pf4BootPlugin.getPluginContext();
    beans.putAll(applicationContext.getBeansWithAnnotation(Controller.class));
    beans.putAll(applicationContext.getBeansWithAnnotation(RestController.class));
    return beans;
  }

  private void unregisterController(String beanName, final Object controller) {

    List<RequestMappingInfo> mappings = new ArrayList<>();
    getHandlerMethods().forEach((mapping, handlerMethod) -> {
      if (controller == handlerMethod.getBean()) {
        mappings.add(mapping);
      }
    });
    mappings.forEach(this::unregisterMapping);
    pluginIdsByHandler.remove(controller);
    unregisterBeanFromMainContext(beanName, controller);
  }

  public void registerBeanToMainContext(String beanName, Object bean) {
    Assert.notNull(bean, "bean must not be null");
    beanName = Strings.isNullOrEmpty(beanName) ? bean.getClass().getName() : beanName;
    AutowireCapableBeanFactory beanFactory = this.obtainApplicationContext().getAutowireCapableBeanFactory();
    DefaultListableBeanFactory listableBeanFactory = (DefaultListableBeanFactory) beanFactory;
    if (listableBeanFactory.containsSingleton(beanName) || listableBeanFactory.containsBeanDefinition(beanName)) {
      Object existing = null;
      if (this.obtainApplicationContext().containsBean(beanName)) {
        try {
          existing = this.obtainApplicationContext().getBean(beanName);
        } catch (Exception ignored) {
        }
      }
      if (existing != bean) {
        throw new IllegalStateException(String.format(
            "Dynamic MVC bean name conflict: beanName [%s] already exists in application context",
            beanName));
      }
    }
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
    if (this.obtainApplicationContext().containsBean(beanName)
        && this.obtainApplicationContext().getBean(beanName) == bean) {
      ((AbstractAutowireCapableBeanFactory)beanFactory).destroySingleton(beanName);
    }
  }

  @Override
  public void beginDrain(Collection<String> pluginIds) {
    if (pluginIds == null) {
      return;
    }
    drainingPluginIds.addAll(pluginIds);
  }

  @Override
  public boolean awaitDrain(Collection<String> pluginIds, long timeoutMillis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + Math.max(timeoutMillis, 0);
    while (true) {
      if (inFlightRequestCount(pluginIds) == 0) {
        return true;
      }
      if (System.currentTimeMillis() >= deadline) {
        return false;
      }
      TimeUnit.MILLISECONDS.sleep(20);
    }
  }

  @Override
  public void endDrain(Collection<String> pluginIds) {
    if (pluginIds == null) {
      return;
    }
    drainingPluginIds.removeAll(pluginIds);
  }

  @Override
  public List<DeploymentCheckResult> verifyStoppedPlugin(String pluginId, ClassLoader pluginClassLoader) {
    List<DeploymentCheckResult> results = new ArrayList<>();
    int handlerCount = getRegisteredHandlerCount(pluginId);
    int interceptorCount = getRegisteredInterceptorCount(pluginId);
    int inFlightCount = getInFlightRequestCount(pluginId);
    if (handlerCount > 0) {
      results.add(DeploymentCheckResult.error(
          "WEB_MAPPING_NOT_CLEANED",
          "Plugin web mappings remain after stop: " + handlerCount));
    }
    if (interceptorCount > 0) {
      results.add(DeploymentCheckResult.error(
          "WEB_INTERCEPTOR_NOT_CLEANED",
          "Plugin web interceptors remain after stop: " + interceptorCount));
    }
    if (inFlightCount > 0) {
      results.add(DeploymentCheckResult.error(
          "WEB_IN_FLIGHT_NOT_DRAINED",
          "Plugin in-flight requests remain after stop: " + inFlightCount));
    }
    if (results.isEmpty()) {
      results.add(DeploymentCheckResult.info("WEB_CLEANUP_VERIFIED", "Plugin web resources are cleaned"));
    }
    return results;
  }

  @Override
  public List<DeploymentCheckResult> verifyStartedPlugin(
      PluginHealthContext context,
      ClassLoader pluginClassLoader) {
    List<DeploymentCheckResult> results = new ArrayList<>();
    results.add(DeploymentCheckResult.info(
        "WEB_MAPPING_HEALTH",
        "Plugin web mapping count: " + getRegisteredHandlerCount(context.getPluginId())));
    results.add(DeploymentCheckResult.info(
        "WEB_INTERCEPTOR_HEALTH",
        "Plugin web interceptor count: " + getRegisteredInterceptorCount(context.getPluginId())));
    return results;
  }

  public boolean isDraining(String pluginId) {
    return drainingPluginIds.contains(pluginId);
  }

  public int getInFlightRequestCount(String pluginId) {
    AtomicInteger count = inFlightRequests.get(pluginId);
    return count == null ? 0 : count.get();
  }

  public int getRegisteredHandlerCount(String pluginId) {
    int count = 0;
    synchronized (pluginIdsByHandler) {
      for (String registeredPluginId : pluginIdsByHandler.values()) {
        if (Objects.equals(pluginId, registeredPluginId)) {
          count++;
        }
      }
    }
    return count;
  }

  public int getRegisteredInterceptorCount(String pluginId) {
    int count = 0;
    synchronized (pluginIdsByInterceptor) {
      for (String registeredPluginId : pluginIdsByInterceptor.values()) {
        if (Objects.equals(pluginId, registeredPluginId)) {
          count++;
        }
      }
    }
    return count;
  }

  private int inFlightRequestCount(Collection<String> pluginIds) {
    int count = 0;
    for (String pluginId : pluginIds) {
      count += getInFlightRequestCount(pluginId);
    }
    return count;
  }

  private String pluginIdForHandler(Object handler) {
    if (handler instanceof HandlerMethod) {
      Object bean = ((HandlerMethod) handler).getBean();
      return pluginIdsByHandler.get(bean);
    }
    return pluginIdsByHandler.get(handler);
  }

  private class PluginDrainInterceptor implements HandlerInterceptor {

    private final String pluginId;

    private PluginDrainInterceptor(String pluginId) {
      this.pluginId = pluginId;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler) throws Exception {
      if (drainingPluginIds.contains(pluginId)) {
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Plugin is draining: " + pluginId);
        return false;
      }
      inFlightRequests.computeIfAbsent(pluginId, key -> new AtomicInteger()).incrementAndGet();
      request.setAttribute(IN_FLIGHT_PLUGIN_ATTRIBUTE, pluginId);
      return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex) {
      Object value = request.getAttribute(IN_FLIGHT_PLUGIN_ATTRIBUTE);
      if (Objects.equals(pluginId, value)) {
        AtomicInteger count = inFlightRequests.get(pluginId);
        if (count != null && count.decrementAndGet() < 0) {
          count.set(0);
        }
      }
    }
  }
}
