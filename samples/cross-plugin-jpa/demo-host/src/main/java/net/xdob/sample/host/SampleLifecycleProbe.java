package net.xdob.sample.host;

import net.xdob.pf4boot.ApplicationContextProvider;
import net.xdob.pf4boot.DefaultShareBeanMgr;
import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.Pf4bootPluginSupport;
import org.pf4j.PluginState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.PluginRequestMappingHandlerMapping;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仅由 smoke 显式启用的真实插件生命周期验收及失败注入入口。 */
@RestController
@ConditionalOnProperty(name = "sample.lifecycle-probe-enabled", havingValue = "true")
public class SampleLifecycleProbe implements Pf4bootPluginSupport {
  private static final String ID = "sample-unrelated-service";
  private final Pf4bootPluginManager manager;
  private final DefaultShareBeanMgr sharing;
  private final PluginRequestMappingHandlerMapping mapping;
  private final String token;
  private boolean failStart;
  private boolean failStop;
  @Value("${server.port:8080}") private int port;

  public SampleLifecycleProbe(@Lazy Pf4bootPluginManager manager, DefaultShareBeanMgr sharing,
      @Lazy PluginRequestMappingHandlerMapping mapping,
      @Value("${spring.pf4boot.management.http.token:}") String token) {
    this.manager = manager;
    this.sharing = sharing;
    this.mapping = mapping;
    this.token = token;
  }

  @Override
  public int getPriority() { return Integer.MAX_VALUE; }

  @Override
  public void startedPlugin(Pf4bootPlugin plugin) {
    if (ID.equals(plugin.getPluginId()) && failStart) {
      throw new IllegalStateException("smoke injected start failure after registration");
    }
  }

  @Override
  public void stopPlugin(Pf4bootPlugin plugin) {
    if (ID.equals(plugin.getPluginId()) && failStop) {
      throw new IllegalStateException("smoke injected stop failure");
    }
  }

  /** 反复操作真实 zip 插件，检查资源归零、失败恢复及旧 loader 可回收。 */
  @PostMapping("/api/sample/lifecycle-probe")
  public synchronized Map<String, Object> verify(
      @RequestHeader("X-PF4Boot-Admin-Token") String suppliedToken) throws Exception {
    return verify(suppliedToken, false);
  }

  /** 新 zip 的 JSON DTO 验收与旧插件入口分开，避免要求旧插件重编译。 */
  @PostMapping("/api/sample/json-lifecycle-probe")
  public synchronized Map<String, Object> verifyJson(
      @RequestHeader("X-PF4Boot-Admin-Token") String suppliedToken) throws Exception {
    return verify(suppliedToken, true);
  }

  private Map<String, Object> verify(String suppliedToken, boolean json) throws Exception {
    if (token.isEmpty() || !token.equals(suppliedToken)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    int contexts = ApplicationContextProvider.getRegisteredContextCount();
    int handlers = mapping.getRegisteredHandlerCount(ID);
    require(handlers > 0, "initial plugin handlers missing");
    if (json) { exerciseJsonDto(); }
    for (int i = 0; i < 20; i++) {
      java.net.ServerSocket previousSocket = extensionSocket();
      manager.stopPlugin(ID);
      assertReleased(contexts - 1);
      require(previousSocket == null || previousSocket.isClosed(), "old extension socket not closed");
      require(startProbePlugin() == PluginState.STARTED, "restart failed");
      require(mapping.getRegisteredHandlerCount(ID) == handlers, "handler count grew");
      if (json) { exerciseJsonDto(); }
    }
    verifyFailures(contexts);
    org.slf4j.LoggerFactory.getLogger(getClass()).info("Lifecycle probe restarts and failure recovery passed");
    List<WeakReference<ClassLoader>> loaders = new ArrayList<>();
    // 在线程退出后检查 GC，避免最后一次 reload 的临时栈引用影响验收。
    java.util.concurrent.atomic.AtomicReference<Throwable> reloadFailure = new java.util.concurrent.atomic.AtomicReference<>();
    Thread reloader = new Thread(() -> {
      try {
        for (int i = 0; i < 10; i++) {
          if (json) { exerciseJsonDto(); }
          loaders.add(reloadAndObserve());
          require(ApplicationContextProvider.getRegisteredContextCount() == contexts, "context count grew");
          require(mapping.getRegisteredHandlerCount(ID) == handlers, "reload handler count grew");
        }
      } catch (Throwable failure) {
        reloadFailure.set(failure);
      }
    }, "sample-lifecycle-reloader");
    reloader.start();
    reloader.join(60000);
    require(!reloader.isAlive(), "reload worker timed out");
    require(reloadFailure.get() == null, "reload worker failed: " + reloadFailure.get());
    for (int attempt = 0; attempt < 30 && remaining(loaders) > 0; attempt++) {
      System.gc();
      Thread.sleep(100);
    }
    require(remaining(loaders) == 0, "old plugin classloaders remain: " + remaining(loaders));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("restarts", 20);
    result.put("reloads", loaders.size());
    result.put("collectedLoaders", loaders.size());
    result.put("failureRecovery", "PASSED");
    if (json) { result.put("jsonRoundTrips", 31); result.put("jsonConverter", jsonConverter()); }
    return result;
  }

  private String jsonConverter() throws ClassNotFoundException {
    Class<?> payload = manager.getPlugin(ID).getPluginClassLoader().loadClass("net.xdob.sample.unrelated.CleanupProbeFixtures$JsonPayload");
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter adapter =
        manager.getApplicationContext().getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.class);
    for (org.springframework.http.converter.HttpMessageConverter<?> converter : adapter.getMessageConverters()) {
      if (converter.canWrite(payload, org.springframework.http.MediaType.APPLICATION_JSON)) { return converter.getClass().getSimpleName(); }
    }
    throw new IllegalStateException("JSON DTO converter missing");
  }

