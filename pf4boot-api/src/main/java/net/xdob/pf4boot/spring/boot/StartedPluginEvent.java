package net.xdob.pf4boot.spring.boot;

import org.pf4j.Plugin;

/**
 * StartedPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class StartedPluginEvent extends PluginEvent {
  public StartedPluginEvent(Plugin plugin) {
    super(plugin);
  }
}
