package com.ls.pf4boot.spring.boot;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

public class Pf4bootMainAppStartedEvent extends ApplicationEvent {

  private static final long serialVersionUID = 6638140452437569228L;

  public Pf4bootMainAppStartedEvent(ApplicationContext mainApplicationContext) {
    super(mainApplicationContext);
  }
}
