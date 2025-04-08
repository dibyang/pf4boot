package net.xdob.pf4boot;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.EventBus2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pf4bootEventBusImpl
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootEventBusImpl implements Pf4bootEventBus {
  static Logger LOG = LoggerFactory.getLogger(Pf4bootEventBusImpl.class);

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
    LOG.info("register listener {}" , listener);
    eventBus.register(listener);
  }

  @Override
  public void unregister(Object listener) {
    eventBus.unregister(listener);
  }
}
