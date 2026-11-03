package net.xdob.pf4boot.audit;

import net.xdob.pf4boot.*;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.*;
import org.springframework.mock.web.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.PluginRequestMappingHandlerMapping;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

// 回归直接插件销毁去重及跨插件分派的停止保护。
public class RestartBoundaryAuditTest {
  private static int destroys;
  public static class EmptyConfig { }
  @PluginStarter(EmptyConfig.class)
  public static class DirectPlugin extends Pf4bootPlugin implements DisposableBean {
    ServerSocket socket;
    public DirectPlugin(PluginWrapper wrapper) { super(wrapper); }
    public void start() { try { socket = new ServerSocket(0); } catch (Exception e) { throw new IllegalStateException(e); } }
    @javax.annotation.PreDestroy public void destroy() throws Exception {
      destroys++;
      socket.close();
      socket = null;
    }
  }
  private static class Manager extends Pf4bootPluginManagerImpl {
    Manager(AnnotationConfigApplicationContext host, Path root) {
      super(host, new Pf4bootProperties(), new Pf4bootPluginSupport() {},
          new DefaultShareBeanMgr(new DefaultAutoExportMgr()), root);
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor("direct", "direct",
          DirectPlugin.class.getName(), "1.0", "", "test", "Apache-2.0");
      ClassLoader loader = new URLClassLoader(new URL[0], getClass().getClassLoader());
      Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(this, descriptor, root.resolve("direct"), loader);
      wrapper.setPluginFactory(getPluginFactory()); wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put("direct", wrapper); resolvedPlugins.add(wrapper); pluginClassLoaders.put("direct", loader);
      dependencyResolver = new DependencyResolver(getVersionManager());
      dependencyResolver.resolve(Collections.singletonList(descriptor));
    }
  }
  @Test public void directPluginDestroyMethodMustOnlyRunOnce() throws Exception {
    destroys = 0;
    AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
    host.registerBean("metadataReaderFactory", MetadataReaderFactory.class,
        () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader())); host.refresh();
    Manager manager = new Manager(host, Files.createTempDirectory("pf4boot-direct-destroy-audit"));
    try {
      assertEquals(PluginState.STARTED, manager.startPlugin("direct"));
      try { manager.stopPlugin("direct"); } catch (PluginRuntimeException expected) { }
      PluginState stopped = manager.getPlugin("direct").getPluginState();
      int callsAtStop = destroys;
      PluginState restarted = manager.startPlugin("direct");
      System.out.println("AUDIT direct plugin: destroyCalls=" + callsAtStop + ", stop=" + stopped + ", restart=" + restarted);
      assertEquals("a single close must not be repeated", 1, callsAtStop);
      assertEquals(PluginState.STOPPED, stopped);
      assertEquals(PluginState.STARTED, restarted);
    } finally { manager.close(); host.close(); }
  }

  @RestController public static class AController { @GetMapping("/edge/a") public String a() { return "a"; } }
  @RestController public static class BController { @GetMapping("/edge/b") public String b() { return "b"; } }
  private static class WebPlugin extends Pf4bootPlugin {
    WebPlugin(String id, AnnotationConfigApplicationContext context) throws Exception {
      super(new Pf4bootPluginWrapper(new DefaultPluginManager(), new DefaultPluginDescriptor(id, id,
          WebPlugin.class.getName(), "1.0", "", "test", "Apache-2.0"),
          Files.createTempDirectory("pf4boot-forward-audit"), WebPlugin.class.getClassLoader()));
      pluginContext = context;
    }
  }
  private static class ForwardFixture implements AutoCloseable {
    final StaticWebApplicationContext host = new StaticWebApplicationContext();
    final AnnotationConfigApplicationContext a = new AnnotationConfigApplicationContext(AController.class);
    final AnnotationConfigApplicationContext b = new AnnotationConfigApplicationContext(BController.class);
    final PluginRequestMappingHandlerMapping mapping = new PluginRequestMappingHandlerMapping();
    final MockHttpServletRequest outer = request("/edge/a");
    final MockHttpServletRequest inner;
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final HandlerExecutionChain outerChain;
    final HandlerExecutionChain innerChain;
    ForwardFixture() throws Exception {
      this(javax.servlet.DispatcherType.FORWARD, "/edge/b", false);
    }
    ForwardFixture(javax.servlet.DispatcherType type, String path, boolean drainOuter) throws Exception {
      inner = request(path);
      inner.setDispatcherType(type);
      host.setServletContext(new MockServletContext()); host.refresh();
      mapping.setApplicationContext(host); mapping.afterPropertiesSet();
      mapping.registerControllers(new WebPlugin("a", a)); mapping.registerControllers(new WebPlugin("b", b));
      outerChain = mapping.getHandler(outer);
      assertTrue(outerChain.getInterceptors()[0].preHandle(outer, response, outerChain.getHandler()));
      if (drainOuter) { mapping.beginDrain(Collections.singletonList("a")); }
      // Servlet forward/include preserves request attributes while selecting a new handler.
      String leaseKey = PluginRequestMappingHandlerMapping.class.getName() + ".pluginId";
      inner.setAttribute(leaseKey, outer.getAttribute(leaseKey));
      innerChain = mapping.getHandler(inner);
      assertTrue(innerChain.getInterceptors()[0].preHandle(inner, response, innerChain.getHandler()));
    }
    static MockHttpServletRequest request(String path) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", path); request.setServletPath(path); return request;
    }
    public void close() throws Exception {
      innerChain.getInterceptors()[0].afterCompletion(inner, response, innerChain.getHandler(), null);
      outerChain.getInterceptors()[0].afterCompletion(outer, response, outerChain.getHandler(), null);
      a.close(); b.close(); host.close();
    }
  }
  @Test public void forwardedPluginWorkMustBeCounted() throws Exception {
    try (ForwardFixture fixture = new ForwardFixture()) {
      System.out.println("AUDIT forward: a=" + fixture.mapping.getInFlightRequestCount("a")
          + ", b=" + fixture.mapping.getInFlightRequestCount("b"));
      assertEquals("forwarded plugin must be protected by drain", 1, fixture.mapping.getInFlightRequestCount("b"));
    }
  }
  @Test public void innerDispatchCompletionMustNotReleaseOuterWork() throws Exception {
    try (ForwardFixture fixture = new ForwardFixture()) {
      fixture.innerChain.getInterceptors()[0].afterCompletion(fixture.inner, fixture.response, fixture.innerChain.getHandler(), null);
      System.out.println("AUDIT outer after inner completion: a=" + fixture.mapping.getInFlightRequestCount("a"));
      assertEquals("outer handler is still running", 1, fixture.mapping.getInFlightRequestCount("a"));
    }
  }

  @Test public void includeExceptionKeepsOuterWorkProtected() throws Exception {
    try (ForwardFixture fixture = new ForwardFixture(javax.servlet.DispatcherType.INCLUDE, "/edge/b", false)) {
      fixture.innerChain.getInterceptors()[0].afterCompletion(fixture.inner, fixture.response,
          fixture.innerChain.getHandler(), new IllegalStateException("inner failure"));
      assertEquals(0, fixture.mapping.getInFlightRequestCount("b"));
      assertEquals(1, fixture.mapping.getInFlightRequestCount("a"));
    }
  }

  @Test public void samePluginNestedDispatchReleasesOnlyInnerCount() throws Exception {
    try (ForwardFixture fixture = new ForwardFixture(javax.servlet.DispatcherType.FORWARD, "/edge/a", false)) {
      assertEquals(2, fixture.mapping.getInFlightRequestCount("a"));
      fixture.innerChain.getInterceptors()[0].afterCompletion(fixture.inner, fixture.response, fixture.innerChain.getHandler(), null);
      assertEquals(1, fixture.mapping.getInFlightRequestCount("a"));
    }
  }

  @Test public void asyncContinuationAdmitsNewPluginAndPreservesOldAdmission() throws Exception {
    try (ForwardFixture fixture = new ForwardFixture(javax.servlet.DispatcherType.ASYNC, "/edge/b", true)) {
      assertEquals(1, fixture.mapping.getInFlightRequestCount("a"));
      assertEquals(1, fixture.mapping.getInFlightRequestCount("b"));
      fixture.mapping.endDrain(Collections.singletonList("a"));
    }
  }

  @Test public void admittedAsyncContinuationSurvivesDrainWithoutDoubleCounting() throws Exception {
    try (ForwardFixture fixture = new ForwardFixture(javax.servlet.DispatcherType.ASYNC, "/edge/a", true)) {
      assertEquals(1, fixture.mapping.getInFlightRequestCount("a"));
      fixture.mapping.endDrain(Collections.singletonList("a"));
    }
  }

  @Test public void completionRacingAsyncAdmissionDoesNotStrandNewPluginCount() throws Exception {
    verifyAsyncCompletionRace(false);
  }

  @Test public void completionRacingAsyncAdmissionDoesNotBypassDrain() throws Exception {
    verifyAsyncCompletionRace(true);
  }

  private void verifyAsyncCompletionRace(boolean drainTarget) throws Exception {
    try (ForwardFixture fixture = new ForwardFixture()) {
      fixture.innerChain.getInterceptors()[0].afterCompletion(fixture.inner, fixture.response, fixture.innerChain.getHandler(), null);
      fixture.inner.setDispatcherType(javax.servlet.DispatcherType.ASYNC);
      HandlerExecutionChain continuation = fixture.mapping.getHandler(fixture.inner);
      java.lang.reflect.Field field = PluginRequestMappingHandlerMapping.class.getDeclaredField("workTracker");
      field.setAccessible(true);
      Object admission = field.get(fixture.mapping);
      java.util.concurrent.atomic.AtomicReference<Boolean> accepted = new java.util.concurrent.atomic.AtomicReference<>();
      java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
      Thread thread = new Thread(() -> {
        try { accepted.set(continuation.getInterceptors()[0].preHandle(fixture.inner, fixture.response, continuation.getHandler())); }
        catch (Throwable error) { failure.set(error); }
      });
      synchronized (admission) {
        thread.start();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) { Thread.yield(); }
        assertEquals(Thread.State.BLOCKED, thread.getState());
        fixture.outerChain.getInterceptors()[0].afterCompletion(fixture.outer, fixture.response, fixture.outerChain.getHandler(), null);
        if (drainTarget) { fixture.mapping.beginDrain(Collections.singletonList("b")); }
      }
      thread.join(2000);
      assertFalse(thread.isAlive()); assertNull(failure.get());
      assertEquals(Boolean.valueOf(!drainTarget), accepted.get());
      continuation.getInterceptors()[0].afterCompletion(fixture.inner, fixture.response, continuation.getHandler(), null);
      assertEquals(0, fixture.mapping.getInFlightRequestCount("a"));
      assertEquals(0, fixture.mapping.getInFlightRequestCount("b"));
      if (drainTarget) { fixture.mapping.endDrain(Collections.singletonList("b")); }
    }
  }
}
