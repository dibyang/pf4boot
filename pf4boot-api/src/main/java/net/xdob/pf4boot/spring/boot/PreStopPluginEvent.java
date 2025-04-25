package net.xdob.pf4boot.spring.boot;

import org.pf4j.Plugin;

/**
 * PreSopPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class PreStopPluginEvent extends PluginEvent {
  public PreStopPluginEvent(Plugin plugin) {
    super(plugin);
  }
}
