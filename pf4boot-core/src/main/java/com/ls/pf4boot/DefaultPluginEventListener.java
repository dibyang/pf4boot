package com.ls.pf4boot;

import com.google.common.eventbus.Subscribe;
import com.ls.pf4boot.annotation.EventListenerComponent;
import com.ls.pf4boot.spring.boot.PreStartPluginEvent;
import com.ls.pf4boot.spring.boot.StartedPluginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DefaultPluginEventListener
 *
 * @author yangzj
 * @version 1.0
 */
@EventListenerComponent
public class DefaultPluginEventListener  {
  static final Logger log = LoggerFactory.getLogger(DefaultPluginEventListener.class);

  @Subscribe
  void onEvent(PreStartPluginEvent event){
    log.info("Plugin pre start : "+ event.getPlugin().getWrapper().getPluginId());
  }

  @Subscribe
  void onEvent(StartedPluginEvent event){
    log.info("Plugin started : "+ event.getPlugin().getWrapper().getPluginId());
  }

}
