package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.*;
import org.junit.Test;
import org.pf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.nio.file.Files;
import static org.junit.Assert.*;

/** 手工创建的 PF4J 扩展归属插件上下文，销毁失败沿用现有重试。 */
public class SpringExtensionCleanupTest {
  public static class Extension implements DisposableBean {
    final ServerSocket socket;
    int annotations;
    int destroys;
    int initialized;
    boolean fail;
    public Extension() throws Exception { socket = new ServerSocket(0); }
    @PostConstruct public void initialize() { initialized++; }
    @PreDestroy public void annotationDestroy() { annotations++; }
    public void destroy() throws Exception {
      destroys++;
      if (fail) { throw new IllegalStateException("extension close failure"); }
      socket.close();
    }
  }
  private static class ContextPlugin extends Pf4bootPlugin {
    ContextPlugin(PluginWrapper wrapper, AnnotationConfigApplicationContext context) {
      super(wrapper); pluginContext = context;
    }
  }
  private static class Fixture implements AutoCloseable {
    final PluginListableBeanFactory factory = new PluginListableBeanFactory(getClass().getClassLoader());
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(factory);
    final SpringExtensionFactory extensions;
    Fixture() throws Exception {
      context.refresh();
      PluginWrapper wrapper = new PluginWrapper(new DefaultPluginManager(),
          new DefaultPluginDescriptor("extension", "extension", ContextPlugin.class.getName(), "1", "", "test", "Apache-2.0"),
          Files.createTempDirectory("pf4boot-extension"), getClass().getClassLoader());
      ContextPlugin plugin = new ContextPlugin(wrapper, context);
      wrapper.setPluginFactory(w -> plugin);
      Pf4bootPluginManager manager = (Pf4bootPluginManager) Proxy.newProxyInstance(getClass().getClassLoader(),
          new Class<?>[]{Pf4bootPluginManager.class}, (proxy, method, args) -> {
            if (method.getName().equals("whichPlugin")) { return wrapper; }
            throw new UnsupportedOperationException(method.getName());
          });
      extensions = new SpringExtensionFactory(manager);
    }
    public void close() { context.close(); factory.retryDestruction(); }
  }
  @Test public void fallbackExtensionClosesOnceWithoutNewInitializationCallbacks() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Extension bean = fixture.extensions.create(Extension.class);
      try {
        assertSame(bean, fixture.extensions.create(Extension.class));
        assertEquals(0, bean.initialized);
        fixture.context.close(); fixture.factory.verifyDestruction();
        assertEquals(1, bean.annotations); assertEquals(1, bean.destroys);
        try (ServerSocket port = new ServerSocket(bean.socket.getLocalPort())) { assertTrue(port.isBound()); }
      } finally { bean.socket.close(); }
    }
  }
  @Test public void extensionFailureIsReportedAndOnlyFailedActionRetried() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Extension bean = fixture.extensions.create(Extension.class);
      try {
        bean.fail = true; fixture.context.close();
        try { fixture.factory.verifyDestruction(); fail("missing destruction failure"); }
        catch (PluginRuntimeException expected) { assertFalse(bean.socket.isClosed()); }
        bean.fail = false; fixture.factory.retryDestruction(); fixture.factory.verifyDestruction();
        assertEquals(1, bean.annotations); assertEquals(2, bean.destroys); assertTrue(bean.socket.isClosed());
      } finally { bean.fail = false; bean.socket.close(); }
    }
  }
  @Test public void springManagedExtensionDoesNotGainDuplicateDestruction() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.factory.registerBeanDefinition("managed", new org.springframework.beans.factory.support.RootBeanDefinition(Extension.class));
      Extension bean = fixture.extensions.create(Extension.class);
      try {
        fixture.context.close(); fixture.factory.verifyDestruction();
        assertEquals(1, bean.destroys); assertEquals(1, bean.annotations);
      } finally { bean.socket.close(); }
    }
  }
}
