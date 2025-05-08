package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.pf4j.Plugin;

/**
 * PreStartPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class PreStartPluginEvent extends PluginEvent {
  public PreStartPluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }
}
