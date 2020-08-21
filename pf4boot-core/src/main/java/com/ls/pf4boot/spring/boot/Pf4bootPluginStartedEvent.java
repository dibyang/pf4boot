package com.ls.pf4boot.spring.boot;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

public class Pf4bootPluginStartedEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1651490578605729784L;

  public Pf4bootPluginStartedEvent(ApplicationContext pluginApplicationContext) {
    super(pluginApplicationContext);
  }
}
