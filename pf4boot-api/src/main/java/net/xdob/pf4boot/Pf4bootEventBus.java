package net.xdob.pf4boot;

/**
 * Pf4bootEventBus
 *
 * @author yangzj
 * @version 1.0
 */
public interface Pf4bootEventBus extends Pf4bootEventPoster {
  void register(Object listener);
  void unregister(Object listener);
}
