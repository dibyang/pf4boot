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
      @RequestHeader("X-PF4Boot-Admin-Token") String suppliedToken) throws InterruptedException {
    if (token.isEmpty() || !token.equals(suppliedToken)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    int contexts = ApplicationContextProvider.getRegisteredContextCount();
    int handlers = mapping.getRegisteredHandlerCount(ID);
    require(handlers > 0, "initial plugin handlers missing");
    for (int i = 0; i < 20; i++) {
      manager.stopPlugin(ID);
      assertReleased(contexts - 1);
      require(manager.startPlugin(ID) == PluginState.STARTED, "restart failed");
      require(mapping.getRegisteredHandlerCount(ID) == handlers, "handler count grew");
    }
    verifyFailures(contexts);
    org.slf4j.LoggerFactory.getLogger(getClass()).info("Lifecycle probe restarts and failure recovery passed");
    List<WeakReference<ClassLoader>> loaders = new ArrayList<>();
    // 在线程退出后检查 GC，避免最后一次 reload 的临时栈引用影响验收。
    java.util.concurrent.atomic.AtomicReference<Throwable> reloadFailure = new java.util.concurrent.atomic.AtomicReference<>();
    Thread reloader = new Thread(() -> {
      try {
        for (int i = 0; i < 10; i++) {
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
    return result;
  }

  private void verifyFailures(int contexts) {
    failStop = true;
    try {
      try {
        manager.stopPlugin(ID);
        throw new AssertionError("injected stop failure was ignored");
      } catch (org.pf4j.PluginRuntimeException expected) {
        require(manager.getPlugin(ID).getPluginState() == PluginState.FAILED, "stop did not fail closed");
      }
      assertReleased(contexts - 1);
    } finally {
      failStop = false;
    }
    require(manager.startPlugin(ID) == PluginState.STARTED, "cleanup retry failed");
    manager.stopPlugin(ID);
    failStart = true;
    try {
      require(manager.startPlugin(ID) == PluginState.FAILED, "injected start did not fail");
      assertReleased(contexts - 1);
    } finally {
      failStart = false;
    }
    require(manager.startPlugin(ID) == PluginState.STARTED, "failed-start recovery failed");
  }

  private WeakReference<ClassLoader> reloadAndObserve() {
    WeakReference<ClassLoader> loader = new WeakReference<>(manager.getPlugin(ID).getPluginClassLoader());
    require(manager.reloadPlugin(ID) == PluginState.STARTED, "reload failed");
    return loader;
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
