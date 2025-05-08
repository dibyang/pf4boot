package net.xdob.pf4boot.internal;

import org.pf4j.PluginStateEvent;
import org.pf4j.PluginStateListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Pf4bootPluginStateListener
 *
 * @author yangzj
 * @version 1.0
 */

public class Pf4bootPluginStateListener implements PluginStateListener {
  private final ConfigurableApplicationContext platformContext;

  public Pf4bootPluginStateListener(ConfigurableApplicationContext platformContext) {
    this.platformContext = platformContext;
  }

  @Override
  public void pluginStateChanged(PluginStateEvent event) {
    platformContext.publishEvent(event);
  }
}
