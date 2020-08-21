package com.ls.pf4boot.spring.boot;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

public class Pf4bootMainAppReadyEvent extends ApplicationEvent {

  private static final long serialVersionUID = -8780401612862384173L;

  public Pf4bootMainAppReadyEvent(ApplicationContext mainApplicationContext) {
    super(mainApplicationContext);
  }
}
