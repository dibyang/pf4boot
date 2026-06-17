package net.xdob.pf4boot.jpa.domain.starter;

import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.jpa.domain.JpaDataSourceDefinition;
import net.xdob.pf4boot.jpa.domain.JpaDomainDefinition;
import net.xdob.pf4boot.jpa.domain.JpaDomainDefinitionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 解析 provider 插件声明的 JPA domain 定义。
 */
final class JpaDomainDefinitionResolver {

  private static final Logger LOG = LoggerFactory.getLogger(JpaDomainDefinitionResolver.class);

  private JpaDomainDefinitionResolver() {
  }

  static JpaDomainDefinition resolve(
      BeanFactory beanFactory,
      Pf4bootJpaDomainProperties legacyProperties) {
    JpaDomainDefinition beanDefinition = resolveBeanDefinition(beanFactory);
    if (beanDefinition != null) {
      return beanDefinition;
    }

    JpaDomainDefinition providerDefinition = resolveProviderDefinition(beanFactory);
    if (providerDefinition != null) {
      return providerDefinition;
    }

    if (legacyProperties != null && legacyProperties.hasLegacyDomainDefinition()) {
      LOG.warn("[PF4BOOT-JPA] Deprecated provider JPA configuration 'pf4boot.plugin.jpa.domain.*' "
          + "is used as compatibility fallback. Plugin JPA domain structure should be declared "
          + "by the provider plugin via JpaDomainDefinition.");
      return fromLegacyProperties(legacyProperties);
    }

    throw new IllegalStateException(
        "[PJF-009] Domain provider plugin did not provide JpaDomainDefinition.");
  }

  private static JpaDomainDefinition resolveBeanDefinition(BeanFactory beanFactory) {
    if (!(beanFactory instanceof ListableBeanFactory)) {
      return null;
    }
    Map<String, JpaDomainDefinition> definitions =
        ((ListableBeanFactory) beanFactory).getBeansOfType(JpaDomainDefinition.class);
    if (definitions == null || definitions.isEmpty()) {
      return null;
    }
    if (definitions.size() > 1) {
      throw new IllegalStateException(
          "[PJF-009] Domain provider plugin must expose only one JpaDomainDefinition bean.");
    }
    return definitions.values().iterator().next();
  }

  private static JpaDomainDefinition resolveProviderDefinition(BeanFactory beanFactory) {
    if (beanFactory == null) {
      return null;
    }
    try {
      if (!beanFactory.containsBean(PluginApplication.BEAN_PLUGIN)) {
        return null;
      }
      Object plugin = beanFactory.getBean(PluginApplication.BEAN_PLUGIN);
      if (plugin instanceof JpaDomainDefinitionProvider) {
        return ((JpaDomainDefinitionProvider) plugin).jpaDomainDefinition();
      }
    } catch (BeansException e) {
      LOG.debug("[PF4BOOT-JPA] cannot resolve JpaDomainDefinitionProvider from plugin bean", e);
    }
    return null;
  }

  private static JpaDomainDefinition fromLegacyProperties(Pf4bootJpaDomainProperties properties) {
    DataSourceProperties datasource = properties.getDatasource();
    JpaDataSourceDefinition.Builder dataSourceBuilder =
        JpaDataSourceDefinition.builder(datasource.determineUrl())
            .username(datasource.determineUsername())
            .password(datasource.determinePassword());
    if (StringUtils.hasText(datasource.getDriverClassName())) {
      dataSourceBuilder.driverClassName(datasource.getDriverClassName());
    }
    return JpaDomainDefinition.builder(properties.requireDomainId())
        .entityPackages(properties.getEntityPackages())
        .dataSource(dataSourceBuilder.build())
        .ddlAuto(properties.resolveDdlAuto())
        .dataSourceName(properties.getDataSourceName())
        .entityManagerFactoryName(properties.getEntityManagerFactoryName())
        .transactionManagerName(properties.getTransactionManagerName())
        .descriptorName(properties.getDescriptorName())
        .build();
  }
}