  /** 使用真实 HTTP converter 触发插件 DTO 双向解析，不把类型实例交给验收线程持有。 */
  private void exerciseJsonDto() throws Exception {
    for (String method : new String[]{"GET", "POST"}) {
      java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(
          "http://127.0.0.1:" + port + "/api/sample/unrelated/json-payload").openConnection();
      connection.setConnectTimeout(2000); connection.setReadTimeout(5000); connection.setRequestMethod(method);
      try {
        String expected = "plugin-json";
        if ("POST".equals(method)) {
          expected = "json-roundtrip"; connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
          try (java.io.OutputStream output = connection.getOutputStream()) {
            output.write("{\"value\":\"json-roundtrip\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
          }
        }
        require(connection.getResponseCode() == 200, "JSON DTO HTTP failure: " + method);
        try (java.io.Reader reader = new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
          require(expected.equals(com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().get("value").getAsString()), "JSON DTO changed");
        }
      } finally { connection.disconnect(); }
    }
  }

  private void verifyFailures(int contexts) {
    java.net.ServerSocket previousSocket = extensionSocket();
    failStop = true;
    try {
      try {
        manager.stopPlugin(ID);
        throw new AssertionError("injected stop failure was ignored");
      } catch (org.pf4j.PluginRuntimeException expected) {
        require(manager.getPlugin(ID).getPluginState() == PluginState.FAILED, "stop did not fail closed");
      }
      assertReleased(contexts - 1);
      require(previousSocket == null || previousSocket.isClosed(), "failed stop retained extension socket");
    } finally {
      failStop = false;
    }
    require(startProbePlugin() == PluginState.STARTED, "cleanup retry failed");
    manager.stopPlugin(ID);
    failStart = true;
    try {
      require(startProbePlugin() == PluginState.FAILED, "injected start did not fail");
      assertReleased(contexts - 1);
    } finally {
      failStart = false;
    }
    require(startProbePlugin() == PluginState.STARTED, "failed-start recovery failed");
  }

  /** 新夹具专用入口，不改变未重编译旧插件使用的 lifecycle-probe 验收。 */
  @PostMapping("/api/sample/pre-refresh-boundaries")
  public synchronized Map<String, Object> verifyBeforeRefresh(
      @RequestHeader("X-PF4Boot-Admin-Token") String suppliedToken) throws Exception {
    if (token.isEmpty() || !token.equals(suppliedToken)) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    require(manager.stopPlugin(ID) == PluginState.STOPPED, "initial pre-refresh stop");
    int contexts = ApplicationContextProvider.getRegisteredContextCount();
    int destroyed = Integer.getInteger("sample.dispatch.plugin-destroys", 0);
    System.setProperty("sample.pre-refresh.fail", "true");
    try {
      for (int i = 0; i < 5; i++) {
        require(manager.startPlugin(ID) == PluginState.FAILED, "pre-refresh start did not fail");
        assertReleased(contexts);
        require(Integer.getInteger("sample.dispatch.plugin-destroys", 0) == destroyed + i + 1, "early destroy was missed or repeated");
        assertEarlyPortReleased();
      }
      System.setProperty("sample.pre-refresh.fail-destroy", "true");
      require(manager.startPlugin(ID) == PluginState.FAILED, "failed destruction start");
      org.springframework.context.ConfigurableApplicationContext retained = ((Pf4bootPlugin) manager.getPlugin(ID).getPlugin()).getPluginContext();
      require(retained != null && !retained.isActive(), "failed early destruction context lost");
      try (java.net.ServerSocket unexpected = new java.net.ServerSocket(Integer.getInteger("sample.pre-refresh.port"))) {
        throw new IllegalStateException("failed destruction unexpectedly released port " + unexpected.getLocalPort());
      } catch (java.net.BindException expected) { /* 失败的资源应保留到重试成功。 */ }
      require(manager.startPlugin(ID) == PluginState.FAILED, "unsafe restart accepted");
      require(((Pf4bootPlugin) manager.getPlugin(ID).getPlugin()).getPluginContext() == retained, "failed retry replaced context");
      System.clearProperty("sample.pre-refresh.fail-destroy");
      require(manager.stopPlugin(ID) == PluginState.STOPPED, "early destruction retry failed");
      assertReleased(contexts); assertEarlyPortReleased();
      require(Integer.getInteger("sample.dispatch.plugin-destroys", 0) == destroyed + 6, "early destruction retried successful callbacks");
    } finally {
      System.clearProperty("sample.pre-refresh.fail"); System.clearProperty("sample.pre-refresh.fail-destroy");
      try { manager.stopPlugin(ID); } finally { System.clearProperty("sample.pre-refresh.port"); }
    }
    require(manager.startPlugin(ID) == PluginState.STARTED, "pre-refresh recovery start failed");
    Map<String, Object> result = new LinkedHashMap<>(); result.put("failedStarts", 5);
    result.put("earlyResourceRelease", "PASSED"); result.put("destructionRetry", "PASSED"); return result;
  }

  private void assertEarlyPortReleased() throws java.io.IOException {
    try (java.net.ServerSocket rebound = new java.net.ServerSocket(Integer.getInteger("sample.pre-refresh.port"))) {
      require(rebound.isBound(), "early resource port not released");
    }
  }

  private WeakReference<ClassLoader> reloadAndObserve() {
    java.net.ServerSocket previousSocket = extensionSocket();
    WeakReference<ClassLoader> loader = new WeakReference<>(manager.getPlugin(ID).getPluginClassLoader());
    System.clearProperty("sample.extension.port");
    require(manager.reloadPlugin(ID) == PluginState.STARTED, "reload failed");
    require(previousSocket == null || previousSocket.isClosed(), "reload retained old extension socket");
    return loader;
  }

  private PluginState startProbePlugin() {
    System.clearProperty("sample.extension.port");
    return manager.startPlugin(ID);
  }

  private java.net.ServerSocket extensionSocket() {
    org.springframework.context.ConfigurableApplicationContext context = ((Pf4bootPlugin) manager.getPlugin(ID).getPlugin()).getPluginContext();
    String name = "net.xdob.sample.unrelated.CleanupProbeExtension";
    if (context == null || !context.containsLocalBean(name)) { return null; }
    Object extension = context.getBean(name);
    java.lang.reflect.Field field = org.springframework.util.ReflectionUtils.findField(extension.getClass(), "socket");
    require(field != null, "extension socket fixture missing");
    org.springframework.util.ReflectionUtils.makeAccessible(field);
    return (java.net.ServerSocket) org.springframework.util.ReflectionUtils.getField(field, extension);
  }

  private void assertReleased(int contexts) {
    require(((Pf4bootPlugin) manager.getPlugin(ID).getPlugin()).getPluginContext() == null, "context retained");
    require(ApplicationContextProvider.getRegisteredContextCount() == contexts, "global context retained");
    require(mapping.getRegisteredHandlerCount(ID) == 0, "handlers retained");
    require(mapping.getRegisteredInterceptorCount(ID) == 0, "interceptors retained");
    require(mapping.getInFlightRequestCount(ID) == 0, "requests retained");
    require(sharing.getScheduledTaskCount(ID) == 0, "scheduled tasks retained");
    require(sharing.getRunningScheduledTaskCount(ID) == 0, "running tasks retained");
    require(sharing.getRegisteredSharingBeanCount(ID) == 0, "shared beans retained");
    require(sharing.getRegisteredExtensionBeanCount(ID) == 0, "extensions retained");
  }

  private int remaining(List<WeakReference<ClassLoader>> loaders) {
    return (int) loaders.stream().filter(reference -> reference.get() != null).count();
  }

  private void require(boolean condition, String message) {
    if (!condition) {
      org.slf4j.LoggerFactory.getLogger(getClass()).error("Lifecycle probe failed: {}", message);
      throw new IllegalStateException(message);
    }
  }
}
