package net.xdob.pf4boot.spring.boot;

import org.pf4j.Plugin;

/**
 * StoppingPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class StoppingPluginEvent extends PluginEvent {
  public StoppingPluginEvent(Plugin plugin) {
    super(plugin);
  }
}
