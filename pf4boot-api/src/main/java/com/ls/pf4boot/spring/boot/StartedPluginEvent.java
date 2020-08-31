package com.ls.pf4boot.spring.boot;

import com.ls.pf4boot.Pf4bootPlugin;

/**
 * StartedPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class StartedPluginEvent extends PluginEvent {
  public StartedPluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }
}
