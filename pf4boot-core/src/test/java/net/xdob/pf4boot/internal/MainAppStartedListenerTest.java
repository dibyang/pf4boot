package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.Pf4bootPluginWrapper;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.RuntimeMode;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class MainAppStartedListenerTest {

  @Test
  public void startsPluginsWhenApplicationStartedAndAutoStartEnabled() throws Exception {
    AtomicBoolean applicationStarted = new AtomicBoolean(false);
    AtomicBoolean autoStart = new AtomicBoolean(true);
    AtomicInteger startPluginCalls = new AtomicInteger(0);
    Pf4bootPluginManager pluginManager = createManager(applicationStarted, autoStart, startPluginCalls);

    MainAppStartedListener listener = new MainAppStartedListener();
    injectPluginManager(listener, pluginManager);

    ApplicationStartedEvent event = createApplicationStartedEvent(null);
    listener.onApplicationEvent(event);

    assertEquals(1, startPluginCalls.get());
    assertEquals(true, applicationStarted.get());
  }

  @Test
  public void doesNotStartPluginsWhenAlreadyStarted() throws Exception {
    AtomicBoolean applicationStarted = new AtomicBoolean(true);
    AtomicBoolean autoStart = new AtomicBoolean(true);
    AtomicInteger startPluginCalls = new AtomicInteger(0);
    Pf4bootPluginManager pluginManager = createManager(applicationStarted, autoStart, startPluginCalls);

    MainAppStartedListener listener = new MainAppStartedListener();
    injectPluginManager(listener, pluginManager);

    ApplicationStartedEvent event = createApplicationStartedEvent(null);
    listener.onApplicationEvent(event);

    assertEquals(0, startPluginCalls.get());
    assertEquals(true, applicationStarted.get());
  }

  @Test
  public void doesNotStartPluginsWhenAutoStartDisabledButMarkStarted() throws Exception {
    AtomicBoolean applicationStarted = new AtomicBoolean(false);
    AtomicBoolean autoStart = new AtomicBoolean(false);
    AtomicInteger startPluginCalls = new AtomicInteger(0);
    Pf4bootPluginManager pluginManager = createManager(applicationStarted, autoStart, startPluginCalls);

    MainAppStartedListener listener = new MainAppStartedListener();
    injectPluginManager(listener, pluginManager);

    ApplicationStartedEvent event = createApplicationStartedEvent(new PluginApplicationSource());
    listener.onApplicationEvent(event);

    assertEquals(0, startPluginCalls.get());
    assertEquals(false, applicationStarted.get());
  }

  private static ApplicationStartedEvent createApplicationStartedEvent(Object source) throws Exception {
    ConfigurableApplicationContext context = new AnnotationConfigApplicationContext();
    if (source == null) {
      return new ApplicationStartedEvent(new SpringApplication(), new String[0], context);
    }
    return new TestApplicationStartedEvent(source, context);
  }

  private static final class TestApplicationStartedEvent extends ApplicationStartedEvent {
    private final Object source;

    private TestApplicationStartedEvent(Object source, ConfigurableApplicationContext context) {
      super(new SpringApplication(), new String[0], context);
      this.source = source;
    }

    @Override
    public Object getSource() {
      return source;
    }
  }

  private static Pf4bootPluginManager createManager(
      AtomicBoolean applicationStarted,
      AtomicBoolean autoStart,
      AtomicInteger startPluginCalls) {
    InvocationHandler invocationHandler = new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("isApplicationStarted".equals(method.getName())) {
          return applicationStarted.get();
        }
        if ("setApplicationStarted".equals(method.getName())) {
          applicationStarted.set((Boolean) args[0]);
          return null;
        }
        if ("isAutoStartPlugin".equals(method.getName())) {
          return autoStart.get();
        }
        if ("startPlugins".equals(method.getName())) {
          startPluginCalls.incrementAndGet();
          return null;
        }
        if (Object.class.equals(method.getDeclaringClass())) {
          switch (method.getName()) {
            case "toString":
              return "pluginManager";
            case "hashCode":
              return System.identityHashCode(proxy);
            case "equals":
              return proxy == args[0];
          }
        }
        return null;
      }
    };
    return (Pf4bootPluginManager) Proxy.newProxyInstance(
        MainAppStartedListenerTest.class.getClassLoader(),
        new Class[]{Pf4bootPluginManager.class},
        invocationHandler);
  }

  private static void injectPluginManager(MainAppStartedListener listener, Pf4bootPluginManager pluginManager)
      throws Exception {
    java.lang.reflect.Field field = MainAppStartedListener.class.getDeclaredField("pluginManager");
    field.setAccessible(true);
    field.set(listener, pluginManager);
  }

  public static class PluginApplicationSource implements net.xdob.pf4boot.PluginApplication {
    private final Pf4bootPlugin plugin;

    public PluginApplicationSource() throws Exception {
      Path pluginPath = Files.createTempFile("app-source-plugin", ".jar");
      this.plugin = new Pf4bootPlugin(new Pf4bootPluginWrapper(
          (PluginManager) Proxy.newProxyInstance(
              MainAppStartedListenerTest.class.getClassLoader(),
              new Class[]{PluginManager.class},
              (proxy, method, args) -> RuntimeMode.DEPLOYMENT),
          new DefaultPluginDescriptor(
              "app-source-plugin",
              "app-source-plugin",
              "net.xdob.pf4boot.internal.MainAppStartedListenerTest.PluginApplicationSource",
              "1.0.0",
              "",
              "test",
              "Apache-2.0"),
          pluginPath,
          PluginApplicationSource.class.getClassLoader()));
    }

    @Override
    public Pf4bootPlugin getPlugin() {
      return plugin;
    }
  }
}
