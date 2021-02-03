package com.ls.pf4boot;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.EventBus2;

/**
 * Pf4bootEventBusImpl
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootEventBusImpl implements Pf4bootEventBus {
  private final EventBus eventBus;

  public Pf4bootEventBusImpl() {
    this.eventBus = new EventBus2();

  }

  @Override
  public void post(Object event) {
    eventBus.post(event);
  }

  @Override
  public void register(Object listener) {
    eventBus.register(listener);
  }

  @Override
  public void unregister(Object listener) {
    eventBus.unregister(listener);
  }
}
