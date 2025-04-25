package net.xdob.pf4boot.spring.boot;

import org.pf4j.Plugin;

/**
 * StartingPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class StartingPluginEvent extends PluginEvent {
  public StartingPluginEvent(Plugin plugin) {
    super(plugin);
  }
}
