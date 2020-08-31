package com.ls.pf4boot.spring.boot;

import com.ls.pf4boot.Pf4bootPlugin;

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
