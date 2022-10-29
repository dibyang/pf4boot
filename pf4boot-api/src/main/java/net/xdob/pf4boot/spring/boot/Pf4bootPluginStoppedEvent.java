package net.xdob.pf4boot.spring.boot;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;


public class Pf4bootPluginStoppedEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1048404352252169025L;

  public Pf4bootPluginStoppedEvent(ApplicationContext pluginApplicationContext) {
    super(pluginApplicationContext);
  }
}
