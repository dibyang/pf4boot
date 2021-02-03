package com.google.common.eventbus;

import java.util.Iterator;

/**
 * @author yangzj
 * @date 2021/2/3
 */
public class Dispatcher2 extends Dispatcher{
  final Dispatcher dispatcher;
  final Dispatcher immediate;

  public Dispatcher2() {
    this.dispatcher = Dispatcher.perThreadDispatchQueue();
    this.immediate = Dispatcher.immediate();
  }

  @Override
  void dispatch(Object event, Iterator<Subscriber> subscribers) {
    if(event instanceof ImmediateEvent){
      immediate.dispatch(event,subscribers);
    }else{
      dispatcher.dispatch(event,subscribers);
    }
  }
}
