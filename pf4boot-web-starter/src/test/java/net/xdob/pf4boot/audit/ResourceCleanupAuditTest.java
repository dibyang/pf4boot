package net.xdob.pf4boot.audit;

import net.xdob.pf4boot.*;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.PluginRequestMappingHandlerMapping;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// 回归复审中复现的异步、全局拦截器及真实端口销毁失败问题。
public class ResourceCleanupAuditTest {
  private static volatile CountDownLatch entered;
  private static volatile CountDownLatch release;

  private static class WebFixture implements AutoCloseable {
    final StaticWebApplicationContext host = new StaticWebApplicationContext();
    final AnnotationConfigApplicationContext pluginContext = new AnnotationConfigApplicationContext(WebConfig.class);
    final PluginRequestMappingHandlerMapping mapping;
    final MockMvc mvc;
    final WebPlugin plugin;
    WebFixture() throws Exception {
      this("/pf4boot/admin");
    }
    WebFixture(String adminBase) throws Exception {
      host.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
          "audit", Collections.singletonMap("spring.pf4boot.management.http.base-path", adminBase)));
      host.setServletContext(new MockServletContext());
      host.registerSingleton("hostController", HostController.class);
      host.registerSingleton("requestMappingHandlerMapping", PluginRequestMappingHandlerMapping.class);
      host.refresh();
      mapping = host.getBean(PluginRequestMappingHandlerMapping.class);
      PluginWrapper wrapper = new Pf4bootPluginWrapper(new DefaultPluginManager(),
          new DefaultPluginDescriptor("audit", "audit", WebPlugin.class.getName(), "1.0", "", "test", "Apache-2.0"),
          Files.createTempDirectory("pf4boot-web-audit"), getClass().getClassLoader());
      plugin = new WebPlugin(wrapper, pluginContext);
      mapping.registerControllers(plugin);
      mapping.registerInterceptors(plugin);
      mvc = MockMvcBuilders.webAppContextSetup(host).build();
    }
    public void close() { pluginContext.close(); host.close(); }
  }
  public static class WebPlugin extends Pf4bootPlugin {
    WebPlugin(PluginWrapper wrapper, AnnotationConfigApplicationContext context) {
      super(wrapper); this.pluginContext = context;
    }
  }
  @Configuration
  public static class WebConfig {
    @Bean public AsyncController asyncController() { return new AsyncController(); }
    @Bean public HandlerInterceptor auditInterceptor() {
      return new HandlerInterceptor() {
        public boolean preHandle(javax.servlet.http.HttpServletRequest request,
            javax.servlet.http.HttpServletResponse response, Object handler) throws Exception {
          if (request.getRequestURI().equals("/audit/host") && entered != null) {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("audit timed out"); }
          }
          return true;
        }
      };
    }
  }
  @RestController public static class AsyncController {
    @GetMapping("/audit/async") public Callable<String> async() { return () -> "ok"; }
    @GetMapping("/audit/deferred") public org.springframework.web.context.request.async.DeferredResult<String> deferred() {
      deferred = new org.springframework.web.context.request.async.DeferredResult<>();
      return deferred;
    }
    @GetMapping("/audit/blocked-async") public Callable<String> blocked() {
      return () -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); return "ok"; };
    }
    @GetMapping("/audit/async-error") public Callable<String> error() {
      return () -> { throw new IllegalStateException("async failure"); };
    }
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleError() { return "failed"; }
  }
  private static volatile org.springframework.web.context.request.async.DeferredResult<String> deferred;
  @RestController public static class HostController {
    @GetMapping("/audit/host") public String host() { return "ok"; }
    @GetMapping("/pf4boot/admin/audit-host") public String admin() { return "admin"; }
    @GetMapping("/ops/audit-host") public String customAdmin() { return "admin"; }
  }

  @Test public void completedAsyncRequestMustNotBlockLaterStop() throws Exception {
    entered = null;
    try (WebFixture fixture = new WebFixture()) {
      MvcResult started = fixture.mvc.perform(get("/audit/async")).andReturn();
      assertTrue(started.getRequest().isAsyncStarted());
      started.getAsyncResult(2000);
      MvcResult completed = fixture.mvc.perform(asyncDispatch(started)).andReturn();
      assertEquals(200, completed.getResponse().getStatus());
      System.out.println("AUDIT async completed; running=" + fixture.mapping.getInFlightRequestCount("audit"));
      assertEquals("completed async request must release admission", 0,
          fixture.mapping.getInFlightRequestCount("audit"));
    }
  }

  @Test public void hostRequestExecutingPluginInterceptorMustPreventDrain() throws Exception {
    entered = new CountDownLatch(1);
    release = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (WebFixture fixture = new WebFixture()) {
      Future<MvcResult> request = executor.submit(() -> fixture.mvc.perform(get("/audit/host")).andReturn());
      try {
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        fixture.mapping.beginDrain(Collections.singletonList("audit"));
        boolean drained = fixture.mapping.awaitDrain(Collections.singletonList("audit"), 0);
        System.out.println("AUDIT blocked plugin interceptor; drained=" + drained);
        assertFalse("running plugin interceptor must be counted", drained);
      } finally {
        release.countDown();
        assertEquals(200, request.get(3, TimeUnit.SECONDS).getResponse().getStatus());
        fixture.mapping.endDrain(Collections.singletonList("audit"));
      }
    } finally { executor.shutdownNow(); entered = null; }
  }

  @Test public void deferredRedispatchMayFinishDuringDrainWithoutDoubleCounting() throws Exception {
    entered = null;
    try (WebFixture fixture = new WebFixture()) {
      MvcResult started = fixture.mvc.perform(get("/audit/deferred")).andReturn();
      fixture.mapping.beginDrain(Collections.singletonList("audit"));
      assertEquals(503, fixture.mvc.perform(get("/audit/host")).andReturn().getResponse().getStatus());
      deferred.setResult("ok");
      started.getAsyncResult(2000);
      assertEquals(200, fixture.mvc.perform(asyncDispatch(started)).andReturn().getResponse().getStatus());
      assertEquals(0, fixture.mapping.getInFlightRequestCount("audit"));
      fixture.mapping.endDrain(Collections.singletonList("audit"));
    }
  }

  @Test public void requestCompletionMustStillWaitForRunningCallable() throws Exception {
    entered = new CountDownLatch(1);
    release = new CountDownLatch(1);
    try (WebFixture fixture = new WebFixture()) {
      MvcResult started = fixture.mvc.perform(get("/audit/blocked-async")).andReturn();
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      fixture.mapping.beginDrain(Collections.singletonList("audit"));
      started.getRequest().getAsyncContext().complete();
      assertFalse(fixture.mapping.awaitDrain(Collections.singletonList("audit"), 0));
      release.countDown();
      assertTrue(fixture.mapping.awaitDrain(Collections.singletonList("audit"), 2000));
      fixture.mapping.endDrain(Collections.singletonList("audit"));
    } finally { release.countDown(); entered = null; }
  }

  @Test public void previouslyMatchedHandlerMustBeRejectedAfterUnregister() throws Exception {
    entered = null;
    try (WebFixture fixture = new WebFixture()) {
      org.springframework.mock.web.MockHttpServletRequest request =
          new org.springframework.mock.web.MockHttpServletRequest("GET", "/audit/async");
      request.setServletPath("/audit/async");
      org.springframework.web.servlet.HandlerExecutionChain chain = fixture.mapping.getHandler(request);
      assertNotNull(chain);
      fixture.mapping.unregisterControllers(fixture.plugin);
      org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
      assertFalse(chain.getInterceptors()[0].preHandle(request, response, chain.getHandler()));
      assertEquals(503, response.getStatus());
    }
  }

  @Test public void managementRequestDoesNotWaitForItsOwnPluginInterceptor() throws Exception {
    entered = null;
    try (WebFixture fixture = new WebFixture()) {
      fixture.mapping.beginDrain(Collections.singletonList("audit"));
      assertEquals(200, fixture.mvc.perform(get("/pf4boot/admin/audit-host")).andReturn().getResponse().getStatus());
      assertEquals(0, fixture.mapping.getInFlightRequestCount("audit"));
      fixture.mapping.endDrain(Collections.singletonList("audit"));
    }
  }

  @Test public void configuredManagementPrefixAlsoBypassesPluginInterceptors() throws Exception {
    entered = null;
    try (WebFixture fixture = new WebFixture("/ops/")) {
      fixture.mapping.beginDrain(Collections.singletonList("audit"));
      assertEquals(200, fixture.mvc.perform(get("/ops/audit-host")).andReturn().getResponse().getStatus());
      fixture.mapping.endDrain(Collections.singletonList("audit"));
    }
  }

  @Test public void asyncExceptionReleasesTheRequestExactlyOnce() throws Exception {
    entered = null;
    try (WebFixture fixture = new WebFixture()) {
      MvcResult started = fixture.mvc.perform(get("/audit/async-error")).andReturn();
      started.getAsyncResult(2000);
      assertEquals(500, fixture.mvc.perform(asyncDispatch(started)).andReturn().getResponse().getStatus());
      assertEquals(0, fixture.mapping.getInFlightRequestCount("audit"));
    }
  }

  private static final List<ServerSocket> leakedSockets = new ArrayList<>();
  private static int port;
  private static volatile boolean allowDestroy;
  private static int pluginDestructions;
  public static class SocketResource implements org.springframework.beans.factory.DisposableBean {
    final ServerSocket socket;
    SocketResource() throws Exception {
      socket = new ServerSocket(port);
      port = socket.getLocalPort();
      leakedSockets.add(socket);
    }
    public void destroy() throws Exception {
      if (!allowDestroy) { throw new IllegalStateException("injected Spring destroy failure before socket close"); }
      socket.close();
    }
  }
  @Configuration public static class SocketConfig {
    @Bean public SocketResource socketResource() throws Exception { return new SocketResource(); }
  }
  @PluginStarter(SocketConfig.class)
  public static class SocketPlugin extends Pf4bootPlugin {
    public SocketPlugin(PluginWrapper wrapper) { super(wrapper); }
    @javax.annotation.PreDestroy public void destroyPluginInstance() { pluginDestructions++; }
  }
  private static class Manager extends Pf4bootPluginManagerImpl {
    Manager(AnnotationConfigApplicationContext host, Path root) {
      super(host, new Pf4bootProperties(), new Pf4bootPluginSupport() {},
          new DefaultShareBeanMgr(new DefaultAutoExportMgr()), root);
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor("socket", "socket",
          SocketPlugin.class.getName(), "1.0", "", "test", "Apache-2.0");
      ClassLoader loader = new URLClassLoader(new URL[0], getClass().getClassLoader());
      Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(this, descriptor, root.resolve("socket"), loader);
      wrapper.setPluginFactory(getPluginFactory());
      wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put("socket", wrapper);
      resolvedPlugins.add(wrapper);
      pluginClassLoaders.put("socket", loader);
      dependencyResolver = new DependencyResolver(getVersionManager());
      dependencyResolver.resolve(Collections.singletonList(descriptor));
    }
  }
  @Test public void springDestroyFailureMustNotReportSuccessfulStopAndAllowBlindRestart() throws Exception {
    port = 0;
    pluginDestructions = 0;
    allowDestroy = false;
    AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
    host.registerBean("metadataReaderFactory", MetadataReaderFactory.class,
        () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader()));
    host.refresh();
    Manager manager = new Manager(host, Files.createTempDirectory("pf4boot-socket-audit"));
    try {
      assertEquals(PluginState.STARTED, manager.startPlugin("socket"));
      try {
        manager.stopPlugin("socket");
        fail("destruction failure must be reported");
      } catch (PluginRuntimeException expected) { }
      PluginState stopped = manager.getPlugin("socket").getPluginState();
      boolean leaked = !leakedSockets.get(0).isClosed();
      PluginState restarted = manager.startPlugin("socket");
      System.out.println("AUDIT destroy failure: stop=" + stopped + ", socketOpen=" + leaked + ", restart=" + restarted);
      assertEquals("failed destruction must not be reported as STOPPED", PluginState.FAILED, stopped);
      assertTrue(leaked);
      assertEquals(PluginState.FAILED, restarted);
      assertEquals(1, pluginDestructions);
      allowDestroy = true;
      assertEquals(PluginState.STARTED, manager.startPlugin("socket"));
      assertTrue(leakedSockets.get(0).isClosed());
      assertEquals(1, pluginDestructions);
      assertEquals(PluginState.STOPPED, manager.stopPlugin("socket"));
      assertEquals(2, pluginDestructions);
    } finally {
      allowDestroy = true;
      for (ServerSocket socket : leakedSockets) { socket.close(); }
      leakedSockets.clear();
      manager.close(); host.close();
    }
  }
}
