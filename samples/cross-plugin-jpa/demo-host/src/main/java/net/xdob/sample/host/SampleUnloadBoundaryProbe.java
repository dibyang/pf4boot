package net.xdob.sample.host;

import net.xdob.pf4boot.*;
import net.xdob.pf4boot.modal.DynamicBeanConflictPolicy;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 仅 smoke 启用的真实 zip 多级卸载、并发与监听器异常验收。 */
@RestController
@ConditionalOnProperty(name = "sample.lifecycle-probe-enabled", havingValue = "true")
public class SampleUnloadBoundaryProbe {
  private static final String DOMAIN = "sample-demo-jpa-domain";
  private static final String SERVICE = "sample-user-book-service";
  private static final String WORKFLOW = "sample-workflow";
  private static final String UNRELATED = "sample-unrelated-service";
  private final Pf4bootPluginManager manager;
  private final Pf4bootProperties properties;
  private final String token;
  public SampleUnloadBoundaryProbe(@Lazy Pf4bootPluginManager manager, Pf4bootProperties properties,
      @Value("${spring.pf4boot.management.http.token:}") String token) {
    this.manager = manager; this.properties = properties; this.token = token;
  }
  @PostMapping("/api/sample/unload-boundaries")
  public synchronized Map<String, Object> verify(@RequestHeader("X-PF4Boot-Admin-Token") String supplied,
      javax.servlet.http.HttpServletRequest request) throws Exception {
    if (token.isEmpty() || !token.equals(supplied)) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    String base = "http://127.0.0.1:" + request.getLocalPort();
    long timeout = properties.getPluginDrainTimeoutMillis();
    try {
      properties.setPluginDrainTimeoutMillis(50);
      verifyDependentScope(base); verifyConcurrentStart(); verifyListenerFailure();
      verifyUncheckedLoaderClose(); verifyExtensionReplacement();
      verifyCleanupReentry();
      require(get(base + "/api/sample/unrelated/health").contains("UP"), "unrelated restart health");
      get(base + "/api/sample/workflow/summary");
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("multiLevelUnload", "PASSED"); result.put("concurrentStart", "PASSED");
      result.put("listenerFailure", "PASSED"); result.put("reentrantStartRejected", "PASSED");
      result.put("uncheckedLoaderClose", "PASSED"); result.put("extensionReplacement", "PASSED");
      result.put("cleanupReentry", "PASSED"); return result;
    } finally {
      properties.setPluginDrainTimeoutMillis(timeout);
      System.clearProperty("sample.dispatch.inner-block"); System.clearProperty("sample.dispatch.inner-entered");
      System.clearProperty("sample.unload.fail-loader");
    }
  }
  private void verifyDependentScope(String base) throws Exception {
    List<String> order = Arrays.asList(WORKFLOW, SERVICE, DOMAIN);
    Map<String, PluginWrapper> original = new LinkedHashMap<>();
    for (String id : order) { original.put(id, manager.getPlugin(id)); }
    System.clearProperty("sample.dispatch.inner-entered"); System.setProperty("sample.dispatch.inner-block", "true");
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<String> response = pool.submit(() -> get(base + "/api/sample/workflow/dispatch-target"));
    try {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (!Boolean.getBoolean("sample.dispatch.inner-entered")) {
        require(System.nanoTime() < deadline, "leaf did not enter"); Thread.sleep(10);
      }
      try { manager.unloadPlugin(DOMAIN); throw new IllegalStateException("blocked dependency scope unloaded"); }
      catch (PluginRuntimeException expected) { }
      for (String id : order) {
        PluginWrapper wrapper = original.get(id);
        require(manager.getPlugin(id) == wrapper && wrapper.getPluginState() == PluginState.STARTED, "drain failure mutated " + id);
        require(((Pf4bootPlugin) wrapper.getPlugin()).getPluginContext().isActive() && !closed(wrapper), "drain failure released " + id);
      }
    } finally {
      System.clearProperty("sample.dispatch.inner-block");
      try { require("target-ok".equals(response.get(3, TimeUnit.SECONDS)), "leaf response"); }
      finally { pool.shutdownNow(); require(pool.awaitTermination(3, TimeUnit.SECONDS), "leaf client still running"); }
    }
    List<String> notified = new ArrayList<>();
    AtomicInteger reentryRejected = new AtomicInteger();
    PluginStateListener listener = event -> {
      if (order.contains(event.getPlugin().getPluginId()) && event.getPlugin().getPluginState() == PluginState.UNLOADED) {
        for (String id : order) {
          require(manager.getPlugin(id) == null && closed(original.get(id)), "notification preceded batch cleanup " + id);
          require(((Pf4bootPlugin) original.get(id).getPlugin()).getPluginContext() == null, "notification retained context " + id);
        }
        try { manager.startPlugin(DOMAIN); throw new IllegalStateException("notification restarted pending provider"); }
        catch (PluginNotFoundException expected) { reentryRejected.incrementAndGet(); }
        notified.add(event.getPlugin().getPluginId());
      }
    };
    manager.addPluginStateListener(listener);
    try { require(manager.unloadPlugin(DOMAIN), "dependent unload retry"); }
    finally { manager.removePluginStateListener(listener); }
    require(order.equals(notified), "dependent unload order: " + notified);
    require(reentryRejected.get() == order.size(), "unload notification reentry not rejected");
    for (String id : Arrays.asList(DOMAIN, SERVICE, WORKFLOW)) {
      require(manager.getPlugin(id) == null && closed(original.get(id)), "dependent cleanup " + id);
      require(id.equals(manager.loadPlugin(original.get(id).getPluginPath())), "dependent reload " + id);
    }
    require(manager.startPlugin(WORKFLOW) == PluginState.STARTED, "dependent restart");
  }
  private void verifyConcurrentStart() throws Exception {
    PluginWrapper original = manager.getPlugin(UNRELATED);
    CountDownLatch notifying = new CountDownLatch(1), release = new CountDownLatch(1), attempted = new CountDownLatch(1);
    AtomicReference<Thread> starter = new AtomicReference<>();
    PluginStateListener listener = event -> {
      if (UNRELATED.equals(event.getPlugin().getPluginId()) && event.getPlugin().getPluginState() == PluginState.UNLOADED) {
        notifying.countDown();
        try { require(release.await(3, TimeUnit.SECONDS), "unload notification barrier"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
      }
    };
    ExecutorService pool = Executors.newFixedThreadPool(2);
    manager.addPluginStateListener(listener);
    Future<Boolean> unload = pool.submit(() -> manager.unloadPlugin(UNRELATED));
    try {
      require(notifying.await(2, TimeUnit.SECONDS), "unload did not reach notification");
      Future<String> start = pool.submit(() -> {
        starter.set(Thread.currentThread()); attempted.countDown();
        try { return manager.startPlugin(UNRELATED).name(); }
        catch (PluginNotFoundException expected) { return "REMOVED"; }
      });
      require(attempted.await(2, TimeUnit.SECONDS), "start not attempted");
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (!start.isDone() && starter.get().getState() != Thread.State.WAITING && System.nanoTime() < deadline) { Thread.sleep(5); }
      require(!start.isDone() && starter.get().getState() == Thread.State.WAITING, "start crossed unload critical section");
      release.countDown(); require(unload.get(3, TimeUnit.SECONDS), "concurrent unload");
      require("REMOVED".equals(start.get(3, TimeUnit.SECONDS)), "old plugin restarted during unload");
      require(closed(original) && ((Pf4bootPlugin) original.getPlugin()).getPluginContext() == null, "concurrent cleanup incomplete");
    } finally {
      release.countDown(); pool.shutdownNow();
      try { require(pool.awaitTermination(3, TimeUnit.SECONDS), "concurrent callers still running"); }
      finally { manager.removePluginStateListener(listener); }
    }
    reload(original.getPluginPath());
  }
  private void verifyListenerFailure() throws Exception {
    PluginWrapper original = manager.getPlugin(UNRELATED);
    AtomicInteger called = new AtomicInteger();
    PluginStateListener listener = event -> {
      if (UNRELATED.equals(event.getPlugin().getPluginId()) && event.getPlugin().getPluginState() == PluginState.UNLOADED) {
        called.incrementAndGet(); throw new IllegalStateException("smoke unload notification failure");
      }
    };
    manager.addPluginStateListener(listener);
    try {
      try { manager.unloadPlugin(UNRELATED); throw new IllegalStateException("listener failure not reported"); }
      catch (PluginRuntimeException expected) { }
    } finally { manager.removePluginStateListener(listener); }
    require(called.get() == 1 && manager.getPlugin(UNRELATED) == null && closed(original), "listener skipped unload cleanup");
    require(((Pf4bootPlugin) original.getPlugin()).getPluginContext() == null, "listener retained context");
    require(!ApplicationContextProvider.containsApplicationContext(original.getPluginClassLoader()), "listener retained global context");
    require(!manager.unloadPlugin(UNRELATED), "completed unload retried destruction");
    reload(original.getPluginPath());
  }
  private void reload(Path path) {
    require(UNRELATED.equals(manager.loadPlugin(path)), "unrelated load after unload");
    require(manager.startPlugin(UNRELATED) == PluginState.STARTED, "unrelated start after unload");
  }
  private void verifyUncheckedLoaderClose() {
    PluginWrapper original = manager.getPlugin(UNRELATED);
    System.setProperty("sample.unload.fail-loader", "true");
    try {
      try { manager.unloadPlugin(UNRELATED); throw new IllegalStateException("unchecked loader failure not reported"); }
      catch (PluginRuntimeException expected) { }
      require(manager.getPlugin(UNRELATED) == original && closed(original), "partial loader close fixture");
      require(original.getPluginState() == PluginState.FAILED && original.getFailedException() instanceof IllegalStateException, "unchecked close did not fail closed");
      require(manager.startPlugin(UNRELATED) == PluginState.FAILED, "restart reused partially closed loader");
      require(((Pf4bootPlugin) original.getPlugin()).getPluginContext() == null, "restart created context on closed loader");
    } finally { System.clearProperty("sample.unload.fail-loader"); }
    require(manager.unloadPlugin(UNRELATED), "unchecked close retry failed");
    reload(original.getPluginPath());
  }
  private void verifyExtensionReplacement() throws Exception {
    String name = "net.xdob.sample.unrelated.CleanupProbeExtension";
    String group = ((Pf4bootPlugin) manager.getPlugin(UNRELATED).getPlugin()).getGroup();
    Object replacement = new Object();
    DynamicBeanConflictPolicy previous = properties.getDynamicBeanConflictPolicy();
    int port = Integer.parseInt(System.getProperty("sample.extension.port"));
    try {
      properties.setDynamicBeanConflictPolicy(DynamicBeanConflictPolicy.REPLACE);
      manager.registerBeanToPlatformContext(group, name, replacement);
      require(manager.stopPlugin(UNRELATED) == PluginState.STOPPED, "replaced extension owner stop");
      require(manager.getPlatformContext(group).getBean(name) == replacement, "stopping old owner removed replacement");
      try (java.net.ServerSocket released = new java.net.ServerSocket(port)) { require(released.isBound(), "original extension port retained"); }
    } finally {
      properties.setDynamicBeanConflictPolicy(previous);
      if (manager.getPlatformContext(group).getBeanFactory().containsLocalBean(name)
          && manager.getPlatformContext(group).getBean(name) == replacement) {
        manager.unregisterBeanFromPlatformContext(group, name);
      }
    }
    require(manager.startPlugin(UNRELATED) == PluginState.STARTED, "extension owner restart failed");
  }
  private void verifyCleanupReentry() throws Exception {
    PluginWrapper original = manager.getPlugin(UNRELATED);
    int port = Integer.parseInt(System.getProperty("sample.extension.port"));
    AtomicBoolean attempted = new AtomicBoolean();
    AtomicInteger rejected = new AtomicInteger();
    org.springframework.context.ConfigurableApplicationContext host = manager.getApplicationContext();
    org.springframework.context.ApplicationListener<net.xdob.pf4boot.spring.boot.PreStopPluginEvent> listener = event -> {
      if (UNRELATED.equals(event.getPlugin().getPluginId()) && attempted.compareAndSet(false, true)) {
        try { manager.restartPlugin(UNRELATED); throw new IllegalStateException("cleanup callback restart succeeded"); }
        catch (PluginRuntimeException expected) { rejected.incrementAndGet(); }
      }
    };
    host.addApplicationListener(listener);
    try {
      require(manager.stopPlugin(UNRELATED) == PluginState.STOPPED, "cleanup reentry stop");
      require(rejected.get() == 1, "active cleanup restart not rejected");
      require(((Pf4bootPlugin) original.getPlugin()).getPluginContext() == null, "cleanup reentry retained context");
      require(!ApplicationContextProvider.containsApplicationContext(original.getPluginClassLoader()), "cleanup reentry retained global registration");
      try (java.net.ServerSocket rebound = new java.net.ServerSocket(port)) { require(rebound.isBound(), "cleanup reentry retained port"); }
    } finally {
      host.getBean("applicationEventMulticaster", org.springframework.context.event.ApplicationEventMulticaster.class).removeApplicationListener(listener);
    }
    require(manager.startPlugin(UNRELATED) == PluginState.STARTED, "restart after cleanup reentry failed");
  }
  private boolean closed(PluginWrapper wrapper) { return ((PluginClassLoader) wrapper.getPluginClassLoader()).isClosed(); }
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
