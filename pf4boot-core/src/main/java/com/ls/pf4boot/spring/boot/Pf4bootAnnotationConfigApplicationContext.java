package com.ls.pf4boot.spring.boot;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Pf4bootAnnotationConfigApplicationContext
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootAnnotationConfigApplicationContext extends AnnotationConfigApplicationContext {
  public Pf4bootAnnotationConfigApplicationContext() {
  }

  public Pf4bootAnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory) {
    super(beanFactory);
  }

  @Override
  public void setParent(ApplicationContext parent) {
    super.setParent(parent);
  }

  protected void initApplicationEventMulticaster(){
    super.initApplicationEventMulticaster();
  }
}
