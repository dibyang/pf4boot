package com.google.common.eventbus;

import com.google.common.util.concurrent.MoreExecutors;

/**
 * @author yangzj
 * @date 2021/2/3
 */
public class EventBus2 extends EventBus{

  public EventBus2() {
    super("default",
        MoreExecutors.directExecutor(),
        new Dispatcher2(),
        EventBus.LoggingHandler.INSTANCE);

  }

}
