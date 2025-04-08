package net.xdob.pf4boot.spring.boot;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

/**
 * 应用释放缓存资源事件
 */
public class AppCacheFreeEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1653148906452766719L;

  public AppCacheFreeEvent(ApplicationContext context) {
    super(context);
  }
}
