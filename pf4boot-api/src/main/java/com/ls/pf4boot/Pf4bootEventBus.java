package com.ls.pf4boot;

/**
 * Pf4bootEventBus
 *
 * @author yangzj
 * @version 1.0
 */
public interface Pf4bootEventBus {
  void post(Object event);
  void register(Object listener);
  void unregister(Object listener);
}
