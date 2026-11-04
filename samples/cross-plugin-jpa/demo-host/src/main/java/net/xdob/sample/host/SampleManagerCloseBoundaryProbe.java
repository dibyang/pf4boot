package net.xdob.sample.host;

import net.xdob.pf4boot.*;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 仅在完整 smoke 的最后关闭隔离宿主的 manager，不影响前面的业务验收。 */
@RestController
@ConditionalOnProperty(name = "sample.lifecycle-probe-enabled", havingValue = "true")
public class SampleManagerCloseBoundaryProbe {
  private static final String ID = "sample-unrelated-service";
  private final Pf4bootPluginManager manager;
  private final Pf4bootProperties properties;
  private final String token;
  public SampleManagerCloseBoundaryProbe(@Lazy Pf4bootPluginManager manager, Pf4bootProperties properties,
      @Value("${spring.pf4boot.management.http.token:}") String token) {
    this.manager = manager; this.properties = properties; this.token = token;
  }
  @PostMapping("/api/sample/manager-close-boundaries")
  public synchronized Map<String, Object> verify(@RequestHeader("X-PF4Boot-Admin-Token") String supplied,
      javax.servlet.http.HttpServletRequest request) throws Exception {
    if (token.isEmpty() || !token.equals(supplied)) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    Pf4bootPluginManagerImpl concrete = manager.getApplicationContext().getBean("pluginManager", Pf4bootPluginManagerImpl.class);
    PluginWrapper original = manager.getPlugin(ID);
    ConfigurableApplicationContext group = manager.getPlatformContext(((Pf4bootPlugin) original.getPlugin()).getGroup());
    ApplicationEventMulticaster events = group.getBean("applicationEventMulticaster", ApplicationEventMulticaster.class);
    AtomicInteger rejected = new AtomicInteger();
    ApplicationListener<ContextClosedEvent> listener = event -> {
      if (event.getApplicationContext() == group) { expectClosed(() -> manager.startPlugin(ID)); rejected.incrementAndGet(); }
    };
    group.addApplicationListener(listener);
    String base = "http://127.0.0.1:" + request.getLocalPort();
    long timeout = properties.getPluginDrainTimeoutMillis();
    int port = Integer.parseInt(System.getProperty("sample.extension.port"));
    System.clearProperty("sample.cleanup.interceptor-entered");
    System.setProperty("sample.cleanup.interceptor-block", "true");
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<String> response = pool.submit(() -> get(base + "/api/sample/lifecycle-host-block"));
    try {
      properties.setPluginDrainTimeoutMillis(50);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (!Boolean.getBoolean("sample.cleanup.interceptor-entered")) {
        require(System.nanoTime() < deadline, "shutdown worker did not enter"); Thread.sleep(10);
      }
      try { concrete.close(); throw new IllegalStateException("shutdown ignored incomplete plugin cleanup"); }
      catch (PluginRuntimeException expected) { }
      require(group.isActive() && manager.getRootContext().isActive(), "failed shutdown closed parents");
      require(original.getPluginState() == PluginState.STARTED && ((Pf4bootPlugin) original.getPlugin()).getPluginContext().isActive(), "failed shutdown closed active plugin");
      expectClosed(() -> manager.startPlugin(ID)); expectClosed(() -> manager.loadPlugin(original.getPluginPath()));
      require(!manager.isApplicationStarted() && concrete.getScheduled() == null, "shutdown left auto-start enabled");
      System.clearProperty("sample.cleanup.interceptor-block");
      require("host-ok".equals(response.get(3, TimeUnit.SECONDS)), "shutdown worker response");
      properties.setPluginDrainTimeoutMillis(2000);
      concrete.close(); concrete.close();
      require(rejected.get() == 1 && !group.isActive() && manager.getRootContext() == null, "shutdown callback admission or retry");
      require(((Pf4bootPlugin) original.getPlugin()).getPluginContext() == null, "shutdown retained context");
      require(ApplicationContextProvider.getRegisteredContextCount() == 0, "shutdown retained global contexts");
      try (ServerSocket rebound = new ServerSocket(port)) { require(rebound.isBound(), "shutdown retained extension port"); }
      expectClosed(() -> manager.startPlugin(ID));
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("shutdownDrain", "PASSED"); result.put("shutdownReentry", "PASSED"); result.put("terminalAdmission", "PASSED"); return result;
    } finally {
      System.clearProperty("sample.cleanup.interceptor-block"); System.clearProperty("sample.cleanup.interceptor-entered");
      properties.setPluginDrainTimeoutMillis(timeout); events.removeApplicationListener(listener);
      pool.shutdownNow(); require(pool.awaitTermination(3, TimeUnit.SECONDS), "shutdown probe client still running");
    }
  }
  private void expectClosed(Runnable action) {
    try { action.run(); throw new IllegalStateException("shutdown admitted activation"); }
    catch (PluginRuntimeException expected) { require(expected.getMessage().contains("closing"), "unexpected activation error"); }
  }
  private String get(String address) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
    connection.setConnectTimeout(2000); connection.setReadTimeout(4000);
    try (java.io.InputStream input = connection.getInputStream(); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
      byte[] bytes = new byte[1024]; int count;
      while ((count = input.read(bytes)) >= 0) { output.write(bytes, 0, count); }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    } finally { connection.disconnect(); }
  }
  private void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
}
