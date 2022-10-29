package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootEventBus;
import org.pf4j.PluginStateEvent;
import org.pf4j.PluginStateListener;

/**
 * Pf4bootPluginStateListener
 *
 * @author yangzj
 * @version 1.0
 */

public class Pf4bootPluginStateListener implements PluginStateListener {
  private Pf4bootEventBus eventBus;

  public Pf4bootPluginStateListener(Pf4bootEventBus eventBus) {
    this.eventBus = eventBus;
  }

  @Override
  public void pluginStateChanged(PluginStateEvent event) {
    eventBus.post(event);
  }
}
