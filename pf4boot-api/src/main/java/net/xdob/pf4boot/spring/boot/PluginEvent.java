package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.pf4j.Plugin;
import org.springframework.context.ApplicationEvent;

/**
 * PluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class PluginEvent extends ApplicationEvent {

  public PluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }

  public Pf4bootPlugin getPlugin(){
    return (Pf4bootPlugin)this.getSource();
  }

}
