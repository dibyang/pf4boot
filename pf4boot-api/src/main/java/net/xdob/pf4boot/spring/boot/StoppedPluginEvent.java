package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPlugin;

/**
 * StoppedPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class StoppedPluginEvent extends PluginEvent {
  public StoppedPluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }
}
