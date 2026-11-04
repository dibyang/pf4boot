package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

/** 关闭必须冻结激活入口，并在所有插件清理成功后才释放父上下文。 */
public class PluginManagerCloseTest {
  public static class Config { }
  @PluginStarter(Config.class)
  public static class SocketPlugin extends Pf4bootPlugin implements DisposableBean {
    ServerSocket socket;
    int destroys;
    public SocketPlugin(PluginWrapper wrapper) { super(wrapper); }
    public void start() { try { socket = new ServerSocket(0); } catch (Exception e) { throw new IllegalStateException(e); } }
    public void destroy() throws Exception { destroys++; socket.close(); }
  }
  @PluginStarter(Config.class)
  public static class FaultPlugin extends SocketPlugin {
    boolean fail = true;
    public FaultPlugin(PluginWrapper wrapper) { super(wrapper); }
    public void stop() { if (fail) { throw new IllegalStateException("stop failure"); } }
  }
  @PluginStarter(Config.class)
  public static class ClosingPlugin extends SocketPlugin {
    static final AtomicInteger rejected = new AtomicInteger();
    public ClosingPlugin(PluginWrapper wrapper) { super(wrapper); }
    public void start() {
      try { ((Pf4bootPluginManagerImpl) getPluginManager()).close(); throw new AssertionError("activation closed manager"); }
      catch (PluginRuntimeException expected) { rejected.incrementAndGet(); }
      super.start();
    }
  }
  @Component
  public static class BlockingTask {
    static CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    static Runnable afterRelease = () -> { };
    @Scheduled(fixedDelay = 1000000) public void run() throws InterruptedException { entered.countDown(); release.await(); afterRelease.run(); }
  }
  @PluginStarter(BlockingTask.class)
  public static class BlockingPlugin extends SocketPlugin { public BlockingPlugin(PluginWrapper wrapper) { super(wrapper); } }
  private static class Manager extends Pf4bootPluginManagerImpl {
    final Path root;
    Manager(AnnotationConfigApplicationContext host, Pf4bootProperties props, Path root) {
      super(host, props, new Pf4bootPluginSupport(){}, new DefaultShareBeanMgr(new DefaultAutoExportMgr()), root); this.root = root;
    }
    void add(String id, Class<? extends Pf4bootPlugin> type, String... dependencies) {
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(id, id, type.getName(), "1", "", "test", "Apache-2.0");
      for (String dependency : dependencies) { descriptor.getDependencies().add(new PluginDependency(dependency)); }
      Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(this, descriptor, root.resolve(id), new ClassLoader(getClass().getClassLoader()){});
      wrapper.setPluginFactory(getPluginFactory()); wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put(id, wrapper); resolvedPlugins.add(wrapper); pluginClassLoaders.put(id, wrapper.getPluginClassLoader());
      dependencyResolver = new DependencyResolver(getVersionManager());
      dependencyResolver.resolve(plugins.values().stream().map(PluginWrapper::getDescriptor).collect(Collectors.toList()));
    }
  }
  private static class Fixture implements AutoCloseable {
    final AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
    final Pf4bootProperties props = new Pf4bootProperties();
    final Manager manager;
    Fixture() throws Exception {
      host.registerBean("metadataReaderFactory", MetadataReaderFactory.class, () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader())); host.refresh();
      props.setPluginDrainTimeoutMillis(30); manager = new Manager(host, props, Files.createTempDirectory("pf4boot-manager-close"));
    }
    public void close() { try { manager.close(); } finally { host.close(); } }
  }
  @Test public void closeCallbacksCannotRestartAndCompletedCloseIsIdempotent() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("socket", SocketPlugin.class); f.manager.startPlugin("socket");
      SocketPlugin plugin = (SocketPlugin) f.manager.getPlugin("socket").getPlugin();
      ClassLoader loader = plugin.getWrapper().getPluginClassLoader(); int port = plugin.socket.getLocalPort();
      org.springframework.context.ConfigurableApplicationContext group = f.manager.getPlatformContext("default");
      AtomicInteger rejected = new AtomicInteger();
      group.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> {
        if (event.getApplicationContext() == group) { expectClosed(() -> f.manager.startPlugin("socket")); rejected.incrementAndGet(); }
      });
      f.manager.close(); f.manager.close();
      assertEquals(1, rejected.get()); assertEquals(PluginState.STOPPED, plugin.getWrapper().getPluginState());
      assertNull(plugin.getPluginContext()); assertFalse(ApplicationContextProvider.containsApplicationContext(loader));
      assertTrue(plugin.socket.isClosed()); assertEquals(1, plugin.destroys); assertNull(f.manager.getRootContext());
      try (ServerSocket rebound = new ServerSocket(port)) { assertTrue(rebound.isBound()); }
      expectClosed(f.manager::startPlugins); expectClosed(() -> f.manager.restartPlugin("socket"));
      expectClosed(f.manager::restartPlugins); expectClosed(() -> f.manager.reloadPlugin("socket"));
      expectClosed(() -> f.manager.reloadPlugins(false)); expectClosed(f.manager::loadPlugins);
      expectClosed(() -> f.manager.loadPlugin(f.manager.root.resolve("unused.zip")));
      expectClosed(() -> f.manager.upgradePlugin("socket", f.manager.root, f.manager.root));
      expectClosed(() -> f.manager.getPlatformContext("new-group")); expectClosed(() -> f.manager.setApplicationStarted(true));
      assertEquals(PluginState.STOPPED, f.manager.stopPlugin("socket"));
    }
  }
  @Test public void drainFailureKeepsParentsUntilCloseRetry() throws Exception {
    BlockingTask.entered = new CountDownLatch(1); BlockingTask.release = new CountDownLatch(1);
    try (Fixture f = new Fixture()) {
      f.manager.add("blocked", BlockingPlugin.class); f.manager.startPlugin("blocked");
      SocketPlugin plugin = (SocketPlugin) f.manager.getPlugin("blocked").getPlugin();
      org.springframework.context.ConfigurableApplicationContext group = f.manager.getPlatformContext("default");
      ScheduledExecutorService scheduler = f.manager.getScheduled();
      try {
        assertTrue(BlockingTask.entered.await(2, TimeUnit.SECONDS));
        expectCloseFailure(f.manager);
        assertTrue(group.isActive()); assertTrue(f.manager.getRootContext().isActive());
        assertTrue(plugin.getPluginContext().isActive()); assertFalse(plugin.socket.isClosed());
        assertTrue(scheduler.isShutdown()); assertFalse(f.manager.isApplicationStarted());
        expectClosed(() -> f.manager.startPlugin("blocked"));
      } finally { BlockingTask.release.countDown(); f.props.setPluginDrainTimeoutMillis(2000); }
      f.manager.close(); assertFalse(group.isActive()); assertTrue(plugin.socket.isClosed());
      assertFalse(ApplicationContextProvider.containsApplicationContext(plugin.getWrapper().getPluginClassLoader()));
    }
  }
  @Test public void cleanupFailureKeepsParentsAndRetriesOnlyFailedStep() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("fault", FaultPlugin.class); f.manager.startPlugin("fault");
      FaultPlugin plugin = (FaultPlugin) f.manager.getPlugin("fault").getPlugin();
      org.springframework.context.ConfigurableApplicationContext group = f.manager.getPlatformContext("default");
      try {
        expectCloseFailure(f.manager); assertTrue(group.isActive()); assertTrue(f.manager.getRootContext().isActive());
        assertEquals(PluginState.FAILED, plugin.getWrapper().getPluginState()); assertEquals(1, plugin.destroys);
        expectClosed(() -> f.manager.startPlugin("fault"));
      } finally { plugin.fail = false; }
      f.manager.close(); assertFalse(group.isActive()); assertEquals(1, plugin.destroys);
    }
  }
  @Test public void drainingWorkCanStillReadItsExistingPlatformContext() throws Exception {
    BlockingTask.entered = new CountDownLatch(1); BlockingTask.release = new CountDownLatch(1);
    try (Fixture f = new Fixture()) {
      f.props.setPluginDrainTimeoutMillis(1000);
      f.manager.add("lookup", BlockingPlugin.class); f.manager.startPlugin("lookup");
      org.springframework.context.ConfigurableApplicationContext group = f.manager.getPlatformContext("default");
      AtomicBoolean lookedUp = new AtomicBoolean();
      BlockingTask.afterRelease = () -> { assertSame(group, f.manager.getPlatformContext("default")); assertTrue(group.isActive()); lookedUp.set(true); };
      CountDownLatch draining = new CountDownLatch(1);
      f.host.getBeanFactory().registerSingleton("closeDrainMarker", new net.xdob.pf4boot.deployment.PluginTrafficDrainer() {
        public void beginDrain(Collection<String> ids) { draining.countDown(); }
        public boolean awaitDrain(Collection<String> ids, long timeout) { return true; }
        public void endDrain(Collection<String> ids) { }
      });
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        assertTrue(BlockingTask.entered.await(2, TimeUnit.SECONDS));
        Future<?> close = pool.submit(f.manager::close);
        assertTrue(draining.await(2, TimeUnit.SECONDS)); BlockingTask.release.countDown();
        close.get(3, TimeUnit.SECONDS); assertTrue(lookedUp.get()); assertFalse(group.isActive());
      } finally {
        BlockingTask.release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        BlockingTask.afterRelease = () -> { };
      }
    }
  }
  @Test public void concurrentStartWaitsForShutdownThenIsRejected() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("socket", SocketPlugin.class); f.manager.startPlugin("socket");
      org.springframework.context.ConfigurableApplicationContext group = f.manager.getPlatformContext("default");
      CountDownLatch closing = new CountDownLatch(1), release = new CountDownLatch(1), attempted = new CountDownLatch(1);
      group.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> {
        if (event.getApplicationContext() == group) {
          closing.countDown();
          try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
          catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }
      });
      ExecutorService pool = Executors.newFixedThreadPool(2);
      Future<?> close = pool.submit(f.manager::close);
      try {
        assertTrue(closing.await(2, TimeUnit.SECONDS));
        Future<?> start = pool.submit(() -> { attempted.countDown(); expectClosed(() -> f.manager.startPlugin("socket")); });
        assertTrue(attempted.await(2, TimeUnit.SECONDS));
        // 不依赖不同 JVM 的瞬时线程状态，关闭屏障未释放时启动不能完成。
        try { start.get(100, TimeUnit.MILLISECONDS); fail("start crossed the close barrier"); }
        catch (TimeoutException expected) { }
        release.countDown(); close.get(3, TimeUnit.SECONDS); start.get(3, TimeUnit.SECONDS);
      } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS)); }
      assertEquals(PluginState.STOPPED, f.manager.getPlugin("socket").getPluginState());
    }
  }
  @Test public void activationCallbackCloseIsRejectedWithoutPoisoningManager() throws Exception {
    try (Fixture f = new Fixture()) {
      ClosingPlugin.rejected.set(0); f.manager.add("base", ClosingPlugin.class); f.manager.add("leaf", ClosingPlugin.class, "base");
      assertEquals(PluginState.STARTED, f.manager.startPlugin("leaf")); assertEquals(2, ClosingPlugin.rejected.get());
      f.manager.add("other", SocketPlugin.class); assertEquals(PluginState.STARTED, f.manager.startPlugin("other"));
      f.manager.close(); assertNull(f.manager.getRootContext());
    }
  }
  @Test public void dequeuedAutoStartTaskDoesNothingAfterClose() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("candidate", SocketPlugin.class);
      class CapturingScheduler extends ScheduledThreadPoolExecutor {
        Runnable captured;
        CapturingScheduler() { super(1); }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initial, long delay, TimeUnit unit) {
          captured = command; return super.scheduleWithFixedDelay(command, 1, 1, TimeUnit.DAYS);
        }
      }
      CapturingScheduler scheduler = new CapturingScheduler(); f.manager.getScheduled().shutdownNow();
      java.lang.reflect.Field field = Pf4bootPluginManagerImpl.class.getDeclaredField("scheduled"); field.setAccessible(true); field.set(f.manager, scheduler);
      f.manager.setApplicationStarted(true); assertNotNull(scheduler.captured);
      f.manager.close(); scheduler.captured.run();
      assertTrue(scheduler.isShutdown()); assertFalse(f.manager.isApplicationStarted());
      assertEquals(PluginState.RESOLVED, f.manager.getPlugin("candidate").getPluginState()); assertTrue(f.manager.getStartedPlugins().isEmpty());
    }
  }
  private static void expectCloseFailure(Manager manager) {
    try { manager.close(); fail("incomplete cleanup must not close parents"); }
    catch (PluginRuntimeException expected) { assertTrue(expected.getMessage().contains("cleanup")); }
  }
  private static void expectClosed(Runnable action) {
    try { action.run(); fail("closed manager admitted activation"); }
    catch (PluginRuntimeException expected) { assertTrue(expected.getMessage().contains("closing")); }
  }
}
