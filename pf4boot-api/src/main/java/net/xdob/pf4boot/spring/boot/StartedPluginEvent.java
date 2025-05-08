package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.pf4j.Plugin;

/**
 * StartedPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class StartedPluginEvent extends PluginEvent {
  public StartedPluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }
}
