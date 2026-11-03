package net.xdob.sample.host;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.PluginRequestMappingHandlerMapping;
import javax.servlet.http.HttpServletRequest;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** 真实 Tomcat 请求与真实插件资源关闭边界验收，默认不注册。 */
@RestController
@ConditionalOnProperty(name = "sample.lifecycle-probe-enabled", havingValue = "true")
public class SampleCleanupBoundaryProbe {
  private static final String ID = "sample-unrelated-service";
  private final Pf4bootPluginManager manager;
  private final Pf4bootProperties properties;
  private final PluginRequestMappingHandlerMapping mapping;
  private final String token;
  public SampleCleanupBoundaryProbe(@Lazy Pf4bootPluginManager manager, Pf4bootProperties properties,
      @Lazy PluginRequestMappingHandlerMapping mapping,
      @Value("${spring.pf4boot.management.http.token:}") String token) {
    this.manager = manager; this.properties = properties; this.mapping = mapping; this.token = token;
  }
  @GetMapping("/api/sample/lifecycle-host-block")
  public String host() { return "host-ok"; }

  @PostMapping("/api/sample/lifecycle-boundaries")
  public synchronized Map<String, Object> verify(@RequestHeader("X-PF4Boot-Admin-Token") String supplied,
      HttpServletRequest request) throws Exception {
    if (token.isEmpty() || !token.equals(supplied)) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    String base = "http://127.0.0.1:" + request.getLocalPort();
    long timeout = properties.getPluginDrainTimeoutMillis();
    try {
      for (int i = 0; i < 10; i++) {
        require("async-ok".equals(get(base + "/api/sample/unrelated/async")), "Callable response");
        require("deferred-ok".equals(get(base + "/api/sample/unrelated/deferred")), "DeferredResult response");
      }
      require(mapping.awaitDrain(Collections.singletonList(ID), 2000), "async count leaked");
      properties.setPluginDrainTimeoutMillis(50);
      verifyBlockedInterceptor(base);
      verifyTimedOutWorker(base);
      verifyDestruction();
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("asyncRequests", 20); result.put("interceptorDrain", "PASSED");
      result.put("timeoutWorkerDrain", "PASSED"); result.put("socketRecovery", "PASSED");
      return result;
    } finally {
      properties.setPluginDrainTimeoutMillis(timeout);
      for (String key : Arrays.asList("sample.cleanup.interceptor-block", "sample.cleanup.interceptor-entered",
          "sample.cleanup.async-block", "sample.cleanup.async-entered", "sample.cleanup.fail-close")) {
        System.clearProperty(key);
      }
    }
  }
  private void verifyBlockedInterceptor(String base) throws Exception {
    System.setProperty("sample.cleanup.interceptor-block", "true");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<String> response = executor.submit(() -> get(base + "/api/sample/lifecycle-host-block"));
    try {
      awaitProperty("sample.cleanup.interceptor-entered");
      expectDrainRejection();
    } finally {
      System.clearProperty("sample.cleanup.interceptor-block");
      try { require("host-ok".equals(response.get(3, TimeUnit.SECONDS)), "host response"); }
      finally { executor.shutdownNow(); }
    }
    restartAfterDrain();
  }
  private void verifyTimedOutWorker(String base) throws Exception {
    System.setProperty("sample.cleanup.async-block", "true");
    try {
      require("timed-out".equals(get(base + "/api/sample/unrelated/async-timeout")), "timeout response");
      awaitProperty("sample.cleanup.async-entered");
      expectDrainRejection();
    } finally { System.clearProperty("sample.cleanup.async-block"); }
    restartAfterDrain();
  }
  private void expectDrainRejection() {
    try { manager.stopPlugin(ID); throw new IllegalStateException("stop ignored active plugin work"); }
    catch (PluginRuntimeException expected) {
      require(manager.getPlugin(ID).getPluginState() == PluginState.STARTED, "timeout changed plugin state");
    }
  }
  private void restartAfterDrain() throws Exception {
    require(mapping.awaitDrain(Collections.singletonList(ID), 2000), "work did not finish");
    require(manager.stopPlugin(ID) == PluginState.STOPPED, "stop after drain");
    require(manager.startPlugin(ID) == PluginState.STARTED, "restart after drain");
  }
  private void verifyDestruction() throws Exception {
    int port = Integer.parseInt(System.getProperty("sample.cleanup.socket-port"));
    System.setProperty("sample.cleanup.fail-close", "true");
    try {
      try { manager.stopPlugin(ID); throw new IllegalStateException("destroy failure was ignored"); }
      catch (PluginRuntimeException expected) { require(manager.getPlugin(ID).getPluginState() == PluginState.FAILED, "destroy state"); }
      try (ServerSocket unexpected = new ServerSocket(port)) {
        throw new IllegalStateException("fixture socket unexpectedly closed");
      } catch (BindException expected) { }
      require(manager.startPlugin(ID) == PluginState.FAILED, "restart bypassed failed destruction");
    } finally { System.clearProperty("sample.cleanup.fail-close"); }
    require(manager.startPlugin(ID) == PluginState.STARTED, "destruction recovery failed");
    require(port == Integer.parseInt(System.getProperty("sample.cleanup.socket-port")), "port was not reused");
  }
  private void awaitProperty(String name) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (!Boolean.getBoolean(name)) {
      if (System.nanoTime() >= deadline) { throw new IllegalStateException("fixture did not enter: " + name); }
      Thread.sleep(10);
    }
  }
  private String get(String address) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
    connection.setConnectTimeout(2000); connection.setReadTimeout(4000);
    try (java.io.InputStream input = connection.getInputStream();
         java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
      byte[] bytes = new byte[1024]; int count;
      while ((count = input.read(bytes)) >= 0) { output.write(bytes, 0, count); }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    } finally { connection.disconnect(); }
  }
  private void require(boolean condition, String message) {
    if (!condition) {
      org.slf4j.LoggerFactory.getLogger(getClass()).error("Cleanup boundary probe failed: {}", message);
      throw new IllegalStateException(message);
    }
  }
}
