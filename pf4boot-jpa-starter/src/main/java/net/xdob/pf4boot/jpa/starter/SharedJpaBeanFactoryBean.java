package net.xdob.pf4boot.jpa.starter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.HierarchicalBeanFactory;

/**
 * 从父 BeanFactory 暴露共享 JPA Bean。
 *
 * <p>Spring Data JPA 在注册 Repository 时需要当前插件上下文里存在目标 EMF/TM
 * 的 BeanDefinition。该 FactoryBean 只在当前上下文占位，实际对象仍从父上下文的
 * 领域能力插件共享 Bean 中取得。</p>
 */
public class SharedJpaBeanFactoryBean implements FactoryBean<Object>, BeanFactoryAware {

  private String targetBeanName;

  private Class<?> objectType;

  private BeanFactory beanFactory;

  public void setTargetBeanName(String targetBeanName) {
    this.targetBeanName = targetBeanName;
  }

  public void setObjectType(Class<?> objectType) {
    this.objectType = objectType;
  }

  @Override
  public Object getObject() {
    BeanFactory parentBeanFactory = getParentBeanFactory();
    return parentBeanFactory.getBean(this.targetBeanName, this.objectType);
  }

  @Override
  public Class<?> getObjectType() {
    return this.objectType;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
    this.beanFactory = beanFactory;
  }

  private BeanFactory getParentBeanFactory() {
    if (this.beanFactory instanceof HierarchicalBeanFactory) {
      BeanFactory parentBeanFactory = ((HierarchicalBeanFactory) this.beanFactory).getParentBeanFactory();
      if (parentBeanFactory != null) {
        return parentBeanFactory;
      }
    }
    throw new IllegalStateException(
        "Shared JPA mode requires parent BeanFactory for bean '" + this.targetBeanName + "'.");
  }
}
