package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.Pf4bootPluginManager;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertTrue;

public class JpaDomainReloadAutoConfigurationTest {

  @Test
  public void bindingRegistryIsExportedToRootContextForPluginStarters() {
    AnnotationConfigApplicationContext rootContext = new AnnotationConfigApplicationContext();
    rootContext.refresh();
    try {
      JpaPluginBindingRegistry registry = new DefaultJpaPluginBindingRegistry();
      Pf4bootPluginManager pluginManager = pluginManager(rootContext);
      JpaDomainReloadAutoConfiguration configuration = new JpaDomainReloadAutoConfiguration();

      SmartInitializingSingleton exporter = configuration.jpaPluginBindingRegistryRootExporter(
          provider(pluginManager),
          registry);
      exporter.afterSingletonsInstantiated();

      assertTrue(rootContext.containsBean(
          JpaDomainReloadAutoConfiguration.JPA_PLUGIN_BINDING_REGISTRY_BEAN_NAME));
      assertTrue(registry == rootContext.getBean(
          JpaDomainReloadAutoConfiguration.JPA_PLUGIN_BINDING_REGISTRY_BEAN_NAME));
    } finally {
      rootContext.close();
    }
  }

  private static ObjectProvider<Pf4bootPluginManager> provider(Pf4bootPluginManager pluginManager) {
    return new ObjectProvider<Pf4bootPluginManager>() {
      @Override
      public Pf4bootPluginManager getObject(Object... args) {
        return pluginManager;
      }

      @Override
      public Pf4bootPluginManager getObject() {
        return pluginManager;
      }

      @Override
      public Pf4bootPluginManager getIfAvailable() {
        return pluginManager;
      }

      @Override
      public Pf4bootPluginManager getIfUnique() {
        return pluginManager;
      }
    };
  }

  private static Pf4bootPluginManager pluginManager(AnnotationConfigApplicationContext rootContext) {
    return (Pf4bootPluginManager) Proxy.newProxyInstance(
        JpaDomainReloadAutoConfigurationTest.class.getClassLoader(),
        new Class[]{Pf4bootPluginManager.class},
        (proxy, method, args) -> {
          if ("getRootContext".equals(method.getName())) {
            return rootContext;
          }
          if ("registerBeanToRootContext".equals(method.getName())) {
            rootContext.getBeanFactory().registerSingleton((String) args[0], args[1]);
            return null;
          }
          if ("toString".equals(method.getName())) {
            return "TestPf4bootPluginManager";
          }
          if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
          }
          if ("equals".equals(method.getName())) {
            return proxy == args[0];
          }
          return null;
        });
  }
}
