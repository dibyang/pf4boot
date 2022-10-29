package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPlugin;

/**
 * PreSopPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class PreStopPluginEvent extends PluginEvent {
  public PreStopPluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }
}
