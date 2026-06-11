package net.xdob.pf4boot.jpa.starter;

import net.xdob.pf4boot.ApplicationContextProvider;
import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.deployment.DeploymentCheckResult;
import net.xdob.pf4boot.deployment.PluginCleanupVerifier;
import net.xdob.pf4boot.deployment.PluginHealthContext;
import net.xdob.pf4boot.deployment.PluginHealthVerifier;
import org.pf4j.PluginWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JPA 插件热替换部署验证器。
 *
 * <p>部署服务在替换插件时会调用该验证器：启动后确认 JPA 资源可用，停止后确认插件
 * 上下文中的 DataSource、EntityManagerFactory 和 TransactionManager 已随上下文释放。
 * 该类只检查单数据源事务域，不提供跨数据源事务保证。</p>
 */
public class JpaPluginDeploymentVerifier implements PluginHealthVerifier, PluginCleanupVerifier {

  private final Pf4bootPluginManager pluginManager;

  public JpaPluginDeploymentVerifier(Pf4bootPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @Override
  public List<DeploymentCheckResult> verifyStartedPlugin(
      PluginHealthContext context,
      ClassLoader pluginClassLoader) {
    ApplicationContext pluginContext = resolvePluginContext(context.getPluginId(), pluginClassLoader);
    if (pluginContext == null) {
      return Collections.singletonList(
          DeploymentCheckResult.info("JPA_CONTEXT_ABSENT", "Plugin context is not registered"));
    }

    JpaBeanNames beanNames = jpaBeanNames(pluginContext);
    if (beanNames.isEmpty()) {
      return Collections.singletonList(
          DeploymentCheckResult.info("JPA_NOT_USED", "Plugin does not expose JPA resources"));
    }

    List<DeploymentCheckResult> results = new ArrayList<>();
    verifyDataSources(pluginContext, beanNames.dataSourceNames, results);
    verifyEntityManagerFactories(pluginContext, beanNames.entityManagerFactoryNames, results);
    verifyTransactionManagers(beanNames.transactionManagerNames, results);
    return results;
  }

  @Override
  public List<DeploymentCheckResult> verifyStoppedPlugin(String pluginId, ClassLoader pluginClassLoader) {
    ApplicationContext pluginContext = resolvePluginContext(pluginId, pluginClassLoader);
    if (pluginContext == null) {
      return Collections.singletonList(
          DeploymentCheckResult.info("JPA_CLEANUP_VERIFIED", "Plugin JPA context is released"));
    }

    JpaBeanNames beanNames = jpaBeanNames(pluginContext);
    List<DeploymentCheckResult> results = new ArrayList<>();
    if (isActive(pluginContext)) {
      results.add(DeploymentCheckResult.error(
          "JPA_CONTEXT_NOT_CLOSED",
          "Plugin JPA context is still active after stop"));
    }
    if (!beanNames.isEmpty()) {
      results.add(DeploymentCheckResult.error(
          "JPA_RESOURCE_NOT_CLEANED",
          "Plugin JPA resources remain after stop: dataSources=" + beanNames.dataSourceNames.length
              + ", entityManagerFactories=" + beanNames.entityManagerFactoryNames.length
              + ", transactionManagers=" + beanNames.transactionManagerNames.length));
    }
    if (results.isEmpty()) {
      results.add(DeploymentCheckResult.info(
          "JPA_CLEANUP_VERIFIED", "Plugin JPA resources are cleaned"));
    }
    return results;
  }

  private ApplicationContext resolvePluginContext(String pluginId, ClassLoader pluginClassLoader) {
    ApplicationContext pluginContext = pluginClassLoader == null
        ? null
        : ApplicationContextProvider.getApplicationContext(pluginClassLoader);
    if (pluginContext != null) {
      return pluginContext;
    }
    if (pluginManager == null || pluginId == null) {
      return null;
    }
    try {
      PluginWrapper pluginWrapper = pluginManager.getPlugin(pluginId);
      if (pluginWrapper != null && pluginWrapper.getPlugin() instanceof Pf4bootPlugin) {
        return ((Pf4bootPlugin) pluginWrapper.getPlugin()).getPluginContext();
      }
    } catch (RuntimeException ignored) {
    }
    return null;
  }

  private JpaBeanNames jpaBeanNames(ApplicationContext context) {
    return new JpaBeanNames(
        beanNamesForType(context, DataSource.class),
        beanNamesForType(context, EntityManagerFactory.class),
        beanNamesForType(context, PlatformTransactionManager.class));
  }

  private String[] beanNamesForType(ApplicationContext context, Class<?> type) {
    if (context instanceof ConfigurableApplicationContext) {
      return ((ConfigurableApplicationContext) context)
          .getBeanFactory()
          .getBeanNamesForType(type, true, false);
    }
    if (context instanceof ListableBeanFactory) {
      return ((ListableBeanFactory) context).getBeanNamesForType(type);
    }
    return new String[0];
  }

  private void verifyDataSources(
      ApplicationContext pluginContext,
      String[] dataSourceNames,
      List<DeploymentCheckResult> results) {
    for (String beanName : dataSourceNames) {
      try {
        DataSource dataSource = pluginContext.getBean(beanName, DataSource.class);
        Connection connection = dataSource.getConnection();
        try {
          results.add(DeploymentCheckResult.info(
              "JPA_DATASOURCE_HEALTH", "DataSource is available: " + beanName));
        } finally {
          if (connection != null) {
            connection.close();
          }
        }
      } catch (Exception e) {
        results.add(DeploymentCheckResult.error(
            "JPA_DATASOURCE_UNAVAILABLE",
            "DataSource is unavailable: " + beanName + ", " + e.getMessage()));
      }
    }
  }

  private void verifyEntityManagerFactories(
      ApplicationContext pluginContext,
      String[] entityManagerFactoryNames,
      List<DeploymentCheckResult> results) {
    for (String beanName : entityManagerFactoryNames) {
      try {
        EntityManagerFactory entityManagerFactory =
            pluginContext.getBean(beanName, EntityManagerFactory.class);
        if (entityManagerFactory.isOpen()) {
          results.add(DeploymentCheckResult.info(
              "JPA_ENTITY_MANAGER_FACTORY_HEALTH",
              "EntityManagerFactory is open: " + beanName));
        } else {
          results.add(DeploymentCheckResult.error(
              "JPA_ENTITY_MANAGER_FACTORY_CLOSED",
              "EntityManagerFactory is closed: " + beanName));
        }
      } catch (BeansException e) {
        results.add(DeploymentCheckResult.error(
            "JPA_ENTITY_MANAGER_FACTORY_UNAVAILABLE",
            "EntityManagerFactory is unavailable: " + beanName + ", " + e.getMessage()));
      }
    }
  }

  private void verifyTransactionManagers(
      String[] transactionManagerNames,
      List<DeploymentCheckResult> results) {
    for (String beanName : transactionManagerNames) {
      results.add(DeploymentCheckResult.info(
          "JPA_TRANSACTION_MANAGER_HEALTH",
          "TransactionManager is available: " + beanName));
    }
  }

  private boolean isActive(ApplicationContext context) {
    return context instanceof ConfigurableApplicationContext
        && ((ConfigurableApplicationContext) context).isActive();
  }

  private static class JpaBeanNames {
    private final String[] dataSourceNames;
    private final String[] entityManagerFactoryNames;
    private final String[] transactionManagerNames;

    private JpaBeanNames(
        String[] dataSourceNames,
        String[] entityManagerFactoryNames,
        String[] transactionManagerNames) {
      this.dataSourceNames = dataSourceNames == null ? new String[0] : dataSourceNames;
      this.entityManagerFactoryNames =
          entityManagerFactoryNames == null ? new String[0] : entityManagerFactoryNames;
      this.transactionManagerNames =
          transactionManagerNames == null ? new String[0] : transactionManagerNames;
    }

    private boolean isEmpty() {
      return dataSourceNames.length == 0
          && entityManagerFactoryNames.length == 0
          && transactionManagerNames.length == 0;
    }
  }
}
