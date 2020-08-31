package com.ls.pf4boot.spring.boot;

import com.ls.pf4boot.Pf4bootPlugin;

/**
 * PreSopPluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class PreStopPluginEvent extends PluginEvent {
  public PreStopPluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }
}
