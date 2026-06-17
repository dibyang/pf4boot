package net.xdob.pf4boot.jpa.starter;

import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.jpa.binding.JpaConsumerBinding;
import net.xdob.pf4boot.jpa.binding.JpaConsumerBindingProvider;
import net.xdob.pf4boot.jpa.domain.JpaDomainDescriptor;
import net.xdob.pf4boot.jpa.starter.reload.DefaultJpaPluginBindingRegistry;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.jpa.util.BeanDefinitionUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PluginJPAStarterTest {

  @Test
  public void explicitJpaStarterDoesNotRequireEnabledPropertyForSharedMode() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      registerDomain(parent, "order");
      parent.refresh();

      context.setParent(parent);
      addProperties(context,
          "pf4boot.plugin.jpa.mode", "SHARED",
          "pf4boot.plugin.jpa.domain-id", "order");
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertEquals(1, context.getBeanNamesForType(PluginJPAStarter.class).length);
      assertTrue(context.getBean("domain.order.entityManagerFactory")
          == parent.getBean("domain.order.entityManagerFactory"));
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void pluginMainClassPackageIsUsedAsFallbackScanPackage() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      context.refresh();

      ExposedPluginJPAStarter starter = new ExposedPluginJPAStarter();
      starter.setBeanFactory(context.getBeanFactory());

      assertArrayEquals(
          new String[]{TestPlugin.class.getPackage().getName()},
          starter.exposedPackagesToScan());
    } finally {
      context.close();
    }
  }

  @Test
  public void defaultDdlAutoIsNoneForPluginJpa() {
    HibernateDefaultDdlAutoProvider provider =
        new HibernateDefaultDdlAutoProvider(Collections.emptyList());

    assertEquals("none", provider.getDefaultDdlAuto(new TestDataSource()));
  }

  @Test
  public void sharedModeRequiresDomainId() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      addProperties(context,
          "pf4boot.plugin.jpa.mode", "SHARED");
      context.register(PluginJPAStarter.class);

      context.refresh();
      fail("shared mode without domain-id should fail");
    } catch (RuntimeException e) {
      assertTrue(rootCause(e).getMessage().contains("[PJF-006]"));
    } finally {
      context.close();
    }
  }

  @Test
  public void sharedModeBindsParentDomainBeansWithoutLocalJpaBeans() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      parent.getBeanFactory().registerSingleton(
          "domain.order.entityManagerFactory", entityManagerFactory());
      parent.getBeanFactory().registerSingleton(
          "domain.order.transactionManager", new TestTransactionManager());
      parent.getBeanFactory().registerSingleton(
          "domain.order.descriptor", domainDescriptor("order", true));
      parent.refresh();

      context.setParent(parent);
      addProperties(context,
          "pf4boot.plugin.jpa.mode", "SHARED",
          "pf4boot.plugin.jpa.domain-id", "order");
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertEquals(1, context.getBeanNamesForType(PluginJPAStarter.class).length);
      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.order.entityManagerFactory"));
      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.order.transactionManager"));
      assertTrue(context.getBean("domain.order.entityManagerFactory")
          == parent.getBean("domain.order.entityManagerFactory"));
      assertTrue(context.getBean("domain.order.transactionManager")
          == parent.getBean("domain.order.transactionManager"));
      assertEquals(0, context.getDefaultListableBeanFactory()
          .getBeanNamesForType(LocalContainerEntityManagerFactoryBean.class).length);
      assertEquals(0, context.getDefaultListableBeanFactory()
          .getBeanNamesForType(JpaTransactionManager.class).length);
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void pluginLevelBindingOverridesGlobalLocalMode() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      parent.getBeanFactory().registerSingleton(
          "domain.invoice.entityManagerFactory", entityManagerFactory());
      parent.getBeanFactory().registerSingleton(
          "domain.invoice.transactionManager", new TestTransactionManager());
      parent.getBeanFactory().registerSingleton(
          "domain.invoice.descriptor", domainDescriptor("invoice", true));
      parent.refresh();

      context.setParent(parent);
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      addProperties(context,
          "pf4boot.plugin.jpa.mode", "LOCAL",
          "pf4boot.plugin.jpa.plugins.jpa-test.mode", "SHARED",
          "pf4boot.plugin.jpa.plugins.jpa-test.domain-id", "invoice");
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.invoice.entityManagerFactory"));
      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.invoice.transactionManager"));
      assertTrue(context.getBean("domain.invoice.entityManagerFactory")
          == parent.getBean("domain.invoice.entityManagerFactory"));
      assertEquals(0, context.getDefaultListableBeanFactory()
          .getBeanNamesForType(LocalContainerEntityManagerFactoryBean.class).length);
      assertEquals(0, context.getDefaultListableBeanFactory()
          .getBeanNamesForType(JpaTransactionManager.class).length);
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void consumerBindingProviderOverridesLegacyConfiguration() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      registerDomain(parent, "invoice");
      parent.refresh();

      context.setParent(parent);
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, SharedBindingPlugin::new);
      addProperties(context,
          "pf4boot.plugin.jpa.mode", "LOCAL",
          "pf4boot.plugin.jpa.plugins.jpa-test.mode", "LOCAL");
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.invoice.entityManagerFactory"));
      assertTrue(context.getBean("domain.invoice.entityManagerFactory")
          == parent.getBean("domain.invoice.entityManagerFactory"));
      assertEquals(0, context.getDefaultListableBeanFactory()
          .getBeanNamesForType(LocalContainerEntityManagerFactoryBean.class).length);
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void pluginLevelBindingCanUseCustomDescriptorRef() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      parent.getBeanFactory().registerSingleton(
          "domain.invoice.entityManagerFactory", entityManagerFactory());
      parent.getBeanFactory().registerSingleton(
          "domain.invoice.transactionManager", new TestTransactionManager());
      parent.getBeanFactory().registerSingleton(
          "custom.invoice.descriptor", domainDescriptor("invoice", true));
      parent.refresh();

      context.setParent(parent);
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      addProperties(context,
          "pf4boot.plugin.jpa.plugins.jpa-test.mode", "SHARED",
          "pf4boot.plugin.jpa.plugins.jpa-test.domain-id", "invoice",
          "pf4boot.plugin.jpa.plugins.jpa-test.descriptor-ref", "custom.invoice.descriptor");
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertTrue(context.getBean("domain.invoice.entityManagerFactory")
          == parent.getBean("domain.invoice.entityManagerFactory"));
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void pluginLevelBindingRegistersAdditionalSharedDomains() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      registerDomain(parent, "order");
      registerDomain(parent, "audit");
      parent.refresh();

      context.setParent(parent);
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      addProperties(context,
          "pf4boot.plugin.jpa.plugins.jpa-test.mode", "SHARED",
          "pf4boot.plugin.jpa.plugins.jpa-test.domain-id", "order",
          "pf4boot.plugin.jpa.plugins.jpa-test.additional-domains[0].domain-id", "audit");
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.order.entityManagerFactory"));
      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.order.transactionManager"));
      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.audit.entityManagerFactory"));
      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.audit.transactionManager"));
      assertTrue(context.getBean("domain.audit.entityManagerFactory")
          == parent.getBean("domain.audit.entityManagerFactory"));
      assertEquals(0, context.getDefaultListableBeanFactory()
          .getBeanNamesForType(LocalContainerEntityManagerFactoryBean.class).length);
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void pluginLevelBindingRequiresReadyAdditionalDomainDescriptor() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      registerDomain(parent, "order");
      parent.getBeanFactory().registerSingleton(
          "domain.audit.entityManagerFactory", entityManagerFactory());
      parent.getBeanFactory().registerSingleton(
          "domain.audit.transactionManager", new TestTransactionManager());
      parent.refresh();

      context.setParent(parent);
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      addProperties(context,
          "pf4boot.plugin.jpa.plugins.jpa-test.mode", "SHARED",
          "pf4boot.plugin.jpa.plugins.jpa-test.domain-id", "order",
          "pf4boot.plugin.jpa.plugins.jpa-test.additional-domains[0].domain-id", "audit");
      context.register(PluginJPAStarter.class);

      context.refresh();
      fail("additional shared domain without descriptor should fail");
    } catch (RuntimeException e) {
      assertTrue(containsMessage(e, "[PJF-007]"));
      assertTrue(containsMessage(e, "audit"));
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void pluginLevelSharedBindingRequiresDomainId() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      addProperties(context,
          "pf4boot.plugin.jpa.plugins.jpa-test.mode", "SHARED");
      context.register(PluginJPAStarter.class);

      context.refresh();
      fail("plugin-level shared binding without domain-id should fail");
    } catch (RuntimeException e) {
      assertTrue(rootCause(e).getMessage().contains("[PJF-006]"));
      assertTrue(rootCause(e).getMessage().contains("jpa-test"));
    } finally {
      context.close();
    }
  }

  @Test
  public void oldSharedConfigurationRemainsFallbackWhenPluginBindingIsMissing() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      parent.getBeanFactory().registerSingleton(
          "domain.order.entityManagerFactory", entityManagerFactory());
      parent.getBeanFactory().registerSingleton(
          "domain.order.transactionManager", new TestTransactionManager());
      parent.getBeanFactory().registerSingleton(
          "domain.order.descriptor", domainDescriptor("order", true));
      parent.refresh();

      context.setParent(parent);
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      addProperties(context,
          "pf4boot.plugin.jpa.mode", "SHARED",
          "pf4boot.plugin.jpa.domain-id", "order",
          "pf4boot.plugin.jpa.plugins.other-plugin.mode", "LOCAL");
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.order.entityManagerFactory"));
      assertTrue(context.getBean("domain.order.transactionManager")
          == parent.getBean("domain.order.transactionManager"));
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void sharedModeWithParentBeanFactoryDomainBeanDefinitionsIsVisibleToSpringDataJpa() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      parent.refresh();
      registerDynamicSingleton(
          parent.getDefaultListableBeanFactory(),
          "domain.order.entityManagerFactory",
          entityManagerFactory());
      registerDynamicSingleton(
          parent.getDefaultListableBeanFactory(),
          "domain.order.transactionManager",
          new TestTransactionManager());
      registerDynamicSingleton(
          parent.getDefaultListableBeanFactory(),
          "domain.order.descriptor",
          domainDescriptor("order", true));

      context.getDefaultListableBeanFactory()
          .setParentBeanFactory(parent.getDefaultListableBeanFactory());
      addProperties(context,
          "pf4boot.plugin.jpa.mode", "SHARED",
          "pf4boot.plugin.jpa.domain-id", "order");
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.order.entityManagerFactory"));
      assertTrue(context.getDefaultListableBeanFactory()
          .containsBeanDefinition("domain.order.transactionManager"));

      Collection<BeanDefinitionUtils.EntityManagerFactoryBeanDefinition> definitions =
          BeanDefinitionUtils.getEntityManagerFactoryBeanDefinitions(context.getDefaultListableBeanFactory());

      assertTrue(definitions.stream()
          .anyMatch(definition -> "domain.order.entityManagerFactory".equals(definition.getBeanName())));
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void sharedModeRegistersBindingInParentBeanFactoryRegistry() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      parent.refresh();
      registerDynamicSingleton(
          parent.getDefaultListableBeanFactory(),
          "domain.order.entityManagerFactory",
          entityManagerFactory());
      registerDynamicSingleton(
          parent.getDefaultListableBeanFactory(),
          "domain.order.transactionManager",
          new TestTransactionManager());
      registerDynamicSingleton(
          parent.getDefaultListableBeanFactory(),
          "domain.order.descriptor",
          domainDescriptor("order", true));
      DefaultJpaPluginBindingRegistry registry = new DefaultJpaPluginBindingRegistry();
      registerDynamicSingleton(
          parent.getDefaultListableBeanFactory(),
          "jpaPluginBindingRegistry",
          registry);

      context.getDefaultListableBeanFactory()
          .setParentBeanFactory(parent.getDefaultListableBeanFactory());
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      addProperties(context,
          "pf4boot.plugin.jpa.plugins.jpa-test.mode", "SHARED",
          "pf4boot.plugin.jpa.plugins.jpa-test.domain-id", "order");
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertEquals("order", registry.findByPluginId("jpa-test").getDomainId());
      assertEquals(1, registry.findByDomainId("order").size());
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void sharedModeRequiresReadyDomainDescriptor() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      parent.getBeanFactory().registerSingleton(
          "domain.order.entityManagerFactory", entityManagerFactory());
      parent.getBeanFactory().registerSingleton(
          "domain.order.transactionManager", new TestTransactionManager());
      parent.refresh();

      context.setParent(parent);
      addProperties(context,
          "pf4boot.plugin.jpa.mode", "SHARED",
          "pf4boot.plugin.jpa.domain-id", "order");
      context.register(PluginJPAStarter.class);

      context.refresh();
      fail("shared mode without descriptor should fail");
    } catch (RuntimeException e) {
      assertTrue(containsMessage(e, "[PJF-007]"));
    } finally {
      context.close();
      parent.close();
    }
  }

  @Test
  public void providerMissingFailureDoesNotAffectUnrelatedNonJpaPluginContext() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext sharedConsumer = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext unrelated = new AnnotationConfigApplicationContext();
    try {
      parent.refresh();

      sharedConsumer.setParent(parent);
      sharedConsumer.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      addProperties(sharedConsumer,
          "pf4boot.plugin.jpa.mode", "SHARED",
          "pf4boot.plugin.jpa.domain-id", "missing");
      sharedConsumer.register(PluginJPAStarter.class);

      try {
        sharedConsumer.refresh();
        fail("shared consumer without provider should fail");
      } catch (RuntimeException e) {
        assertTrue(containsMessage(e, "[PJF-007]"));
        assertTrue(containsMessage(e, "missing"));
      }

      unrelated.setParent(parent);
      unrelated.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      unrelated.refresh();

      assertEquals(0, unrelated.getBeanNamesForType(PluginJPAStarter.class).length);
    } finally {
      sharedConsumer.close();
      unrelated.close();
      parent.close();
    }
  }

  @Test
  public void sharedModeRejectsNotReadyDomainDescriptor() {
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      parent.getBeanFactory().registerSingleton(
          "domain.order.entityManagerFactory", entityManagerFactory());
      parent.getBeanFactory().registerSingleton(
          "domain.order.transactionManager", new TestTransactionManager());
      parent.getBeanFactory().registerSingleton(
          "domain.order.descriptor", domainDescriptor("order", false));
      parent.refresh();

      context.setParent(parent);
      addProperties(context,
          "pf4boot.plugin.jpa.mode", "SHARED",
          "pf4boot.plugin.jpa.domain-id", "order");
      context.register(PluginJPAStarter.class);

      context.refresh();
      fail("shared mode with not-ready descriptor should fail");
    } catch (RuntimeException e) {
      assertTrue(containsMessage(e, "[PJF-007]"));
      assertTrue(containsMessage(e, "not ready"));
    } finally {
      context.close();
      parent.close();
    }
  }

  private static class ExposedPluginJPAStarter extends PluginJPAStarter {
    ExposedPluginJPAStarter() {
      super(new JpaProperties(), new HibernateProperties(), Collections.emptyList(), new Pf4bootJpaProperties());
    }

    String[] exposedPackagesToScan() {
      return getPackagesToScan();
    }
  }

  public static class TestPlugin extends Plugin {
    public TestPlugin() {
      super(new PluginWrapper(
          new DefaultPluginManager(Paths.get("jpa-test-root")),
          new DefaultPluginDescriptor(
              "jpa-test", "jpa-test", TestPlugin.class.getName(), "1.0.0", "", "test", "Apache-2.0"),
          Paths.get("jpa-test"),
          TestPlugin.class.getClassLoader()));
    }
  }

  public static class SharedBindingPlugin extends TestPlugin implements JpaConsumerBindingProvider {
    @Override
    public JpaConsumerBinding jpaConsumerBinding() {
      return JpaConsumerBinding.shared("invoice").build();
    }
  }

  private static void addProperties(AnnotationConfigApplicationContext context, String... entries) {
    Map<String, Object> properties = new HashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      properties.put(entries[i], entries[i + 1]);
    }
    context.getEnvironment().getPropertySources()
        .addFirst(new MapPropertySource("test", properties));
  }

  private static Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
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

  private static EntityManagerFactory entityManagerFactory() {
    return (EntityManagerFactory) Proxy.newProxyInstance(
        PluginJPAStarterTest.class.getClassLoader(),
        new Class[]{EntityManagerFactory.class},
        PluginJPAStarterTest::defaultInvocation);
  }

  private static JpaDomainDescriptor domainDescriptor(String domainId, boolean ready) {
    return new JpaDomainDescriptor(
        domainId,
        "provider-" + domainId,
        new String[]{"net.xdob.test." + domainId},
        "domain." + domainId + ".dataSource",
        "domain." + domainId + ".entityManagerFactory",
        "domain." + domainId + ".transactionManager",
        ready,
        1L);
  }

  private static void registerDomain(AnnotationConfigApplicationContext context, String domainId) {
    context.getBeanFactory().registerSingleton(
        "domain." + domainId + ".entityManagerFactory", entityManagerFactory());
    context.getBeanFactory().registerSingleton(
        "domain." + domainId + ".transactionManager", new TestTransactionManager());
    context.getBeanFactory().registerSingleton(
        "domain." + domainId + ".descriptor", domainDescriptor(domainId, true));
  }

  private static void registerDynamicSingleton(
      DefaultListableBeanFactory beanFactory,
      String beanName,
      Object bean) {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(bean.getClass());
    beanDefinition.setInstanceSupplier(() -> bean);
    beanFactory.registerBeanDefinition(beanName, beanDefinition);
    beanFactory.registerSingleton(beanName, bean);
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
