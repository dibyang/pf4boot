package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginWrapper;
import net.xdob.pf4boot.Pf4bootPluginManager;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class SpringExtensionFactoryTest {

  @Test
  public void returnsBeanFromPluginContextWhenExists() throws Exception {
    ConfigurableApplicationContext pluginContext = new AnnotationConfigApplicationContext();
    ExistingExtension expected = new ExistingExtension();
    pluginContext.getBeanFactory().registerSingleton(ExistingExtension.class.getName(), expected);
    pluginContext.refresh();

    SpringExtensionFactory extensionFactory = new SpringExtensionFactory(
        pluginManagerWithContext(pluginContext));

    Object extension = extensionFactory.create(ExistingExtension.class);
    assertSame(expected, extension);
    pluginContext.close();
  }

  @Test
  public void createsAndRegistersBeanWhenNoContextBeanExists() throws Exception {
    ConfigurableApplicationContext pluginContext = new AnnotationConfigApplicationContext();
    pluginContext.refresh();

    SpringExtensionFactory extensionFactory = new SpringExtensionFactory(
        pluginManagerWithContext(pluginContext));

    MissingExtension extension = extensionFactory.create(MissingExtension.class);
    Object bean = pluginContext.getBean(MissingExtension.class.getName());

    assertNotNull(extension);
    assertSame(extension, bean);
    pluginContext.close();
  }

  @Test
  public void returnsRegisteredExtensionBeanName() throws Exception {
    ConfigurableApplicationContext pluginContext = new AnnotationConfigApplicationContext();
    pluginContext.getBeanFactory().registerSingleton(MissingExtension.class.getName(), new MissingExtension());
    pluginContext.refresh();

    SpringExtensionFactory extensionFactory = new SpringExtensionFactory(
        pluginManagerWithContext(pluginContext));

    assertSame(MissingExtension.class.getName(),
        extensionFactory.getExtensionBeanName(MissingExtension.class));
    pluginContext.close();
  }

  private static Pf4bootPluginManager pluginManagerWithContext(ConfigurableApplicationContext pluginContext) throws Exception {
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        "ext-plugin", "ext-plugin", MissingExtension.class.getName(), "1.0.0", "", "test", "Apache-2.0");
    Path pluginPath = Paths.get("target/ext-plugin");
    Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(
        new DefaultPluginManager(), descriptor, pluginPath, MissingExtension.class.getClassLoader());
    TestPlugin plugin = new TestPlugin(wrapper, pluginContext);
    injectPlugin(wrapper, plugin);
    return (Pf4bootPluginManager) Proxy.newProxyInstance(
        SpringExtensionFactoryTest.class.getClassLoader(),
        new Class[]{Pf4bootPluginManager.class},
        (proxy, method, args) -> {
          if ("whichPlugin".equals(method.getName()) && args != null && args.length == 1) {
            return wrapper;
          }
          if (Object.class.equals(method.getDeclaringClass())) {
            switch (method.getName()) {
              case "toString":
                return "plugin-manager";
              case "hashCode":
                return System.identityHashCode(proxy);
              case "equals":
                return proxy == args[0];
            }
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }

  private static void injectPlugin(PluginWrapper wrapper, Plugin plugin) throws Exception {
    try {
      Method setter = PluginWrapper.class.getDeclaredMethod("setPlugin", Plugin.class);
      setter.setAccessible(true);
      setter.invoke(wrapper, plugin);
      return;
    } catch (NoSuchMethodException ignore) {
      // compatible fallback
    }
    Field field = PluginWrapper.class.getDeclaredField("plugin");
    field.setAccessible(true);
    field.set(wrapper, plugin);
  }

  public static class ExistingExtension {
  }

  public static class MissingExtension {
  }

  public static class TestPlugin extends Pf4bootPlugin {
    private final ConfigurableApplicationContext pluginContext;

    public TestPlugin(PluginWrapper wrapper, ConfigurableApplicationContext pluginContext) {
      super(wrapper);
      this.pluginContext = pluginContext;
    }

    @Override
    public ConfigurableApplicationContext getPluginContext() {
      return pluginContext;
    }
  }

}
