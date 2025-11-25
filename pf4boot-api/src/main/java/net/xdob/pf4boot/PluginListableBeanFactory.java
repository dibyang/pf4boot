package net.xdob.pf4boot;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * PluginListableBeanFactory
 *
 * @author yangzj
 * @version 1.0
 */
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

}
