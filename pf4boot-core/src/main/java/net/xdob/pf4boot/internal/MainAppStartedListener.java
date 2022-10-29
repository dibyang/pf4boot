package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginHandler;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.annotation.EventListener;
import net.xdob.pf4boot.spring.boot.Pf4bootMainAppStartedEvent;
import org.pf4j.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MainAppStartedListener
 *
 * @author yangzj
 * @version 1.0
 */
@Component
public class MainAppStartedListener implements ApplicationListener<ApplicationStartedEvent> {
  static final Logger log = LoggerFactory.getLogger(MainAppStartedListener.class);
  @Autowired
  private Pf4bootPluginManager pluginManager;

  @Autowired
  private ApplicationContext applicationContext;

  @Override
  public void onApplicationEvent(ApplicationStartedEvent event) {
    Pf4bootPlugin plugin = this.getPlugin(event.getSource());
    if (plugin==null){
      Map<String, Object> beans = event.getApplicationContext().getBeansWithAnnotation(EventListener.class);
      for (Object listener : beans.values()) {
        pluginManager.getPf4bootEventBus().register(listener);
      }
      if(!pluginManager.isMainApplicationStarted()){
        if (pluginManager.isAutoStartPlugin()) {
          pluginManager.startPlugins();
        }

        pluginManager.getPlugins(PluginState.STARTED).forEach(pluginWrapper -> {
          Pf4bootPluginHandler pf4BootPluginService = (Pf4bootPluginHandler) pluginWrapper.getPlugin();
          ApplicationContext pluginAppCtx = pf4BootPluginService.getApplicationContext();
          pluginAppCtx.publishEvent(new Pf4bootMainAppStartedEvent(applicationContext));
        });
        pluginManager.setMainApplicationStarted(true);
      }
    }
  }

  private Pf4bootPlugin getPlugin(Object source) {
    if(source instanceof PluginApplication){
      return ((PluginApplication)source).getPlugin();
    }
    return null;
  }

}
