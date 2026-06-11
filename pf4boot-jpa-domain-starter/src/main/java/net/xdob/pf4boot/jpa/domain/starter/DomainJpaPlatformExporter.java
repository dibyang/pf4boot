package net.xdob.pf4boot.jpa.domain.starter;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.jpa.domain.JpaDomainDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.transaction.PlatformTransactionManager;

/**
 * 领域 JPA Bean 平台导出器。
 *
 * <p>领域能力插件在自己的 Spring 上下文中创建 DataSource、EMF、TM，但消费插件只能从父
 * 平台上下文按约定名称解析这些 Bean。本类负责在领域插件初始化完成后导出，并在插件
 * 上下文销毁时反向注销，避免平台上下文残留过期事务域。</p>
 */
public class DomainJpaPlatformExporter
    implements SmartInitializingSingleton, DisposableBean, BeanFactoryAware {

  private static final Logger LOG = LoggerFactory.getLogger(DomainJpaPlatformExporter.class);

  private final Pf4bootJpaDomainProperties properties;

  private final DataSource dataSource;

  private final EntityManagerFactory entityManagerFactory;

  private final PlatformTransactionManager transactionManager;

  private final List<String> exportedBeanNames = new ArrayList<>();

  private BeanFactory beanFactory;

  private Pf4bootPluginManager pluginManager;

  private String group;

  public DomainJpaPlatformExporter(
      Pf4bootJpaDomainProperties properties,
      DataSource dataSource,
      EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {
    this.properties = properties;
    this.dataSource = dataSource;
    this.entityManagerFactory = entityManagerFactory;
    this.transactionManager = transactionManager;
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
    this.beanFactory = beanFactory;
  }

  @Override
  public void afterSingletonsInstantiated() {
    String domainId = this.properties.requireDomainId();
    Pf4bootPlugin plugin = getPlugin();
    this.pluginManager = plugin.getPluginManager();
    if (this.pluginManager == null) {
      throw new IllegalStateException(
          "[PJF-004] Domain JPA provider '" + domainId + "' cannot resolve Pf4bootPluginManager.");
    }
    this.group = plugin.getGroup();

    try {
      export(this.properties.resolveDataSourceName(), this.dataSource);
      export(this.properties.resolveEntityManagerFactoryName(), this.entityManagerFactory);
      export(this.properties.resolveTransactionManagerName(), this.transactionManager);
      export(this.properties.resolveDescriptorName(), descriptor(domainId, plugin));
      LOG.info(
          "[PF4BOOT-JPA] export domain {} to platform [{}], dataSource={}, entityManagerFactory={}, transactionManager={}, descriptor={}",
          domainId,
          this.group,
          this.properties.resolveDataSourceName(),
          this.properties.resolveEntityManagerFactoryName(),
          this.properties.resolveTransactionManagerName(),
          this.properties.resolveDescriptorName());
    } catch (RuntimeException e) {
      rollbackExports();
      throw new IllegalStateException(
          "[PJF-004] Domain JPA provider '" + domainId + "' failed to export platform beans.", e);
    }
  }

  private Pf4bootPlugin getPlugin() {
    if (this.beanFactory == null) {
      throw new IllegalStateException("[PJF-004] Domain JPA provider cannot access BeanFactory.");
    }
    try {
      return this.beanFactory.getBean(PluginApplication.BEAN_PLUGIN, Pf4bootPlugin.class);
    } catch (BeansException e) {
      throw new IllegalStateException(
          "[PJF-004] Domain JPA provider requires plugin bean '" +
              PluginApplication.BEAN_PLUGIN + "'.", e);
    }
  }

  private void export(String beanName, Object bean) {
    this.pluginManager.registerBeanToPlatformContext(this.group, beanName, bean);
    this.exportedBeanNames.add(beanName);
  }

  private JpaDomainDescriptor descriptor(String domainId, Pf4bootPlugin plugin) {
    return new JpaDomainDescriptor(
        domainId,
        plugin.getPluginId(),
        this.properties.resolveEntityPackages(),
        this.properties.resolveDataSourceName(),
        this.properties.resolveEntityManagerFactoryName(),
        this.properties.resolveTransactionManagerName(),
        true,
        System.currentTimeMillis());
  }

  @Override
  public void destroy() {
    rollbackExports();
  }

  private void rollbackExports() {
    if (this.pluginManager == null || this.exportedBeanNames.isEmpty()) {
      return;
    }

    List<String> beanNames = new ArrayList<>(this.exportedBeanNames);
    Collections.reverse(beanNames);
    for (String beanName : beanNames) {
      try {
        this.pluginManager.unregisterBeanFromPlatformContext(this.group, beanName);
        LOG.info("[PF4BOOT-JPA] unexport domain bean {} from platform [{}]", beanName, this.group);
      } catch (Exception e) {
        LOG.warn("[PF4BOOT-JPA] unexport domain bean {} from platform [{}] failed",
            beanName, this.group, e);
      }
    }
    this.exportedBeanNames.clear();
  }
}
