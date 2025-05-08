package net.xdob.pf4boot;

import com.google.common.eventbus.Subscribe;
import net.xdob.pf4boot.spring.boot.PreStartPluginEvent;
import net.xdob.pf4boot.spring.boot.StartedPluginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * DefaultPluginEventListener
 *
 * @author yangzj
 * @version 1.0
 */
public class DefaultPluginEventListener  {
  static final Logger log = LoggerFactory.getLogger(DefaultPluginEventListener.class);

  @EventListener
  public void onEvent(PreStartPluginEvent event){
		log.info("Plugin pre start : {}", event.getPlugin().getWrapper().getPluginId());
  }

  @EventListener
  public void onEvent(StartedPluginEvent event){
		log.info("Plugin started : {}", event.getPlugin().getWrapper().getPluginId());
  }

}
