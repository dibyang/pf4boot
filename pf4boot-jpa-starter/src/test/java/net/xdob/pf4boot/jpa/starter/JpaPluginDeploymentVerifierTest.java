package net.xdob.pf4boot.jpa.starter;

import net.xdob.pf4boot.ApplicationContextProvider;
import net.xdob.pf4boot.deployment.DeploymentCheckResult;
import net.xdob.pf4boot.deployment.DeploymentCheckSeverity;
import net.xdob.pf4boot.deployment.PluginHealthContext;
import org.junit.After;
import org.junit.Test;
import org.pf4j.PluginState;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JpaPluginDeploymentVerifierTest {

  private AnnotationConfigApplicationContext context;

  @After
  public void tearDown() {
    if (context != null) {
      ApplicationContextProvider.unregisterApplicationContext(context);
      context.close();
    }
  }

  @Test
  public void healthVerifierReportsJpaResources() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean("dataSource", DataSource.class, () -> new TestDataSource(false));
    context.registerBean("entityManagerFactory", EntityManagerFactory.class,
        () -> entityManagerFactory(true));
    context.registerBean("transactionManager", PlatformTransactionManager.class,
        TestTransactionManager::new);
    context.refresh();
    ApplicationContextProvider.registerApplicationContext(context);

    JpaPluginDeploymentVerifier verifier = new JpaPluginDeploymentVerifier(null);
    List<DeploymentCheckResult> results = verifier.verifyStartedPlugin(
        new PluginHealthContext("deploy-1", "sample-jpa", PluginState.STARTED),
        context.getClassLoader());

    assertTrue(hasCode(results, "JPA_DATASOURCE_HEALTH"));
    assertTrue(hasCode(results, "JPA_ENTITY_MANAGER_FACTORY_HEALTH"));
    assertTrue(hasCode(results, "JPA_TRANSACTION_MANAGER_HEALTH"));
  }

  @Test
  public void healthVerifierFailsClosedEntityManagerFactory() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean("entityManagerFactory", EntityManagerFactory.class,
        () -> entityManagerFactory(false));
    context.refresh();
    ApplicationContextProvider.registerApplicationContext(context);

    JpaPluginDeploymentVerifier verifier = new JpaPluginDeploymentVerifier(null);
    List<DeploymentCheckResult> results = verifier.verifyStartedPlugin(
        new PluginHealthContext("deploy-1", "sample-jpa", PluginState.STARTED),
        context.getClassLoader());

    assertTrue(hasCode(results, "JPA_ENTITY_MANAGER_FACTORY_CLOSED"));
    assertEquals(DeploymentCheckSeverity.ERROR,
        result(results, "JPA_ENTITY_MANAGER_FACTORY_CLOSED").getSeverity());
  }

  @Test
  public void cleanupVerifierFailsWhenJpaResourcesRemain() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean("dataSource", DataSource.class, () -> new TestDataSource(false));
    context.refresh();
    ApplicationContextProvider.registerApplicationContext(context);

    JpaPluginDeploymentVerifier verifier = new JpaPluginDeploymentVerifier(null);
    List<DeploymentCheckResult> results =
        verifier.verifyStoppedPlugin("sample-jpa", context.getClassLoader());

    assertTrue(hasCode(results, "JPA_CONTEXT_NOT_CLOSED"));
    assertTrue(hasCode(results, "JPA_RESOURCE_NOT_CLEANED"));
  }

  @Test
  public void verifierIgnoresPluginsWithoutJpaResources() {
    context = new AnnotationConfigApplicationContext();
    context.refresh();
    ApplicationContextProvider.registerApplicationContext(context);

    JpaPluginDeploymentVerifier verifier = new JpaPluginDeploymentVerifier(null);
    List<DeploymentCheckResult> results = verifier.verifyStartedPlugin(
        new PluginHealthContext("deploy-1", "sample-non-jpa", PluginState.STARTED),
        context.getClassLoader());

    assertTrue(hasCode(results, "JPA_NOT_USED"));
  }

  private static boolean hasCode(List<DeploymentCheckResult> results, String code) {
    return result(results, code) != null;
  }

  private static DeploymentCheckResult result(List<DeploymentCheckResult> results, String code) {
    for (DeploymentCheckResult result : results) {
      if (code.equals(result.getCode())) {
        return result;
      }
    }
    return null;
  }

  private static EntityManagerFactory entityManagerFactory(boolean open) {
    return (EntityManagerFactory) Proxy.newProxyInstance(
        JpaPluginDeploymentVerifierTest.class.getClassLoader(),
        new Class[]{EntityManagerFactory.class},
        (Object proxy, Method method, Object[] args) -> {
          if ("isOpen".equals(method.getName())) {
            return open;
          }
          if ("toString".equals(method.getName())) {
            return "TestEntityManagerFactory";
          }
          if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
          }
          if ("equals".equals(method.getName())) {
            return proxy == args[0];
          }
          Class<?> returnType = method.getReturnType();
          if (Void.TYPE == returnType) {
            return null;
          }
          if (Boolean.TYPE == returnType) {
            return false;
          }
          if (Integer.TYPE == returnType) {
            return 0;
          }
          if (Long.TYPE == returnType) {
            return 0L;
          }
          return null;
        });
  }

  private static class TestDataSource implements DataSource {
    private final boolean failConnection;

    private TestDataSource(boolean failConnection) {
      this.failConnection = failConnection;
    }

    @Override
    public Connection getConnection() throws SQLException {
      if (failConnection) {
        throw new SQLException("connection failed");
      }
      return (Connection) Proxy.newProxyInstance(
          JpaPluginDeploymentVerifierTest.class.getClassLoader(),
          new Class[]{Connection.class},
          (Object proxy, Method method, Object[] args) -> {
            if ("isClosed".equals(method.getName())) {
              return false;
            }
            if ("toString".equals(method.getName())) {
              return "TestConnection";
            }
            if (Void.TYPE == method.getReturnType()) {
              return null;
            }
            if (Boolean.TYPE == method.getReturnType()) {
              return false;
            }
            return null;
          });
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
      return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
      return 0;
    }

    @Override
    public Logger getParentLogger() {
      return Logger.getGlobal();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      throw new SQLException("not used");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
      return false;
    }
  }

  private static class TestTransactionManager implements PlatformTransactionManager {
    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
    }

    @Override
    public void rollback(TransactionStatus status) {
    }
  }
}
