package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.pf4j.Plugin;

/**
 * StoppingPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class StoppingPluginEvent extends PluginEvent {
  public StoppingPluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }
}
