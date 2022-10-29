package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPlugin;

/**
 * StartingPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class StartingPluginEvent extends PluginEvent {
  public StartingPluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }
}
