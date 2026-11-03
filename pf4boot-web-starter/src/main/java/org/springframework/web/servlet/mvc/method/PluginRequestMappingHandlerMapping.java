package org.springframework.web.servlet.mvc.method;

import com.google.common.base.Strings;
import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.deployment.DeploymentCheckResult;
import net.xdob.pf4boot.deployment.PluginCleanupVerifier;
import net.xdob.pf4boot.deployment.PluginHealthContext;
import net.xdob.pf4boot.deployment.PluginHealthVerifier;
import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import net.xdob.pf4boot.deployment.PluginWorkTracker;
import net.xdob.pf4boot.util.PluginCleanup;
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
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncUtils;
import java.util.concurrent.Callable;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

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
  private final PluginWorkTracker workTracker = new PluginWorkTracker();
  private final Map<Object, String> registeredBeanNames =
      Collections.synchronizedMap(new IdentityHashMap<>());

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
    Map<HandlerInterceptor, String> owned = new IdentityHashMap<>();
    String path = getUrlPathHelper().getPathWithinApplication(request);
    String admin = obtainApplicationContext().getEnvironment()
        .getProperty("spring.pf4boot.management.http.base-path", "/pf4boot/admin");
    while (admin.length() > 1 && admin.endsWith("/")) { admin = admin.substring(0, admin.length() - 1); }
    boolean controlPlane = path.equals(admin) || path.startsWith(admin + "/");
    // 添加动态拦截器到执行链的最前面
    synchronized (pluginIdsByInterceptor) {
      for (HandlerInterceptor dynamicInterceptor : dynamicInterceptors) {
        String owner = pluginIdsByInterceptor.get(dynamicInterceptor);
        // 管理入口不能参与待停止插件自己的请求链，否则同步 stop 必然等待自身。
        if (controlPlane && owner != null) { continue; }
        if (dynamicInterceptor instanceof MappedInterceptor
            && !((MappedInterceptor) dynamicInterceptor).matches(request)) { continue; }
        chain.addInterceptor(0, dynamicInterceptor);
        if (owner != null) { owned.put(dynamicInterceptor, owner); }
      }
    }
    if (pluginId != null || !owned.isEmpty()) {
      Object controller = handler instanceof HandlerMethod ? ((HandlerMethod) handler).getBean() : handler;
      chain.addInterceptor(0, new PluginDrainInterceptor(pluginId, controller, owned));
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
    synchronized (pluginIdsByInterceptor) {
    dynamicInterceptors.add(interceptor);
    pluginIdsByInterceptor.put(interceptor, pluginId);
    registeredBeanNames.put(interceptor, beanName);
    logger.info("register interceptor=" + interceptor);
    }
  }

  public Map<String, HandlerInterceptor> getInterceptorBeans(Pf4bootPlugin pf4BootPlugin) {
		ApplicationContext applicationContext = pf4BootPlugin.getPluginContext();
		return new HashMap<>(applicationContext.getBeansOfType(HandlerInterceptor.class));
  }

  public void unregisterInterceptors(Pf4bootPlugin pf4BootPlugin) {
    PluginCleanup cleanup = new PluginCleanup();
    Map<HandlerInterceptor, String> owned;
    synchronized (pluginIdsByInterceptor) {
      owned = new IdentityHashMap<>(pluginIdsByInterceptor);
    }
    owned.forEach((interceptor, id) -> {
      if (pf4BootPlugin.getPluginId().equals(id)) {
        cleanup.run("interceptor", () -> unregisterInterceptor(registeredBeanNames.get(interceptor), interceptor));
      }
    });
    cleanup.throwIfFailed();
  }

  private void unregisterInterceptor(String beanName, final HandlerInterceptor interceptor) {
    if(dynamicInterceptors.contains(interceptor) || pluginIdsByInterceptor.containsKey(interceptor)) {
      unregisterBeanFromMainContext(beanName, interceptor);
      synchronized (pluginIdsByInterceptor) {
      dynamicInterceptors.removeIf(e -> e == interceptor);
      pluginIdsByInterceptor.remove(interceptor);
      registeredBeanNames.remove(interceptor);
      logger.info("unregister interceptor=" + interceptor);
    }
    }
  }

  @Override
  protected HandlerMethod createHandlerMethod(Object handler, Method method) {
    HandlerMethod original = super.createHandlerMethod(handler, method);
    String owner = pluginIdsByHandler.get(handler);
    return owner == null ? original : new OwnedHandlerMethod(original, owner);
  }

  /** 匹配后即使路由被注销，仍保留所有者用于准入时拒绝旧 handler。 */
  private static final class OwnedHandlerMethod extends HandlerMethod {
    private final String pluginId;
    private OwnedHandlerMethod(HandlerMethod original, String pluginId) {
      super(original);
      this.pluginId = pluginId;
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
    registeredBeanNames.put(controller, beanName);
    detectHandlerMethods(controller);
  }

  public void unregisterControllers(Pf4bootPlugin pf4BootPlugin) {
    PluginCleanup cleanup = new PluginCleanup();
    Map<Object, String> owned;
    synchronized (pluginIdsByHandler) {
      owned = new IdentityHashMap<>(pluginIdsByHandler);
    }
    owned.forEach((controller, id) -> {
      if (pf4BootPlugin.getPluginId().equals(id)) {
        cleanup.run("controller", () -> unregisterController(registeredBeanNames.get(controller), controller));
      }
    });
    cleanup.throwIfFailed();
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
    unregisterBeanFromMainContext(beanName, controller);
    pluginIdsByHandler.remove(controller);
    registeredBeanNames.remove(controller);
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
    DefaultListableBeanFactory factory = (DefaultListableBeanFactory) beanFactory;
    if (factory.containsSingleton(beanName) && factory.getSingleton(beanName) == bean) {
      ((AbstractAutowireCapableBeanFactory)beanFactory).destroySingleton(beanName);
    }
  }

  @Override
  public void beginDrain(Collection<String> pluginIds) {
    if (pluginIds == null) {
      return;
    }
    workTracker.beginDrain(pluginIds);
  }

  @Override
  public boolean awaitDrain(Collection<String> pluginIds, long timeoutMillis) throws InterruptedException {
    return workTracker.awaitDrain(pluginIds, timeoutMillis);
  }

  @Override
  public void endDrain(Collection<String> pluginIds) {
    if (pluginIds == null) {
      return;
    }
    workTracker.endDrain(pluginIds);
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
    return workTracker.isDraining(pluginId);
  }

  public int getInFlightRequestCount(String pluginId) {
    return workTracker.getRunningCount(pluginId);
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
    if (handler instanceof OwnedHandlerMethod) { return ((OwnedHandlerMethod) handler).pluginId; }
    if (handler instanceof HandlerMethod) {
      Object bean = ((HandlerMethod) handler).getBean();
      return pluginIdsByHandler.get(bean);
    }
    return pluginIdsByHandler.get(handler);
  }

  private class PluginDrainInterceptor implements AsyncHandlerInterceptor {

    private final String pluginId;
    private final Object controller;
    private final Map<HandlerInterceptor, String> owned;

    private PluginDrainInterceptor(String pluginId, Object controller, Map<HandlerInterceptor, String> owned) {
      this.pluginId = pluginId;
      this.controller = controller;
      this.owned = owned;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler) throws Exception {
      Object existing = request.getAttribute(IN_FLIGHT_PLUGIN_ATTRIBUTE);
      if (existing instanceof RequestWork && !((RequestWork) existing).released) { return true; }
      Set<String> ids = new LinkedHashSet<>(owned.values());
      if (pluginId != null) { ids.add(pluginId); }
      List<String> entered = new ArrayList<>();
      synchronized (workTracker) {
        boolean live = pluginId == null || pluginId.equals(pluginIdsByHandler.get(controller));
        for (Map.Entry<HandlerInterceptor, String> entry : owned.entrySet()) {
          live &= entry.getValue().equals(pluginIdsByInterceptor.get(entry.getKey()));
        }
        if (live) {
          for (String id : ids) {
            if (!workTracker.tryEnter(id)) { live = false; break; }
            entered.add(id);
          }
        }
        if (!live) {
          entered.forEach(workTracker::exit);
          response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Plugin work is unavailable");
          return false;
        }
      }
      RequestWork work = new RequestWork(entered);
      request.setAttribute(IN_FLIGHT_PLUGIN_ATTRIBUTE, work);
      WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(IN_FLIGHT_PLUGIN_ATTRIBUTE,
          new CallableProcessingInterceptor() {
            public <T> void preProcess(NativeWebRequest req, Callable<T> task) { work.beginWorker(); }
            public <T> void postProcess(NativeWebRequest req, Callable<T> task, Object result) { work.endWorker(); }
            public <T> void afterCompletion(NativeWebRequest req, Callable<T> task) { work.complete(); }
          });
      WebAsyncUtils.getAsyncManager(request).registerDeferredResultInterceptor(IN_FLIGHT_PLUGIN_ATTRIBUTE,
          new DeferredResultProcessingInterceptor() {
            public <T> void afterCompletion(NativeWebRequest req, DeferredResult<T> result) { work.complete(); }
          });
      return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
      RequestWork work = (RequestWork) request.getAttribute(IN_FLIGHT_PLUGIN_ATTRIBUTE);
      request.getAsyncContext().addListener(new AsyncListener() {
        public void onComplete(AsyncEvent event) { work.complete(); }
        public void onTimeout(AsyncEvent event) { }
        public void onError(AsyncEvent event) { }
        public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
      });
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex) {
      Object value = request.getAttribute(IN_FLIGHT_PLUGIN_ATTRIBUTE);
      if (value instanceof RequestWork) { ((RequestWork) value).complete(); }
    }
  }

  /** 请求终结且 Callable 工作线程已退出后，才释放计数。 */
  private class RequestWork {
    private final List<String> ids;
    private boolean completed;
    private int workers;
    private volatile boolean released;
    private RequestWork(List<String> ids) { this.ids = ids; }
    private synchronized void beginWorker() {
      if (completed) { throw new IllegalStateException("Request already completed"); }
      workers++;
    }
    private void endWorker() {
      synchronized (this) { if (workers > 0) { workers--; } }
      releaseIfReady();
    }
    private void complete() {
      synchronized (this) { completed = true; }
      releaseIfReady();
    }
    private void releaseIfReady() {
      synchronized (this) {
        if (!completed || workers != 0 || released) { return; }
        released = true;
      }
      ids.forEach(workTracker::exit);
    }
  }
}
