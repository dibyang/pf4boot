package net.xdob.pf4boot.spring.boot;

import org.pf4j.Plugin;

/**
 * PreStartPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class PreStartPluginEvent extends PluginEvent {
  public PreStartPluginEvent(Plugin plugin) {
    super(plugin);
  }
}
