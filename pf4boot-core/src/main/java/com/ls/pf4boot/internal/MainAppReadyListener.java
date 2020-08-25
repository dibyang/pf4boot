package com.ls.pf4boot.internal;

import com.ls.pf4boot.PluginApplication;
import com.ls.pf4boot.Pf4bootPlugin;
import com.ls.pf4boot.Pf4bootPluginManager;
import com.ls.pf4boot.spring.boot.Pf4bootMainAppReadyEvent;
import org.pf4j.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * MainAppReadyListener
 *
 * @author yangzj
 * @version 1.0
 */
@Component
public class MainAppReadyListener implements ApplicationListener<ApplicationReadyEvent> {
  static final Logger log = LoggerFactory.getLogger(MainAppReadyListener.class);

  @Autowired
  private Pf4bootPluginManager pluginManager;

  @Autowired
  private ApplicationContext applicationContext;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    Pf4bootPlugin plugin = this.getPlugin(event.getSource());
    if (plugin==null){
      pluginManager.getPlugins(PluginState.STARTED).forEach(pluginWrapper -> {
        Pf4bootPlugin pf4bootPlugin = (Pf4bootPlugin) pluginWrapper.getPlugin();
        ApplicationContext pluginAppCtx = pf4bootPlugin.getApplicationContext();
        pluginAppCtx.publishEvent(new Pf4bootMainAppReadyEvent(applicationContext));
      });
    }
  }

  private Pf4bootPlugin getPlugin(Object source) {
    if(source instanceof PluginApplication){
      return ((PluginApplication)source).getPlugin();
    }
    return null;
  }
}
