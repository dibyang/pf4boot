package net.xdob.pf4boot.internal;

import com.google.common.eventbus.Subscribe;
import net.xdob.pf4boot.annotation.EventListenerComponent;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.PluginStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * LoggingPluginStateListener2
 *
 * @author yangzj
 * @version 1.0
 */
@ConditionalOnProperty(prefix = Pf4bootProperties.PREFIX, value = "runtime-mode", havingValue = "dev")
@EventListenerComponent
public class LoggingPluginStateListener2  {

  private static final Logger log = LoggerFactory.getLogger(LoggingPluginStateListener2.class);

  @Subscribe
  public void pluginStateChanged(PluginStateEvent event) {
    log.debug("The state of plugin '{}' has changed from '{}' to '{}'", event.getPlugin().getPluginId(),
        event.getOldState(), event.getPluginState());
  }
}
