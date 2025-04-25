package net.xdob.pf4boot.spring.boot;

import org.pf4j.Plugin;
import org.springframework.context.ApplicationEvent;

/**
 * PluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class PluginEvent extends ApplicationEvent {

  public PluginEvent(Plugin plugin) {
    super(plugin);
  }

  public Plugin getPlugin(){
    return (Plugin)this.getSource();
  }

}
