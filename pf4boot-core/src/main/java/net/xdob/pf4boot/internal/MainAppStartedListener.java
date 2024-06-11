package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.TypeWrapper;
import net.xdob.pf4boot.annotation.EventListener;
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

  @Override
  public void onApplicationEvent(ApplicationStartedEvent event) {
    Pf4bootPlugin plugin = this.getPlugin(event.getSource());
    if (plugin==null){
      if(!pluginManager.isMainApplicationStarted()){
        if (pluginManager.isAutoStartPlugin()) {
          pluginManager.startPlugins();
        }
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
