package net.xdob.sample.host;

import net.xdob.pf4boot.*;
import org.pf4j.PluginState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 共享 scheduler 的失败启动回滚及旧任务跨生命周期准入验收，仅 smoke 启用。 */
@RestController
@ConditionalOnProperty(name = "sample.lifecycle-probe-enabled", havingValue = "true")
public class SampleSchedulingBoundaryProbe {
  private static final String ID = "sample-unrelated-service";
  private static final String SCHEDULER = "sampleSharedSchedulingProbe";
  private final Pf4bootPluginManager manager;
  private final DefaultShareBeanMgr sharing;
  private final String token;
  public SampleSchedulingBoundaryProbe(@Lazy Pf4bootPluginManager manager, DefaultShareBeanMgr sharing,
      @Value("${spring.pf4boot.management.http.token:}") String token) {
    this.manager = manager; this.sharing = sharing; this.token = token;
  }
  private static class SharedScheduler extends ThreadPoolTaskScheduler {
    final AtomicBoolean gate = new AtomicBoolean();
    final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) {
      if (!gate.compareAndSet(true, false)) { return super.scheduleWithFixedDelay(task, delay); }
      return super.scheduleWithFixedDelay(() -> {
        entered.countDown();
        try { if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("shared scheduler gate timeout"); } task.run(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { finished.countDown(); }
      }, delay);
    }
  }
  @PostMapping("/api/sample/scheduling-boundaries")
  public synchronized Map<String, Object> verify(@RequestHeader("X-PF4Boot-Admin-Token") String supplied) throws Exception {
    if (token.isEmpty() || !token.equals(supplied)) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    SharedScheduler scheduler = new SharedScheduler(); scheduler.setPoolSize(2); scheduler.setRemoveOnCancelPolicy(true); scheduler.initialize();
    ScheduledFuture<?> hostWork = scheduler.schedule(() -> { }, new Date(System.currentTimeMillis() + 3600000));
    try {
      require(manager.stopPlugin(ID) == PluginState.STOPPED, "initial scheduling stop");
      manager.registerBeanToRootContext(SCHEDULER, scheduler);
      verifyFailedRegistration(scheduler, hostWork);
      verifyStaleWork(scheduler);
      require(manager.stopPlugin(ID) == PluginState.STOPPED, "final shared scheduling stop");
      require(sharing.getScheduledTaskCount(ID) == 0 && scheduler.getScheduledThreadPoolExecutor().getQueue().size() == 1, "plugin tasks remain in shared scheduler");
      require(!hostWork.isCancelled() && !scheduler.getScheduledThreadPoolExecutor().isShutdown(), "plugin cleanup changed host work");
    } finally {
      scheduler.release.countDown();
      for (String property : Arrays.asList("sample.scheduling.invalid", "sample.scheduling.stale-probe", "sample.scheduling.after-close", "sample.scheduling.called")) { System.clearProperty(property); }
      try {
        if (manager.getPlugin(ID).getPluginState() == PluginState.STARTED) { manager.stopPlugin(ID); }
        manager.unregisterBeanFromRootContext(SCHEDULER);
      } finally {
        hostWork.cancel(false); scheduler.shutdown();
        require(scheduler.getScheduledThreadPoolExecutor().awaitTermination(3, TimeUnit.SECONDS), "shared scheduler worker did not exit");
      }
    }
    require(manager.startPlugin(ID) == PluginState.STARTED, "default scheduling restart");
    Map<String, Object> result = new LinkedHashMap<>(); result.put("invalidStarts", 5);
    result.put("schedulingRollback", "PASSED"); result.put("staleTaskAdmission", "PASSED"); result.put("hostWorkPreserved", "PASSED"); return result;
  }
  private void verifyFailedRegistration(SharedScheduler scheduler, ScheduledFuture<?> hostWork) {
    System.setProperty("sample.scheduling.invalid", "true");
    try {
      for (int i = 0; i < 5; i++) {
        require(manager.startPlugin(ID) == PluginState.FAILED, "conflicting schedule accepted");
        require(((Pf4bootPlugin) manager.getPlugin(ID).getPlugin()).getPluginContext() == null, "failed scheduling context retained");
        require(sharing.getScheduledTaskCount(ID) == 0 && scheduler.getScheduledThreadPoolExecutor().getQueue().size() == 1, "failed scheduling left a future");
        require(!hostWork.isCancelled(), "failed scheduling cancelled foreign work");
      }
    } finally { System.clearProperty("sample.scheduling.invalid"); }
  }
  private void verifyStaleWork(SharedScheduler scheduler) throws Exception {
    System.setProperty("sample.scheduling.stale-probe", "true");
    System.clearProperty("sample.scheduling.after-close"); System.clearProperty("sample.scheduling.called");
    scheduler.gate.set(true);
    require(manager.startPlugin(ID) == PluginState.STARTED, "scheduled probe start");
    require(scheduler.entered.await(2, TimeUnit.SECONDS), "old scheduled work not dequeued");
    require(manager.stopPlugin(ID) == PluginState.STOPPED, "dequeued work stop");
    require(manager.startPlugin(ID) == PluginState.STARTED, "scheduled probe restart");
    scheduler.release.countDown(); require(scheduler.finished.await(2, TimeUnit.SECONDS), "old scheduled wrapper did not exit");
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (!Boolean.getBoolean("sample.scheduling.called")) {
      require(System.nanoTime() < deadline, "new lifecycle task did not run"); Thread.sleep(10);
    }
    require(!Boolean.getBoolean("sample.scheduling.after-close"), "old scheduled task ran after bean destruction");
  }
  private void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
}
