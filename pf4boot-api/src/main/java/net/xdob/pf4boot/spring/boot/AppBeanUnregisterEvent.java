package net.xdob.pf4boot.spring.boot;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

/**
 * bean取消注册到主APP上事件
 */
public class AppBeanUnregisterEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1653148906452766719L;

  private final String beanName;
  private final Object bean;
  public AppBeanUnregisterEvent(ApplicationContext applicationContext, String beanName, Object bean) {
    super(applicationContext);
    this.beanName = beanName;
    this.bean = bean;
  }

  public String getBeanName() {
    return beanName;
  }

  public Object getBean() {
    return bean;
  }
}
