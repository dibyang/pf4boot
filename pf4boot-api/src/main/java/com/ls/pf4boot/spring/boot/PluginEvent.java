package com.ls.pf4boot.spring.boot;

import com.ls.pf4boot.Pf4bootPlugin;
import org.springframework.context.ApplicationEvent;

import java.util.EventObject;

/**
 * PluginEvent
 *
 * @author yangzj
 * @version 1.0
 */
public class PluginEvent extends ApplicationEvent {

  public PluginEvent(Pf4bootPlugin plugin) {
    super(plugin);
  }

  public Pf4bootPlugin getPlugin(){
    return (Pf4bootPlugin)this.getSource();
  }

}
