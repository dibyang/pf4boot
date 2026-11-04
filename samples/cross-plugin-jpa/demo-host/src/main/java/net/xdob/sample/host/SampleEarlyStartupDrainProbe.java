package net.xdob.sample.host;

import net.xdob.pf4boot.*;
import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import net.xdob.pf4boot.deployment.PluginWorkTracker;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** 仅 smoke 启用：上下文创建前失败仍须排空真实工作并释放端口。 */
@RestController
@ConditionalOnProperty(name = "sample.lifecycle-probe-enabled", havingValue = "true")
public class SampleEarlyStartupDrainProbe implements PluginTrafficDrainer {
  private static final String ID = "sample-unrelated-service", WORK = "sampleEarlyStartupWork";
  private final PluginWorkTracker tracker = new PluginWorkTracker();
  private final Pf4bootPluginManager manager;
  private final Pf4bootProperties properties;
  private final String token;
  public SampleEarlyStartupDrainProbe(@Lazy Pf4bootPluginManager manager, Pf4bootProperties properties,
      @Value("${spring.pf4boot.management.http.token:}") String token) {
    this.manager = manager; this.properties = properties; this.token = token;
  }
  public void beginDrain(Collection<String> ids) { tracker.beginDrain(ids); }
  public boolean awaitDrain(Collection<String> ids, long timeout) throws InterruptedException { return tracker.awaitDrain(ids, timeout); }
  public void endDrain(Collection<String> ids) { tracker.endDrain(ids); }

  @PostMapping("/api/sample/early-startup-drain")
  public synchronized Map<String, Object> verify(@RequestHeader("X-PF4Boot-Admin-Token") String supplied) throws Exception {
    if (token.isEmpty() || !token.equals(supplied)) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    require(manager.stopPlugin(ID) == PluginState.STOPPED, "initial early startup stop");
    int contexts = ApplicationContextProvider.getRegisteredContextCount();
    long timeout = properties.getPluginDrainTimeoutMillis();
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    AtomicReference<Future<?>> task = new AtomicReference<>();
    Consumer<Pf4bootPlugin> work = plugin -> {
      task.set(pool.submit(() -> {
        require(tracker.tryEnter(ID), "early work admission failed"); entered.countDown();
        try { require(release.await(10, TimeUnit.SECONDS), "early work release timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        finally { tracker.exit(ID); }
      }));
      try { require(entered.await(2, TimeUnit.SECONDS), "early worker did not enter"); }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    };
    try {
      properties.setPluginDrainTimeoutMillis(50); manager.registerBeanToRootContext(WORK, work);
      System.setProperty("sample.startup-drain.fail", "true");
      require(manager.startPlugin(ID) == PluginState.FAILED, "early startup should fail");
      require(((Pf4bootPlugin) manager.getPlugin(ID).getPlugin()).getPluginContext() == null && tracker.getRunningCount(ID) == 1, "early failure did not precede context creation");
      require(manager.startPlugin(ID) == PluginState.FAILED, "pending startup cleanup bypassed");
      require(Integer.getInteger("sample.startup-drain.attempts", 0) == 1, "initiate repeated before drain");
      expectFailure(() -> manager.unloadPlugin(ID));
      require(manager.getPlugin(ID) != null, "early failure wrapper removed before drain");
      release.countDown(); task.get().get(3, TimeUnit.SECONDS);
      require(tracker.getRunningCount(ID) == 0, "early work count retained");
      require(manager.stopPlugin(ID) == PluginState.STOPPED, "early cleanup retry failed");
      require(Integer.getInteger("sample.startup-drain.stops", 0) == 1, "early resource not stopped exactly once");
      require(ApplicationContextProvider.getRegisteredContextCount() == contexts, "early context registration leaked");
      try (ServerSocket rebound = new ServerSocket(Integer.getInteger("sample.startup-drain.port"))) { require(rebound.isBound(), "early resource port retained"); }
    } finally {
      release.countDown(); pool.shutdown();
      try {
        require(pool.awaitTermination(3, TimeUnit.SECONDS), "early worker did not exit");
        System.clearProperty("sample.startup-drain.fail"); manager.stopPlugin(ID);
      } finally {
        for (String key : Arrays.asList("sample.startup-drain.fail", "sample.startup-drain.attempts", "sample.startup-drain.stops", "sample.startup-drain.port")) { System.clearProperty(key); }
        properties.setPluginDrainTimeoutMillis(timeout); manager.unregisterBeanFromRootContext(WORK);
      }
    }
    require(manager.startPlugin(ID) == PluginState.STARTED, "early failure recovery start failed");
    Map<String, Object> result = new LinkedHashMap<>(); result.put("inFlightDrain", "PASSED");
    result.put("activationRetry", "PASSED"); result.put("socketRecovery", "PASSED"); return result;
  }
  private void expectFailure(Runnable action) {
    try { action.run(); throw new IllegalStateException("un-drained early resource operation succeeded"); }
    catch (PluginRuntimeException expected) { }
  }
  private void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
}
