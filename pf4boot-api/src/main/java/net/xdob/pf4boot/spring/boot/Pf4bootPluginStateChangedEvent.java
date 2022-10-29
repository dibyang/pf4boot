package net.xdob.pf4boot.spring.boot;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

public class Pf4bootPluginStateChangedEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1653148906452766719L;

  public Pf4bootPluginStateChangedEvent(ApplicationContext mainApplicationContext) {
    super(mainApplicationContext);
  }
}
