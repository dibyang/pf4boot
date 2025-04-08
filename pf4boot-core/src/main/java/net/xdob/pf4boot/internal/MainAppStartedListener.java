package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.PluginApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * MainAppStartedListener
 *
 * @author yangzj
 * @version 1.0
 */
public class MainAppStartedListener implements ApplicationListener<ApplicationStartedEvent> {
  static final Logger log = LoggerFactory.getLogger(MainAppStartedListener.class);
  @Autowired
  private Pf4bootPluginManager pluginManager;

  @Override
  public void onApplicationEvent(ApplicationStartedEvent event) {
    Pf4bootPlugin plugin = this.getPlugin(event.getSource());
    if (plugin==null){
      if(!pluginManager.isApplicationStarted()){
        if (pluginManager.isAutoStartPlugin()) {
          pluginManager.startPlugins();
        }
        pluginManager.setApplicationStarted(true);
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
