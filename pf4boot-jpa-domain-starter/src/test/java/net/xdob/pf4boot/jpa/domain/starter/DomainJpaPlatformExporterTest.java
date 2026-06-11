package net.xdob.pf4boot.jpa.domain.starter;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.jpa.domain.JpaDomainDescriptor;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DomainJpaPlatformExporterTest {

  @Test
  public void exporterRegistersAndUnregistersDomainBeans() {
    RecordingPluginManager recording = new RecordingPluginManager();
    Pf4bootJpaDomainProperties properties = properties();
    DataSource dataSource = new TestDataSource();
    EntityManagerFactory entityManagerFactory = entityManagerFactory();
    PlatformTransactionManager transactionManager = new TestTransactionManager();

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.registerBean(
          PluginApplication.BEAN_PLUGIN,
          Pf4bootPlugin.class,
          () -> new TestPlugin(recording.proxy()));
      context.registerBean(
          DomainJpaPlatformExporter.class,
          () -> new DomainJpaPlatformExporter(
              properties, dataSource, entityManagerFactory, transactionManager));
      context.refresh();

      assertSame(dataSource, recording.registered.get("test-group:domain.order.dataSource"));
      assertSame(entityManagerFactory,
          recording.registered.get("test-group:domain.order.entityManagerFactory"));
      assertSame(transactionManager,
          recording.registered.get("test-group:domain.order.transactionManager"));
      JpaDomainDescriptor descriptor =
          (JpaDomainDescriptor) recording.registered.get("test-group:domain.order.descriptor");
      assertEquals("order", descriptor.getDomainId());
      assertEquals("domain-test", descriptor.getProviderPluginId());
      assertEquals("domain.order.dataSource", descriptor.getDataSourceBeanName());
      assertEquals("domain.order.entityManagerFactory", descriptor.getEntityManagerFactoryBeanName());
      assertEquals("domain.order.transactionManager", descriptor.getTransactionManagerBeanName());
      assertTrue(descriptor.isReady());
    } finally {
      context.close();
    }

    assertEquals(0, recording.registered.size());
    assertEquals("test-group:domain.order.descriptor", recording.unregistered.get(0));
    assertEquals("test-group:domain.order.transactionManager", recording.unregistered.get(1));
    assertEquals("test-group:domain.order.entityManagerFactory", recording.unregistered.get(2));
    assertEquals("test-group:domain.order.dataSource", recording.unregistered.get(3));
  }

  @Test
  public void exporterRollsBackDescriptorAndBeansWhenExportFails() {
    RecordingPluginManager recording = new RecordingPluginManager();
    recording.failOnBeanName = "domain.order.descriptor";
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.registerBean(
          PluginApplication.BEAN_PLUGIN,
          Pf4bootPlugin.class,
          () -> new TestPlugin(recording.proxy()));
      context.registerBean(
          DomainJpaPlatformExporter.class,
          () -> new DomainJpaPlatformExporter(
              properties(), new TestDataSource(), entityManagerFactory(), new TestTransactionManager()));

      context.refresh();
      fail("descriptor export failure should fail provider startup");
    } catch (RuntimeException e) {
      assertTrue(containsMessage(e, "[PJF-004]"));
    } finally {
      context.close();
    }

    assertEquals(0, recording.registered.size());
    assertTrue(recording.unregistered.contains("test-group:domain.order.transactionManager"));
    assertTrue(recording.unregistered.contains("test-group:domain.order.entityManagerFactory"));
    assertTrue(recording.unregistered.contains("test-group:domain.order.dataSource"));
  }

  @Test
  public void exporterRequiresPluginBean() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.registerBean(
          DomainJpaPlatformExporter.class,
          () -> new DomainJpaPlatformExporter(
              properties(), new TestDataSource(), entityManagerFactory(), new TestTransactionManager()));
      context.refresh();
      fail("exporter outside plugin context should fail");
    } catch (RuntimeException e) {
      assertTrue(containsMessage(e, "[PJF-004]"));
    } finally {
      context.close();
    }
  }

  @Test
  public void domainStarterCreatesEntityManagerFactoryAndExportsBeans() {
    RecordingPluginManager recording = new RecordingPluginManager();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      addProperties(context,
          "pf4boot.plugin.jpa.domain.id", "sample",
          "pf4boot.plugin.jpa.domain.entity-packages[0]",
          "net.xdob.pf4boot.jpa.domain.starter.model",
          "pf4boot.plugin.jpa.domain.datasource.url",
          "jdbc:h2:mem:pf4boot_domain_test;DB_CLOSE_DELAY=-1",
          "pf4boot.plugin.jpa.domain.datasource.username", "sa",
          "pf4boot.plugin.jpa.domain.datasource.driver-class-name", "org.h2.Driver",
          "pf4boot.plugin.jpa.domain.ddl-auto", "create-drop",
          "spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
      context.registerBean(
          PluginApplication.BEAN_PLUGIN,
          Pf4bootPlugin.class,
          () -> new TestPlugin(recording.proxy()));
      context.register(Pf4bootJpaDomainStarter.class);
      context.refresh();

      EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);
      entityManagerFactory.getMetamodel()
          .entity(net.xdob.pf4boot.jpa.domain.starter.model.DomainSampleEntity.class);

      assertTrue(recording.registered.containsKey("test-group:domain.sample.dataSource"));
      assertSame(entityManagerFactory,
          recording.registered.get("test-group:domain.sample.entityManagerFactory"));
      assertTrue(recording.registered.containsKey("test-group:domain.sample.transactionManager"));
      assertTrue(recording.registered.containsKey("test-group:domain.sample.descriptor"));
    } finally {
      context.close();
    }
  }

  private static Pf4bootJpaDomainProperties properties() {
    Pf4bootJpaDomainProperties properties = new Pf4bootJpaDomainProperties();
    properties.setId("order");
    properties.setEntityPackages(
        Collections.singletonList("net.xdob.pf4boot.jpa.domain.starter.model"));
    return properties;
  }

  private static boolean containsMessage(Throwable throwable, String text) {
    Throwable current = throwable;
    while (current != null) {
      if (current.getMessage() != null && current.getMessage().contains(text)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static void addProperties(AnnotationConfigApplicationContext context, String... entries) {
    Map<String, Object> properties = new HashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      properties.put(entries[i], entries[i + 1]);
    }
    context.getEnvironment().getPropertySources()
        .addFirst(new MapPropertySource("test", properties));
  }

  private static EntityManagerFactory entityManagerFactory() {
    return (EntityManagerFactory) Proxy.newProxyInstance(
        DomainJpaPlatformExporterTest.class.getClassLoader(),
        new Class[]{EntityManagerFactory.class},
        DomainJpaPlatformExporterTest::defaultInvocation);
  }

  private static Object defaultInvocation(Object proxy, Method method, Object[] args) {
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
      return true;
    }
    if (Integer.TYPE == returnType) {
      return 0;
    }
    if (Long.TYPE == returnType) {
      return 0L;
    }
    return null;
  }

  private static class RecordingPluginManager {
    private final Map<String, Object> registered = new LinkedHashMap<>();
    private final List<String> unregistered = new ArrayList<>();
    private String failOnBeanName;

    Pf4bootPluginManager proxy() {
      InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
        String name = method.getName();
        if ("registerBeanToPlatformContext".equals(name)) {
          if (args[1].equals(this.failOnBeanName)) {
            throw new IllegalStateException("export failed: " + args[1]);
          }
          this.registered.put(args[0] + ":" + args[1], args[2]);
          return null;
        }
        if ("unregisterBeanFromPlatformContext".equals(name)) {
          String key = args[0] + ":" + args[1];
          this.unregistered.add(key);
          this.registered.remove(key);
          return null;
        }
        if ("toString".equals(name)) {
          return "RecordingPluginManager";
        }
        return defaultValue(method.getReturnType());
      };
      return (Pf4bootPluginManager) Proxy.newProxyInstance(
          DomainJpaPlatformExporterTest.class.getClassLoader(),
          new Class[]{Pf4bootPluginManager.class},
          handler);
    }

    private Object defaultValue(Class<?> returnType) {
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
    }
  }

  private static class TestPlugin extends Pf4bootPlugin {
    private final Pf4bootPluginManager pluginManager;

    TestPlugin(Pf4bootPluginManager pluginManager) {
      super(new PluginWrapper(
          new DefaultPluginManager(Paths.get("domain-test-root")),
          new DefaultPluginDescriptor(
              "domain-test",
              "domain-test",
              TestPlugin.class.getName(),
              "1.0.0",
              "",
              "test",
              "Apache-2.0"),
          Paths.get("domain-test"),
          TestPlugin.class.getClassLoader()));
      this.pluginManager = pluginManager;
    }

    @Override
    public Pf4bootPluginManager getPluginManager() {
      return this.pluginManager;
    }

    @Override
    public String getGroup() {
      return "test-group";
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

  private static class TestDataSource implements DataSource {
    @Override
    public Connection getConnection() throws SQLException {
      throw new SQLException("not used");
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      throw new SQLException("not used");
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
}
