package com.ls.pf4boot.internal;

import com.ls.pf4boot.Pf4bootPlugin;
import com.ls.pf4boot.Pf4bootPluginService;
import com.ls.pf4boot.Pf4bootPluginManager;
import com.ls.pf4boot.PluginApplication;
import com.ls.pf4boot.spring.boot.Pf4bootMainAppStartedEvent;
import org.pf4j.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

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
      if(!pluginManager.isMainApplicationStarted()){
        if (pluginManager.isAutoStartPlugin()) {
          pluginManager.startPlugins();
        }

        pluginManager.getPlugins(PluginState.STARTED).forEach(pluginWrapper -> {
          Pf4bootPluginService pf4BootPluginService = (Pf4bootPluginService) pluginWrapper.getPlugin();
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
