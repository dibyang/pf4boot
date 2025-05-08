package net.xdob.pf4boot.internal;


import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.PluginStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * LoggingPluginStateListener2
 *
 * @author yangzj
 * @version 1.0
 */
@ConditionalOnProperty(prefix = Pf4bootProperties.PREFIX, value = "runtime-mode", havingValue = "dev")
@Component
public class LoggingPluginStateListener2  {

  private static final Logger LOG = LoggerFactory.getLogger(LoggingPluginStateListener2.class);

  @EventListener(PluginStateEvent.class)
  public void pluginStateChanged(PluginStateEvent event) {
    LOG.info("The state of plugin '{}' has changed from '{}' to '{}'", event.getPlugin().getPluginId(),
        event.getOldState(), event.getPluginState());
  }
}
