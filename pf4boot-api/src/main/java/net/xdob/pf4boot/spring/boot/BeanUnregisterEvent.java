package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.modal.SharingScope;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

/**
 * bean取消注册到主APP上事件
 */
public class BeanUnregisterEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1653148906452766719L;

  private final String beanName;
  private final Object bean;
  private final SharingScope scope;
  public BeanUnregisterEvent(SharingScope scope, ApplicationContext applicationContext, String beanName, Object bean) {
    super(applicationContext);
    this.scope = scope;
    this.beanName = beanName;
    this.bean = bean;
  }

  public SharingScope getScope() {
    return scope;
  }

  public String getBeanName() {
    return beanName;
  }

  public Object getBean() {
    return bean;
  }
}
