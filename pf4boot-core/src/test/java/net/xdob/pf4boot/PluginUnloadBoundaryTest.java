package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.*;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

/** 卸载范围、互斥和异常收尾必须同时保持资源所有权。 */
public class PluginUnloadBoundaryTest {
  public static class Config { }
  @PluginStarter(Config.class)
  public static class PlainPlugin extends Pf4bootPlugin {
    public PlainPlugin(PluginWrapper wrapper) { super(wrapper); }
  }
  @PluginStarter(Config.class)
  public static class SocketPlugin extends Pf4bootPlugin implements org.springframework.beans.factory.DisposableBean {
    ServerSocket socket;
    public SocketPlugin(PluginWrapper wrapper) { super(wrapper); }
    public void start() { try { socket = new ServerSocket(0); } catch (IOException e) { throw new IllegalStateException(e); } }
    public void destroy() throws IOException { if (socket != null) { socket.close(); } }
  }
  private static class Loader extends URLClassLoader {
    final String id;
    final List<String> order;
    boolean closed;
    boolean fail;
    boolean block;
    Throwable unchecked;
    final CountDownLatch closing = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    Loader(String id, List<String> order) { super(new URL[0], PlainPlugin.class.getClassLoader()); this.id = id; this.order = order; }
    public void close() throws IOException {
      if (closed) { throwUnchecked(); return; }
      if (block) {
        closing.countDown();
        try { if (!release.await(3, TimeUnit.SECONDS)) { throw new IOException("close barrier timeout"); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
      }
      if (fail) { throw new IOException("injected loader close failure"); }
      super.close(); closed = true; order.add(id);
      throwUnchecked();
    }
    private void throwUnchecked() {
      if (unchecked instanceof RuntimeException) { throw (RuntimeException) unchecked; }
      if (unchecked instanceof Error) { throw (Error) unchecked; }
    }
  }
  private static class Manager extends Pf4bootPluginManagerImpl {
    final List<String> closed = new ArrayList<>();
    final Path root;
    final Map<String, Loader> loaders = new HashMap<>();
    Manager(AnnotationConfigApplicationContext host, Pf4bootProperties props, Path root) {
      super(host, props, new Pf4bootPluginSupport(){}, new DefaultShareBeanMgr(new DefaultAutoExportMgr()), root);
      this.root = root;
    }
    void add(String id, Class<? extends Pf4bootPlugin> type, String... dependencies) {
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(id, id, type.getName(), "1", "", "test", "Apache-2.0");
      for (String dependency : dependencies) { descriptor.getDependencies().add(new PluginDependency(dependency)); }
      Loader loader = new Loader(id, closed); loaders.put(id, loader);
      Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(this, descriptor, root.resolve(id), loader);
      wrapper.setPluginFactory(getPluginFactory()); wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put(id, wrapper); resolvedPlugins.add(wrapper); pluginClassLoaders.put(id, loader);
      dependencyResolver = new DependencyResolver(getVersionManager());
      dependencyResolver.resolve(plugins.values().stream().map(PluginWrapper::getDescriptor).collect(Collectors.toList()));
    }
    boolean tracks(String id) { return pluginClassLoaders.containsKey(id); }
    boolean graphContainsConsumer(String id) { return !dependencyResolver.getDependents(id).isEmpty(); }
  }
  private static class Fixture implements AutoCloseable {
    final AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
    final Pf4bootProperties props = new Pf4bootProperties();
    final Manager manager;
    Fixture() throws Exception {
      host.registerBean("metadataReaderFactory", MetadataReaderFactory.class,
          () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader())); host.refresh();
      props.setPluginDrainTimeoutMillis(30);
      manager = new Manager(host, props, Files.createTempDirectory("pf4boot-unload-boundary"));
    }
    public void close() throws Exception {
      for (Loader loader : manager.loaders.values()) { loader.fail = false; loader.unchecked = null; loader.release.countDown(); }
      try { manager.close(); }
      finally { host.close(); for (Loader loader : manager.loaders.values()) { loader.close(); } }
    }
  }
  @Test public void blockedLeafPreservesEntireThreeLevelScope() throws Exception {
    Pf4bootPluginManagerLifecycleTest.BlockingTask.entered = new CountDownLatch(1);
    Pf4bootPluginManagerLifecycleTest.BlockingTask.release = new CountDownLatch(1);
    try (Fixture f = new Fixture()) {
      f.manager.add("base", PlainPlugin.class);
      f.manager.add("middle", PlainPlugin.class, "base");
      f.manager.add("leaf", Pf4bootPluginManagerLifecycleTest.BlockingPlugin.class, "middle");
      assertEquals(PluginState.STARTED, f.manager.startPlugin("leaf"));
      try {
        assertTrue(Pf4bootPluginManagerLifecycleTest.BlockingTask.entered.await(2, TimeUnit.SECONDS));
        expectUnloadFailure(f.manager, "base");
        for (String id : Arrays.asList("base", "middle", "leaf")) {
          assertEquals(PluginState.STARTED, f.manager.getPlugin(id).getPluginState());
          assertTrue(((Pf4bootPlugin) f.manager.getPlugin(id).getPlugin()).getPluginContext().isActive());
          assertFalse(f.manager.loaders.get(id).closed);
        }
      } finally { Pf4bootPluginManagerLifecycleTest.BlockingTask.release.countDown(); }
      f.props.setPluginDrainTimeoutMillis(2000);
      assertTrue(f.manager.unloadPlugin("base"));
      assertEquals(Arrays.asList("leaf", "middle", "base"), f.manager.closed);
      assertFalse(f.manager.graphContainsConsumer("base"));
    }
  }
  @Test public void diamondUnloadsEachConsumerBeforeBothProviders() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("base", PlainPlugin.class);
      f.manager.add("left", PlainPlugin.class, "base");
      f.manager.add("right", PlainPlugin.class, "base");
      f.manager.add("leaf", PlainPlugin.class, "left", "right");
      f.manager.startPlugin("leaf");
      assertTrue(f.manager.unloadPlugin("base"));
      assertEquals(4, f.manager.closed.size());
      assertEquals("leaf", f.manager.closed.get(0)); assertEquals("base", f.manager.closed.get(3));
      assertTrue(f.manager.getPlugins().isEmpty());
    }
  }
  @Test public void concurrentStartWaitsForCompleteUnload() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("race", PlainPlugin.class); f.manager.startPlugin("race");
      Loader loader = f.manager.loaders.get("race"); loader.block = true;
      ExecutorService pool = Executors.newFixedThreadPool(2);
      AtomicReference<Thread> starter = new AtomicReference<>();
      CountDownLatch attempted = new CountDownLatch(1);
      Future<Boolean> unload = pool.submit(() -> f.manager.unloadPlugin("race"));
      try {
        assertTrue(loader.closing.await(2, TimeUnit.SECONDS));
        Future<PluginState> start = pool.submit(() -> {
          starter.set(Thread.currentThread()); attempted.countDown(); return f.manager.startPlugin("race");
        });
        assertTrue(attempted.await(2, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!start.isDone() && starter.get().getState() != Thread.State.WAITING && System.nanoTime() < deadline) { Thread.yield(); }
        assertFalse("start crossed the unload lock", start.isDone());
        assertEquals(Thread.State.WAITING, starter.get().getState());
        loader.release.countDown(); assertTrue(unload.get(2, TimeUnit.SECONDS));
        try { start.get(2, TimeUnit.SECONDS); fail("removed plugin must not restart on its old loader"); }
        catch (ExecutionException expected) { assertTrue(expected.getCause() instanceof PluginNotFoundException); }
        assertTrue(loader.closed); assertNull(f.manager.getPlugin("race"));
      } finally { loader.release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS)); }
    }
  }
  @Test public void listenerFailureCannotSkipLoaderCloseOrGraphRefresh() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("base", PlainPlugin.class); f.manager.add("leaf", PlainPlugin.class, "base");
      f.manager.startPlugin("leaf");
      List<String> notified = new ArrayList<>();
      PluginStateListener listener = event -> {
        if (event.getPlugin().getPluginState() == PluginState.UNLOADED) {
          String id = event.getPlugin().getPluginId();
          assertTrue(f.manager.loaders.get(id).closed); notified.add(id);
          throw new IllegalStateException("injected notification failure");
        }
      };
      f.manager.addPluginStateListener(listener);
      try { expectUnloadFailure(f.manager, "base"); }
      finally { f.manager.removePluginStateListener(listener); }
      assertEquals(Arrays.asList("leaf", "base"), notified);
      assertEquals(notified, f.manager.closed);
      assertTrue(f.manager.getPlugins().isEmpty());
      assertFalse(f.manager.tracks("base")); assertFalse(f.manager.tracks("leaf"));
      assertFalse(f.manager.graphContainsConsumer("base"));
      assertFalse(f.manager.unloadPlugin("base"));
    }
  }
  @Test public void loaderCloseFailureRetainsRetryAndBlocksRestart() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("base", PlainPlugin.class); f.manager.add("leaf", PlainPlugin.class, "base");
      f.manager.startPlugin("leaf");
      PluginWrapper original = f.manager.getPlugin("base");
      f.manager.loaders.get("base").fail = true;
      expectUnloadFailure(f.manager, "base");
      assertSame(original, f.manager.getPlugin("base")); assertTrue(f.manager.tracks("base"));
      assertEquals(PluginState.FAILED, original.getPluginState());
      assertFalse(f.manager.loaders.get("base").closed); assertTrue(f.manager.loaders.get("leaf").closed);
      assertFalse(f.manager.graphContainsConsumer("base"));
      assertEquals(PluginState.FAILED, f.manager.startPlugin("base"));
      f.manager.loaders.get("base").fail = false;
      assertTrue(f.manager.unloadPlugin("base"));
      assertTrue(f.manager.loaders.get("base").closed);
      assertEquals(Arrays.asList("leaf", "base"), f.manager.closed);
    }
  }
  private static void expectUnloadFailure(Manager manager, String id) {
    try { manager.unloadPlugin(id); fail("unload failure must be reported"); }
    catch (PluginRuntimeException expected) { assertFalse(expected.getMessage().isEmpty()); }
  }

  @Test public void dynamicUnregisterEvictsOnlyItsStaleMergedDefinition() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.registerBeanToPlatformContext("", "removed", new Object());
      f.manager.registerBeanToPlatformContext("", "retained", new Object());
      org.springframework.beans.factory.support.DefaultListableBeanFactory factory =
          (org.springframework.beans.factory.support.DefaultListableBeanFactory) f.manager.getPlatformContext("").getBeanFactory();
      factory.getMergedBeanDefinition("removed");
      Object retained = factory.getMergedBeanDefinition("retained");
      java.lang.reflect.Field field = org.springframework.beans.factory.support.AbstractBeanFactory.class.getDeclaredField("mergedBeanDefinitions");
      field.setAccessible(true);
      Map<?, ?> merged = (Map<?, ?>) field.get(factory);
      assertTrue(merged.containsKey("removed"));
      f.manager.unregisterBeanFromPlatformContext("", "removed");
      assertFalse(merged.containsKey("removed")); assertSame(retained, merged.get("retained"));
      assertFalse(factory.containsBean("removed")); assertTrue(factory.containsBean("retained"));
    }
  }

  @Test public void unloadCallbacksCannotRestartPendingProviderAndLeakItsSocket() throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("base", SocketPlugin.class); f.manager.add("leaf", PlainPlugin.class, "base");
      f.manager.startPlugin("leaf");
      PluginWrapper original = f.manager.getPlugin("base");
      SocketPlugin provider = (SocketPlugin) original.getPlugin();
      int port = provider.socket.getLocalPort();
      List<String> callbacks = new ArrayList<>();
      PluginStateListener listener = event -> {
        if (event.getPlugin().getPluginState() == PluginState.UNLOADED) {
          callbacks.add(event.getPlugin().getPluginId());
          assertTrue(f.manager.loaders.get("base").closed); assertTrue(f.manager.loaders.get("leaf").closed);
          assertFalse(f.manager.graphContainsConsumer("base"));
          try { f.manager.startPlugin("base"); fail("provider must be absent before notification"); }
          catch (PluginNotFoundException expected) { }
        }
      };
      f.manager.addPluginStateListener(listener);
      try {
        assertTrue(f.manager.unloadPlugin("base"));
        assertEquals(Arrays.asList("leaf", "base"), callbacks);
        assertNull(provider.getPluginContext()); assertTrue(provider.socket.isClosed());
        assertFalse(ApplicationContextProvider.containsApplicationContext(original.getPluginClassLoader()));
        try (ServerSocket rebound = new ServerSocket(port)) { assertTrue(rebound.isBound()); }
      } finally {
        f.manager.removePluginStateListener(listener);
        if (provider.getPluginContext() != null) {
          ApplicationContextProvider.unregisterApplicationContext(provider.getPluginContext()); provider.closePluginContext();
          f.manager.getStartedPlugins().remove(original);
        }
      }
    }
  }

  @Test public void runtimeCloseFailureBlocksRestartOnPartiallyClosedLoader() throws Exception {
    verifyUncheckedCloseFailure(new IllegalStateException("unchecked close failure"));
  }

  @Test public void closeErrorBlocksRestartOnPartiallyClosedLoader() throws Exception {
    verifyUncheckedCloseFailure(new LinkageError("close error"));
  }

  private void verifyUncheckedCloseFailure(Throwable failure) throws Exception {
    try (Fixture f = new Fixture()) {
      f.manager.add("unchecked", PlainPlugin.class); f.manager.startPlugin("unchecked");
      PluginWrapper original = f.manager.getPlugin("unchecked");
      Loader loader = f.manager.loaders.get("unchecked"); loader.unchecked = failure;
      expectUnloadFailure(f.manager, "unchecked");
      assertSame(original, f.manager.getPlugin("unchecked")); assertTrue(f.manager.tracks("unchecked"));
      assertEquals(PluginState.FAILED, original.getPluginState()); assertSame(failure, original.getFailedException());
      assertTrue(loader.closed); assertEquals(PluginState.FAILED, f.manager.startPlugin("unchecked"));
      assertNull(((Pf4bootPlugin) original.getPlugin()).getPluginContext());
      loader.unchecked = null; assertTrue(f.manager.unloadPlugin("unchecked"));
      assertFalse(f.manager.tracks("unchecked")); assertEquals(Collections.singletonList("unchecked"), f.manager.closed);
    }
  }
}
