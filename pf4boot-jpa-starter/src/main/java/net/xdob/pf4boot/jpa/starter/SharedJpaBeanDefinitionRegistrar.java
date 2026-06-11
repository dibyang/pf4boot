package net.xdob.pf4boot.jpa.starter;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManagerFactory;

/**
 * 为共享 JPA 模式注册本地占位 BeanDefinition。
 *
 * <p>这些 BeanDefinition 让 Spring Data Repository 后处理器能够在当前插件上下文
 * 找到指定名称的 EMF/TM；真正的 Bean 实例由 {@link SharedJpaBeanFactoryBean}
 * 从父上下文中的领域能力插件导出 Bean 取得。</p>
 */
public class SharedJpaBeanDefinitionRegistrar
    implements ImportBeanDefinitionRegistrar, BeanDefinitionRegistryPostProcessor, EnvironmentAware, PriorityOrdered {

  private Environment environment;

  @Override
  public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    registerSharedBeanDefinitions(registry);
  }

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    registerSharedBeanDefinitions(registry);
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private void registerSharedBeanDefinitions(BeanDefinitionRegistry registry) {
    BeanFactory beanFactory = registry instanceof BeanFactory ? (BeanFactory) registry : null;
    JpaPluginBinding binding = JpaPluginBindingResolver.resolve(environment, beanFactory);
    if (!binding.isShared()) {
      return;
    }

    String domainId = binding.getDomainId();
    if (!StringUtils.hasText(domainId)) {
      throw new IllegalStateException(
          "[PJF-006] Shared JPA binding for plugin '" + binding.getPluginId()
              + "' requires domain-id.");
    }

    registerDomainBeanDefinitions(registry, binding.getPluginId(), binding.primaryDomain());
    for (JpaDomainBinding additionalDomain : binding.getAdditionalDomains()) {
      registerDomainBeanDefinitions(registry, binding.getPluginId(), additionalDomain);
    }
  }

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  private void registerSharedBeanDefinition(
      BeanDefinitionRegistry registry,
      String beanName,
      Class<?> objectType) {
    if (registry.containsBeanDefinition(beanName)) {
      return;
    }

    BeanDefinition beanDefinition = BeanDefinitionBuilder
        .genericBeanDefinition(SharedJpaBeanFactoryBean.class)
        .addPropertyValue("targetBeanName", beanName)
        .addPropertyValue("objectType", objectType)
        .getBeanDefinition();

    registry.registerBeanDefinition(beanName, beanDefinition);
  }

  private void registerDomainBeanDefinitions(
      BeanDefinitionRegistry registry,
      String pluginId,
      JpaDomainBinding domainBinding) {
    String domainId = domainBinding.getDomainId();
    if (!StringUtils.hasText(domainId)) {
      throw new IllegalStateException(
          "[PJF-006] Shared JPA binding for plugin '" + pluginId
              + "' requires domain-id.");
    }

    registerSharedBeanDefinition(
        registry, domainBinding.resolveEntityManagerFactoryRef(), EntityManagerFactory.class);
    registerSharedBeanDefinition(
        registry, domainBinding.resolveTransactionManagerRef(), PlatformTransactionManager.class);
  }
}
