package net.xdob.pf4boot.internal;


import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.Pf4bootPluginSupport;
import org.springframework.web.servlet.mvc.method.PluginRequestMappingHandlerMapping;


public class WebPf4BootPluginSupport implements Pf4bootPluginSupport {

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
    getMainRequestMapping(pluginManager).unregisterControllers(pf4bootPlugin);
    //unregister Interceptor
    getMainRequestMapping(pluginManager).unregisterInterceptors(pf4bootPlugin);
  }

  @Override
  public void stoppedPlugin(Pf4bootPlugin pf4bootPlugin) {

  }

  private PluginRequestMappingHandlerMapping getMainRequestMapping(Pf4bootPluginManager pluginManager) {
    return (PluginRequestMappingHandlerMapping)
        pluginManager.getApplicationContext().getBean(REQUEST_MAPPING_HANDLER_MAPPING);
  }
}
