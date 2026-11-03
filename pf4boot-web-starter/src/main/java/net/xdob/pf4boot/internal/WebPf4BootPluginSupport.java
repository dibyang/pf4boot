package net.xdob.pf4boot.internal;


import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.Pf4bootPluginSupport;
import net.xdob.pf4boot.util.PluginCleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.servlet.mvc.method.PluginRequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.lang.reflect.Field;
import java.util.Map;


public class WebPf4BootPluginSupport implements Pf4bootPluginSupport {
	static final Logger LOG = LoggerFactory.getLogger(WebPf4BootPluginSupport.class);
  public static final String REQUEST_MAPPING_HANDLER_MAPPING = "requestMappingHandlerMapping";


  @Override
  public void initiatedPlugin(Pf4bootPlugin pf4bootPlugin) {

  }

  @Override
  public void startPlugin(Pf4bootPlugin pf4bootPlugin) {

  }

  @Override
  public void startedPlugin(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    //register Interceptor
    getMainRequestMapping(pluginManager).registerInterceptors(pf4bootPlugin);
    //register controllers
    getMainRequestMapping(pluginManager).registerControllers(pf4bootPlugin);
  }

  @Override
  public void stopPlugin(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    //unregister controllers
    PluginCleanup cleanup = new PluginCleanup();
    cleanup.run("controllers", () -> getMainRequestMapping(pluginManager).unregisterControllers(pf4bootPlugin));
    //unregister Interceptor
    cleanup.run("interceptors", () -> getMainRequestMapping(pluginManager).unregisterInterceptors(pf4bootPlugin));
    cleanup.run("MVC caches", () -> PluginWebCacheCleaner.clear(
        pluginManager.getApplicationContext(), pf4bootPlugin.getWrapper().getPluginClassLoader()));

    try {
      MethodValidationPostProcessor p =
          pluginManager.getApplicationContext().getBeanFactory().getBeanProvider(MethodValidationPostProcessor.class)
              .getIfAvailable();
      if (p != null) {
        Field field = p.getClass().getDeclaredField("validator");
        field.setAccessible(true);
        Object validator = ReflectionUtils.getField(field, p);

        if (validator != null &&
            validator.getClass().getClassLoader() == pf4bootPlugin.getPluginContext().getClassLoader()) {
          ReflectionUtils.setField(field, p, null);
        }
      }

    } catch (Exception e) {
      LOG.warn("unregister validator error", e);
    }
    cleanup.throwIfFailed();
  }

  @Override
  public void stoppedPlugin(Pf4bootPlugin pf4bootPlugin) {

  }

  private PluginRequestMappingHandlerMapping getMainRequestMapping(Pf4bootPluginManager pluginManager) {
    return (PluginRequestMappingHandlerMapping)
        pluginManager.getApplicationContext().getBeanFactory().getBean(REQUEST_MAPPING_HANDLER_MAPPING);
  }
}
