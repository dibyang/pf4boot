package net.xdob.pf4boot.jpa.starter;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 根据当前插件最终 JPA 绑定判断是否启用本地 JPA Bean。
 */
public class LocalJpaModeCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    BeanDefinitionRegistry registry = context.getRegistry();
    BeanFactory beanFactory = registry instanceof BeanFactory ? (BeanFactory) registry : null;
    JpaPluginBinding binding = JpaPluginBindingResolver.resolve(context.getEnvironment(), beanFactory);
    if (binding == null) {
      return true;
    }
    return !binding.isShared();
  }
}
