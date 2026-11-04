package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** 共享调度器中的部分注册与已出队任务不能跨插件生命周期残留。 */
public class PluginScheduledRollbackTest {
  @Component public static class BadDelay {
    @Scheduled(fixedDelay = 1000000, fixedRate = 1000000, initialDelay = 60000) public void run() { }
  }
  @Component public static class BadRate {
    @Scheduled(fixedRate = 1000000, fixedRateString = "1000000", initialDelay = 60000) public void run() { }
  }
  @Component public static class Work implements DisposableBean {
    final AtomicInteger calls = new AtomicInteger();
    final CountDownLatch invoked = new CountDownLatch(1);
    volatile boolean closed;
    @Scheduled(fixedDelay = 1000000) public void run() { calls.incrementAndGet(); invoked.countDown(); }
    public void destroy() { closed = true; }
  }
  @PluginStarter(BadDelay.class) public static class BadDelayPlugin extends Pf4bootPlugin { public BadDelayPlugin(PluginWrapper w) { super(w); } }
  @PluginStarter(BadRate.class) public static class BadRatePlugin extends Pf4bootPlugin { public BadRatePlugin(PluginWrapper w) { super(w); } }
  @PluginStarter(Work.class) public static class WorkPlugin extends Pf4bootPlugin { public WorkPlugin(PluginWrapper w) { super(w); } }
  private static class Manager extends Pf4bootPluginManagerImpl {
    final DefaultShareBeanMgr sharing;
    Manager(AnnotationConfigApplicationContext host, Path root, DefaultShareBeanMgr sharing) {
      super(host, new Pf4bootProperties(), new Pf4bootPluginSupport(){}, sharing, root); this.sharing = sharing;
    }
    void add(Class<? extends Pf4bootPlugin> type) {
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor("schedule", "schedule", type.getName(), "1", "", "test", "Apache-2.0");
      PluginWrapper wrapper = new Pf4bootPluginWrapper(this, descriptor, getPluginsRoot().resolve("schedule"), new URLClassLoader(new URL[0], type.getClassLoader()));
      wrapper.setPluginFactory(getPluginFactory()); wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put("schedule", wrapper); resolvedPlugins.add(wrapper); pluginClassLoaders.put("schedule", wrapper.getPluginClassLoader());
      dependencyResolver = new DependencyResolver(getVersionManager()); dependencyResolver.resolve(Collections.singletonList(descriptor));
    }
  }
  private static class Shared extends ThreadPoolTaskScheduler {
    final AtomicBoolean gate = new AtomicBoolean();
    final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) {
      if (!gate.compareAndSet(true, false)) { return super.scheduleWithFixedDelay(task, delay); }
      return super.scheduleWithFixedDelay(() -> {
        entered.countDown();
        try { if (!release.await(3, TimeUnit.SECONDS)) { throw new IllegalStateException("gate timeout"); } task.run(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { finished.countDown(); }
      }, delay);
    }
  }
  private static class Fixture implements AutoCloseable {
    final AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
    final Shared shared = new Shared();
    final Manager manager;
    Fixture(Class<? extends Pf4bootPlugin> type) throws Exception {
      shared.setPoolSize(2); shared.setRemoveOnCancelPolicy(true); shared.initialize();
      host.registerBean("metadataReaderFactory", MetadataReaderFactory.class, () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader())); host.refresh();
      manager = new Manager(host, Files.createTempDirectory("pf4boot-scheduling-rollback"), new DefaultShareBeanMgr(new DefaultAutoExportMgr()));
      manager.registerBeanToRootContext("sharedScheduler", shared); manager.add(type);
    }
    public void close() throws Exception {
      shared.release.countDown();
      try { manager.close(); }
      finally { host.close(); shared.shutdown(); assertTrue(shared.getScheduledThreadPoolExecutor().awaitTermination(3, TimeUnit.SECONDS)); }
    }
    Work work() { return ((Pf4bootPlugin) manager.getPlugin("schedule").getPlugin()).getPluginContext().getBean(Work.class); }
  }
  @Test public void conflictingDelayDoesNotLeakTasksOrCancelHostWork() throws Exception { verifyRollback(BadDelayPlugin.class); }
  @Test public void conflictingRateDoesNotLeakTasksOrCancelHostWork() throws Exception { verifyRollback(BadRatePlugin.class); }
  private void verifyRollback(Class<? extends Pf4bootPlugin> type) throws Exception {
    try (Fixture f = new Fixture(type)) {
      ScheduledFuture<?> hostWork = f.shared.schedule(() -> { }, new Date(System.currentTimeMillis() + 3600000));
      try {
        for (int i = 0; i < 5; i++) {
          assertEquals(PluginState.FAILED, f.manager.startPlugin("schedule"));
          assertNull(((Pf4bootPlugin) f.manager.getPlugin("schedule").getPlugin()).getPluginContext());
          assertEquals(0, f.manager.sharing.getScheduledTaskCount("schedule"));
          assertEquals(1, f.shared.getScheduledThreadPoolExecutor().getQueue().size());
          assertFalse(hostWork.isCancelled()); assertFalse(f.shared.getScheduledThreadPoolExecutor().isShutdown());
        }
        assertTrue(f.manager.unloadPlugin("schedule"));
        assertEquals(1, f.shared.getScheduledThreadPoolExecutor().getQueue().size()); assertFalse(hostWork.isCancelled());
      } finally { hostWork.cancel(false); }
    }
  }
  @Test public void dequeuedTaskCannotExecuteDestroyedBeanAfterRestart() throws Exception { verifyStaleWork(true); }
  @Test public void dequeuedTaskCannotExecuteDestroyedBeanAfterUnload() throws Exception { verifyStaleWork(false); }
  private void verifyStaleWork(boolean restart) throws Exception {
    try (Fixture f = new Fixture(WorkPlugin.class)) {
      f.shared.gate.set(true); assertEquals(PluginState.STARTED, f.manager.startPlugin("schedule"));
      assertTrue(f.shared.entered.await(2, TimeUnit.SECONDS)); Work original = f.work();
      ClassLoader loader = f.manager.getPlugin("schedule").getPluginClassLoader();
      if (restart) {
        assertEquals(PluginState.STOPPED, f.manager.stopPlugin("schedule"));
        assertEquals(PluginState.STARTED, f.manager.startPlugin("schedule"));
        assertNotSame(original, f.work()); assertTrue(f.work().invoked.await(2, TimeUnit.SECONDS));
      } else { assertTrue(f.manager.unloadPlugin("schedule")); }
      assertTrue(original.closed); f.shared.release.countDown(); assertTrue(f.shared.finished.await(2, TimeUnit.SECONDS));
      assertEquals("retired task invoked a destroyed bean", 0, original.calls.get());
      if (!restart) { assertNull(f.manager.getPlugin("schedule")); assertFalse(ApplicationContextProvider.containsApplicationContext(loader)); }
    }
  }
}
