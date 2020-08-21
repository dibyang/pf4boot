package com.ls.pf4boot.internal;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.event.EventListenerMethodProcessor;


public class PluginListableBeanFactory extends DefaultListableBeanFactory {

  private ClassLoader classLoader;

  public PluginListableBeanFactory(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
    /*
    try {
      return classLoader.loadClass(beanName);
    } catch (ClassNotFoundException ignored) {
    } // */
    return super.predictBeanType(beanName, mbd, typesToMatch);
  }

  @Override
  public Object getBean(String name) throws BeansException {
    Object bean = super.getBean(name);
    if (bean instanceof EventListenerMethodProcessor) {
      ((EventListenerMethodProcessor) bean).postProcessBeanFactory(this);
    }
    return bean;
  }
}
