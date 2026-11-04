package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DependencyResolver;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Pf4bootPluginManagerLifecycleTest {

  private AnnotationConfigApplicationContext applicationContext;
  private TestPluginManager pluginManager;
  private Path pluginsRoot;

  @Before
  public void setUp() throws Exception {
    LifecyclePlugin.events.clear();
    FailingPlugin.events.clear();
    SlowStartPlugin.events.clear();

    applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.registerBean(
        "metadataReaderFactory",
        MetadataReaderFactory.class,
        () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader()));
    applicationContext.refresh();

    pluginsRoot = Files.createTempDirectory("pf4boot-lifecycle-test");
    pluginManager = new TestPluginManager(applicationContext, pluginsRoot);
  }

  @After
  public void tearDown() {
    if (pluginManager != null) {
      pluginManager.close();
    }
    if (applicationContext != null) {
      applicationContext.close();
    }
  }

  @Test
  public void startStopAndRestartRunLifecycleHooks() {
    pluginManager.addResolvedPlugin("sample", LifecyclePlugin.class);

    assertPluginState("sample", PluginState.STARTED, pluginManager.startPlugin("sample"));
    Pf4bootPlugin plugin = (Pf4bootPlugin) pluginManager.getPlugin("sample").getPlugin();
    ClassLoader contextClassLoader = plugin.getPluginContext().getClassLoader();
    assertTrue(ApplicationContextProvider.containsApplicationContext(contextClassLoader));
    assertEquals(PluginState.STOPPED, pluginManager.stopPlugin("sample"));
    assertFalse(ApplicationContextProvider.containsApplicationContext(contextClassLoader));
    assertEquals(PluginState.STARTED, pluginManager.restartPlugin("sample"));

    assertTrue(LifecyclePlugin.events.contains("sample:initiate"));
    assertTrue(LifecyclePlugin.events.contains("sample:start"));
    assertTrue(LifecyclePlugin.events.contains("sample:stop"));
    assertTrue(LifecyclePlugin.events.contains("sample:closed"));
  }

  @Test
  public void stoppingDependencyStopsDependentPluginFirst() {
    pluginManager.addResolvedPlugin("base", LifecyclePlugin.class);
    pluginManager.addResolvedPlugin("dependent", LifecyclePlugin.class, "base");
    pluginManager.rebuildDependencyResolver();

    assertPluginState("dependent", PluginState.STARTED, pluginManager.startPlugin("dependent"));
    pluginManager.stopPlugin("base");

    assertEquals(PluginState.FAILED, pluginManager.getPlugin("dependent").getPluginState());
    assertEquals(PluginState.STOPPED, pluginManager.getPlugin("base").getPluginState());
    assertTrue(LifecyclePlugin.events.indexOf("dependent:stop")
        < LifecyclePlugin.events.indexOf("base:stop"));
  }

  private void assertPluginState(String pluginId, PluginState expected, PluginState actual) {
    Throwable failure = pluginManager.getPlugin(pluginId).getFailedException();
    assertEquals(failure == null ? null : failure.toString(), expected, actual);
  }

  @Test
  public void failedStartClosesPluginContext() {
    int contextsBefore = ApplicationContextProvider.getRegisteredContextCount();
    pluginManager.addResolvedPlugin("failing", FailingPlugin.class);

    assertEquals(PluginState.FAILED, pluginManager.startPlugin("failing"));

    FailingPlugin plugin = (FailingPlugin) pluginManager.getPlugin("failing").getPlugin();
    assertNull(plugin.getPluginContext());
    assertTrue(FailingPlugin.events.contains("failing:start"));
    assertEquals(contextsBefore, ApplicationContextProvider.getRegisteredContextCount());
  }

  @Test
  public void failureBeforeRefreshDestroysRegisteredSingletonsAndAllowsRestart() throws Exception {
    pluginManager.addResolvedPlugin("before-refresh", BeforeRefreshPlugin.class);
    BeforeRefreshPlugin plugin = (BeforeRefreshPlugin) pluginManager.getPlugin("before-refresh").getPlugin();
    try {
      for (int i = 0; i < 5; i++) {
        assertEquals(PluginState.FAILED, pluginManager.startPlugin("before-refresh"));
        assertNull(plugin.getPluginContext());
        assertEquals(i + 1, plugin.destroys);
        try (java.net.ServerSocket rebound = new java.net.ServerSocket(plugin.port)) {
          assertTrue(rebound.isBound());
        }
      }
      plugin.failBeforeRefresh = false;
      assertEquals(PluginState.STARTED, pluginManager.startPlugin("before-refresh"));
      assertEquals(PluginState.STOPPED, pluginManager.stopPlugin("before-refresh"));
      assertEquals(6, plugin.destroys);
    } finally { plugin.closeSocket(); }
  }

  @Test
  public void failureBeforeRefreshRetainsFailedDestructionForRetry() throws Exception {
    pluginManager.addResolvedPlugin("before-refresh-retry", BeforeRefreshPlugin.class);
    BeforeRefreshPlugin plugin = (BeforeRefreshPlugin) pluginManager.getPlugin("before-refresh-retry").getPlugin();
    plugin.failDestroy = true;
    try {
      assertEquals(PluginState.FAILED, pluginManager.startPlugin("before-refresh-retry"));
      assertTrue(plugin.getPluginContext() != null);
      assertFalse(plugin.socket.isClosed());
      assertEquals(1, plugin.destroys);
      assertEquals(1, plugin.markerDestroyed);
      assertEquals(PluginState.FAILED, pluginManager.startPlugin("before-refresh-retry"));
      assertEquals(1, plugin.opens);
      assertEquals(2, plugin.destroys);
      assertEquals(1, plugin.markerDestroyed);
      plugin.failDestroy = false;
      assertEquals(PluginState.STOPPED, pluginManager.stopPlugin("before-refresh-retry"));
      assertNull(plugin.getPluginContext());
      assertTrue(plugin.socket.isClosed());
      assertEquals(3, plugin.destroys);
      assertEquals(1, plugin.markerDestroyed);
      plugin.failBeforeRefresh = false;
      assertEquals(PluginState.STARTED, pluginManager.startPlugin("before-refresh-retry"));
      assertEquals(PluginState.STOPPED, pluginManager.stopPlugin("before-refresh-retry"));
      assertEquals(4, plugin.destroys);
      assertEquals(2, plugin.markerDestroyed);
    } finally { plugin.failDestroy = false; plugin.closeSocket(); }
  }

  @Test
  public void fiftyRestartsReleaseContextsAndDestroyBeans() {
    int contextsBefore = ApplicationContextProvider.getRegisteredContextCount();
    Resource.closed.set(0);
    pluginManager.addResolvedPlugin("repeat", ResourcePlugin.class);
    for (int i = 0; i < 50; i++) {
      assertEquals(PluginState.STARTED, pluginManager.startPlugin("repeat"));
      Pf4bootPlugin plugin = (Pf4bootPlugin) pluginManager.getPlugin("repeat").getPlugin();
      ClassLoader loader = plugin.getPluginContext().getClassLoader();
      assertEquals(PluginState.STOPPED, pluginManager.stopPlugin("repeat"));
      assertNull(plugin.getPluginContext());
      assertFalse(ApplicationContextProvider.containsApplicationContext(loader));
      assertEquals(contextsBefore, ApplicationContextProvider.getRegisteredContextCount());
      assertEquals(i + 1, Resource.closed.get());
    }
  }

  @Test
  public void stopFailureStillClosesContextAndRetriesOnlyFailedStep() {
    pluginManager.addResolvedPlugin("stop-failure", StopFailurePlugin.class);
    pluginManager.startPlugin("stop-failure");
    StopFailurePlugin plugin = (StopFailurePlugin) pluginManager.getPlugin("stop-failure").getPlugin();
    ClassLoader loader = plugin.getPluginContext().getClassLoader();
    AtomicInteger releases = new AtomicInteger();
    plugin.addReleaseHook(ignored -> releases.incrementAndGet());
    plugin.fail = true;
    expectStopFailure("stop-failure");
    assertEquals(PluginState.FAILED, plugin.getWrapper().getPluginState());
    assertNull(plugin.getPluginContext());
    assertFalse(ApplicationContextProvider.containsApplicationContext(loader));
    assertEquals(1, releases.get());
    assertEquals(PluginState.FAILED, pluginManager.startPlugin("stop-failure"));
    plugin.fail = false;
    assertEquals(PluginState.STARTED, pluginManager.startPlugin("stop-failure"));
    assertEquals(1, releases.get());
  }

  @Test
  public void releaseHookFailureDoesNotSkipOtherHooksOrContextClose() {
    pluginManager.addResolvedPlugin("hook-failure", LifecyclePlugin.class);
    pluginManager.startPlugin("hook-failure");
    Pf4bootPlugin plugin = (Pf4bootPlugin) pluginManager.getPlugin("hook-failure").getPlugin();
    AtomicInteger attempts = new AtomicInteger();
    AtomicInteger successes = new AtomicInteger();
    plugin.addReleaseHook(ignored -> {
      if (attempts.incrementAndGet() == 1) {
        throw new IllegalStateException("first release fails");
      }
    });
    plugin.addReleaseHook(ignored -> successes.incrementAndGet());
    expectStopFailure("hook-failure");
    assertNull(plugin.getPluginContext());
    assertEquals(1, successes.get());
    assertEquals(PluginState.STARTED, pluginManager.startPlugin("hook-failure"));
    assertEquals(2, attempts.get());
    assertEquals(1, successes.get());
    assertTrue(plugin.getReleaseHooks().isEmpty());
  }

  @Test
  public void appendedReleaseHookIsRetainedAndCleanedBeforeRestart() throws Exception {
    pluginManager.addResolvedPlugin("appended-hook", LifecyclePlugin.class);
    assertEquals(PluginState.STARTED, pluginManager.startPlugin("appended-hook"));
    Pf4bootPlugin plugin = (Pf4bootPlugin) pluginManager.getPlugin("appended-hook").getPlugin();
    AtomicInteger parentCalls = new AtomicInteger(), childCalls = new AtomicInteger();
    java.net.ServerSocket socket = new java.net.ServerSocket(0);
    plugin.addReleaseHook(current -> {
      parentCalls.incrementAndGet();
      current.addReleaseHook(ignored -> {
        try { socket.close(); childCalls.incrementAndGet(); }
        catch (IOException e) { throw new IllegalStateException(e); }
      });
    });
    try {
      expectStopFailure("appended-hook");
      assertNull(plugin.getPluginContext()); assertFalse(socket.isClosed());
      assertEquals(1, plugin.getReleaseHooks().size());
      assertEquals(PluginState.STARTED, pluginManager.startPlugin("appended-hook"));
      assertTrue(socket.isClosed()); assertTrue(plugin.getReleaseHooks().isEmpty());
      assertEquals(1, parentCalls.get()); assertEquals(1, childCalls.get());
    } finally { socket.close(); }
  }

  @Test
  public void selfAppendingReleaseHookFailsBoundedlyUntilCorrected() {
    pluginManager.addResolvedPlugin("self-hook", LifecyclePlugin.class); pluginManager.startPlugin("self-hook");
    Pf4bootPlugin plugin = (Pf4bootPlugin) pluginManager.getPlugin("self-hook").getPlugin();
    AtomicInteger calls = new AtomicInteger();
    java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<Pf4bootPlugin>> hook = new java.util.concurrent.atomic.AtomicReference<>();
    hook.set(current -> { calls.incrementAndGet(); current.addReleaseHook(hook.get()); });
    plugin.addReleaseHook(hook.get());
    try {
      expectStopFailure("self-hook");
      assertEquals(1, calls.get()); assertEquals(1, plugin.getReleaseHooks().size());
      assertEquals(PluginState.FAILED, pluginManager.startPlugin("self-hook"));
      assertEquals(2, calls.get()); assertNull(plugin.getPluginContext());
    } finally { plugin.getReleaseHooks().clear(); }
    assertEquals(PluginState.STARTED, pluginManager.startPlugin("self-hook"));
  }

  @Test
  public void blockedTaskPreventsAnyDependentFromStoppingUntilDrained() throws Exception {
    BlockingTask.entered = new CountDownLatch(1);
    BlockingTask.release = new CountDownLatch(1);
    pluginManager.testProperties.setPluginDrainTimeoutMillis(30);
    pluginManager.addResolvedPlugin("blocked", BlockingPlugin.class);
    pluginManager.addResolvedPlugin("consumer", LifecyclePlugin.class, "blocked");
    pluginManager.startPlugin("consumer");
    assertTrue(BlockingTask.entered.await(2, TimeUnit.SECONDS));
    try {
      expectStopFailure("blocked");
      assertEquals(PluginState.STARTED, pluginManager.getPlugin("blocked").getPluginState());
      assertEquals(PluginState.STARTED, pluginManager.getPlugin("consumer").getPluginState());
      assertTrue(((Pf4bootPlugin) pluginManager.getPlugin("blocked").getPlugin()).getPluginContext().isActive());
    } finally {
      BlockingTask.release.countDown();
    }
    pluginManager.testProperties.setPluginDrainTimeoutMillis(2000);
    assertEquals(PluginState.STOPPED, pluginManager.stopPlugin("blocked"));
    assertEquals(PluginState.FAILED, pluginManager.getPlugin("consumer").getPluginState());
  }

  private void expectStopFailure(String pluginId) {
    try {
      pluginManager.stopPlugin(pluginId);
      throw new AssertionError("stop should fail");
    } catch (org.pf4j.PluginRuntimeException expected) {
      assertTrue(expected.getMessage() != null);
    }
  }

  @Test
  public void unloadKeepsBlockedPluginAndLoaderUntilDrainSucceeds() throws Exception {
    BlockingTask.entered = new CountDownLatch(1);
    BlockingTask.release = new CountDownLatch(1);
    pluginManager.testProperties.setPluginDrainTimeoutMillis(30);
    pluginManager.addResolvedPlugin("blocked-unload", BlockingPlugin.class);
    pluginManager.startPlugin("blocked-unload");
    PluginWrapper wrapper = pluginManager.getPlugin("blocked-unload");
    TestPluginClassLoader loader = (TestPluginClassLoader) wrapper.getPluginClassLoader();
    try {
      assertTrue(BlockingTask.entered.await(2, TimeUnit.SECONDS));
      expectUnloadFailure("blocked-unload");
      assertTrue(wrapper == pluginManager.getPlugin("blocked-unload"));
      assertEquals(PluginState.STARTED, wrapper.getPluginState());
      assertTrue(((Pf4bootPlugin) wrapper.getPlugin()).getPluginContext().isActive());
      assertFalse(loader.isClosed());
    } finally { BlockingTask.release.countDown(); }
    pluginManager.testProperties.setPluginDrainTimeoutMillis(2000);
    assertTrue(pluginManager.unloadPlugin("blocked-unload"));
    assertNull(pluginManager.getPlugin("blocked-unload"));
    assertTrue(loader.isClosed());
  }

  @Test
  public void unloadRetainsFailedCleanupForRetry() {
    pluginManager.addResolvedPlugin("failed-unload", StopFailurePlugin.class);
    pluginManager.startPlugin("failed-unload");
    PluginWrapper wrapper = pluginManager.getPlugin("failed-unload");
    StopFailurePlugin plugin = (StopFailurePlugin) wrapper.getPlugin();
    TestPluginClassLoader loader = (TestPluginClassLoader) wrapper.getPluginClassLoader();
    plugin.fail = true;
    expectUnloadFailure("failed-unload");
    assertTrue(wrapper == pluginManager.getPlugin("failed-unload"));
    assertEquals(PluginState.FAILED, wrapper.getPluginState());
    assertFalse(loader.isClosed());
    plugin.fail = false;
    assertTrue(pluginManager.unloadPlugin("failed-unload"));
    assertTrue(loader.isClosed());
  }

  @Test
  public void dependentCleanupFailurePreventsProviderUnload() {
    pluginManager.addResolvedPlugin("provider", LifecyclePlugin.class);
    pluginManager.addResolvedPlugin("dependent", StopFailurePlugin.class, "provider");
    pluginManager.startPlugin("dependent");
    StopFailurePlugin dependent = (StopFailurePlugin) pluginManager.getPlugin("dependent").getPlugin();
    dependent.fail = true;
    expectUnloadFailure("provider");
    assertEquals(PluginState.STARTED, pluginManager.getPlugin("provider").getPluginState());
    assertEquals(PluginState.FAILED, pluginManager.getPlugin("dependent").getPluginState());
    assertFalse(((TestPluginClassLoader) pluginManager.getPlugin("provider").getPluginClassLoader()).isClosed());
    dependent.fail = false;
    assertTrue(pluginManager.unloadPlugin("provider"));
    assertNull(pluginManager.getPlugin("dependent"));
  }

  private void expectUnloadFailure(String id) {
    try {
      pluginManager.unloadPlugin(id);
      throw new AssertionError("unload should preserve a plugin whose stop failed");
    } catch (org.pf4j.PluginRuntimeException expected) {
      assertFalse(expected.getMessage().isEmpty());
    }
  }

  @Test
  public void repeatedStartFailuresDoNotGrowGlobalRegistry() {
    int contexts = ApplicationContextProvider.getRegisteredContextCount();
    pluginManager.addResolvedPlugin("failing", FailingPlugin.class);
    for (int i = 0; i < 20; i++) {
      assertEquals(PluginState.FAILED, pluginManager.startPlugin("failing"));
      assertEquals(contexts, ApplicationContextProvider.getRegisteredContextCount());
      assertNull(((Pf4bootPlugin) pluginManager.getPlugin("failing").getPlugin()).getPluginContext());
    }
  }

  @Test
  public void preStopRestartCannotConsumeActiveCleanupOrRetainClosedContext() {
    int contexts = ApplicationContextProvider.getRegisteredContextCount();
    Resource.closed.set(0);
    pluginManager.addResolvedPlugin("cleanup-reentry", ResourcePlugin.class);
    assertEquals(PluginState.STARTED, pluginManager.startPlugin("cleanup-reentry"));
    ClassLoader loader = pluginManager.getPlugin("cleanup-reentry").getPluginClassLoader();
    AtomicInteger rejected = new AtomicInteger();
    org.springframework.context.ApplicationListener<net.xdob.pf4boot.spring.boot.PreStopPluginEvent> listener = event -> {
      if (event.getPlugin().getPluginId().equals("cleanup-reentry")) {
        try { pluginManager.restartPlugin("cleanup-reentry"); throw new AssertionError("active cleanup restart succeeded"); }
        catch (org.pf4j.PluginRuntimeException expected) { rejected.incrementAndGet(); }
      }
    };
    applicationContext.addApplicationListener(listener);
    try {
      assertEquals(PluginState.STOPPED, pluginManager.stopPlugin("cleanup-reentry"));
      assertTrue(rejected.get() > 0);
      assertNull(((Pf4bootPlugin) pluginManager.getPlugin("cleanup-reentry").getPlugin()).getPluginContext());
      assertFalse(ApplicationContextProvider.containsApplicationContext(loader));
      assertEquals(contexts, ApplicationContextProvider.getRegisteredContextCount());
      assertEquals(1, Resource.closed.get());
    } finally {
      applicationContext.getBean("applicationEventMulticaster", org.springframework.context.event.ApplicationEventMulticaster.class).removeApplicationListener(listener);
    }
    assertEquals(PluginState.STARTED, pluginManager.startPlugin("cleanup-reentry"));
  }

  @Test
  public void failedStartCleanupReentryIsReportedAndRecoversAfterCallbackRemoval() {
    pluginManager.close();
    java.util.concurrent.atomic.AtomicBoolean failStart = new java.util.concurrent.atomic.AtomicBoolean(true);
    pluginManager = new TestPluginManager(applicationContext, pluginsRoot, new Pf4bootProperties(), new Pf4bootPluginSupport() {
      public void startedPlugin(Pf4bootPlugin plugin) {
        if (failStart.get()) { throw new IllegalStateException("start failure before cleanup"); }
      }
    });
    int contexts = ApplicationContextProvider.getRegisteredContextCount();
    Resource.closed.set(0);
    pluginManager.addResolvedPlugin("failed-cleanup-reentry", ResourcePlugin.class);
    org.springframework.context.ApplicationListener<net.xdob.pf4boot.spring.boot.PreStopPluginEvent> listener = event -> {
      if (event.getPlugin().getPluginId().equals("failed-cleanup-reentry")) { pluginManager.stopPlugin("failed-cleanup-reentry"); }
    };
    applicationContext.addApplicationListener(listener);
    try {
      assertEquals(PluginState.FAILED, pluginManager.startPlugin("failed-cleanup-reentry"));
      assertNull(((Pf4bootPlugin) pluginManager.getPlugin("failed-cleanup-reentry").getPlugin()).getPluginContext());
      assertEquals(contexts, ApplicationContextProvider.getRegisteredContextCount());
      assertEquals(1, Resource.closed.get());
    } finally {
      applicationContext.getBean("applicationEventMulticaster", org.springframework.context.event.ApplicationEventMulticaster.class).removeApplicationListener(listener);
    }
    failStart.set(false);
    assertEquals(PluginState.STARTED, pluginManager.startPlugin("failed-cleanup-reentry"));
    assertEquals(1, Resource.closed.get());
  }

  @Test
  public void initializationFailureClearsPropertiesBeforeContextExists() {
    pluginManager.addResolvedPlugin("init-failure", InitFailurePlugin.class);
    assertEquals(PluginState.FAILED, pluginManager.startPlugin("init-failure"));
    assertNull(System.getProperty("pf4boot.test.init-failure"));
    assertNull(((Pf4bootPlugin) pluginManager.getPlugin("init-failure").getPlugin()).getPluginContext());
  }

  @Test
  public void failedStartKeepsContextUntilRunningWorkCanDrain() throws Exception {
    pluginManager.close();
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginDrainTimeoutMillis(30);
    BlockingTask.entered = new CountDownLatch(1);
    BlockingTask.release = new CountDownLatch(1);
    AtomicInteger starts = new AtomicInteger();
    pluginManager = new TestPluginManager(applicationContext, pluginsRoot, properties, new Pf4bootPluginSupport() {
      public void startedPlugin(Pf4bootPlugin plugin) {
        try {
          assertTrue(BlockingTask.entered.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        if (starts.getAndIncrement() == 0) { throw new IllegalStateException("after scheduling"); }
      }
    });
    pluginManager.addResolvedPlugin("blocked-start", BlockingPlugin.class);
    try {
      assertEquals(PluginState.FAILED, pluginManager.startPlugin("blocked-start"));
      Pf4bootPlugin plugin = (Pf4bootPlugin) pluginManager.getPlugin("blocked-start").getPlugin();
      assertTrue(plugin.getPluginContext().isActive());
    } finally {
      BlockingTask.release.countDown();
    }
    properties.setPluginDrainTimeoutMillis(2000);
    assertEquals(PluginState.STARTED, pluginManager.startPlugin("blocked-start"));
    assertEquals(PluginState.STOPPED, pluginManager.stopPlugin("blocked-start"));
  }

  @Test
  public void preStopEventFailureDoesNotSkipCleanup() {
    pluginManager.addResolvedPlugin("event-failure", LifecyclePlugin.class);
    pluginManager.startPlugin("event-failure");
    AtomicInteger attempts = new AtomicInteger();
    org.springframework.context.ApplicationListener<org.springframework.context.ApplicationEvent> listener = event -> {
      if (event instanceof net.xdob.pf4boot.spring.boot.PreStopPluginEvent
          && attempts.incrementAndGet() == 1) {
        throw new IllegalStateException("event failure");
      }
    };
    applicationContext.addApplicationListener(listener);
    int contexts = ApplicationContextProvider.getRegisteredContextCount();
    try {
      expectStopFailure("event-failure");
      assertEquals(contexts - 1, ApplicationContextProvider.getRegisteredContextCount());
      assertNull(((Pf4bootPlugin) pluginManager.getPlugin("event-failure").getPlugin()).getPluginContext());
      assertEquals(PluginState.STARTED, pluginManager.startPlugin("event-failure"));
    } finally {
      applicationContext.getBean("applicationEventMulticaster",
          org.springframework.context.event.ApplicationEventMulticaster.class).removeApplicationListener(listener);
    }
  }

  @Test
  public void reloadStopsUnloadsLoadsAndStartsPluginAgain() {
    pluginManager.addResolvedPlugin("reloadable", LifecyclePlugin.class);

    pluginManager.startPlugin("reloadable");
    TestPluginClassLoader oldClassLoader =
        (TestPluginClassLoader) pluginManager.getPlugin("reloadable").getPluginClassLoader();
    PluginState state = pluginManager.reloadPlugin("reloadable");

    assertEquals(PluginState.STARTED, state);
    assertTrue(oldClassLoader.isClosed());
    assertEquals(2, LifecyclePlugin.events.stream()
        .filter(event -> event.equals("reloadable:start"))
        .count());
    assertTrue(LifecyclePlugin.events.contains("reloadable:stop"));
  }

  @Test
  public void concurrentStartsForSamePluginRunStartOnce() throws Exception {
    pluginManager.addResolvedPlugin("slow", SlowStartPlugin.class);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<PluginState> first = executor.submit(() -> pluginManager.startPlugin("slow"));
      Future<PluginState> second = executor.submit(() -> pluginManager.startPlugin("slow"));

      assertEquals(PluginState.STARTED, first.get());
      assertEquals(PluginState.STARTED, second.get());
    } finally {
      executor.shutdownNow();
    }

    assertEquals(1, SlowStartPlugin.events.stream()
        .filter(event -> event.equals("slow:start"))
        .count());
  }

  @Test
  public void loadPluginVerifiesPackageBeforeCreatingClassLoader() throws Exception {
    Path pluginPath = pluginsRoot.resolve("verified");
    Files.createDirectories(pluginPath);
    List<String> events = new ArrayList<>();
    TestPluginManager manager = new TestPluginManager(applicationContext, pluginsRoot,
        (path, descriptor) -> {
          events.add("verify:" + descriptor.getPluginId());
          return PluginPackageVerificationResult.fail("blocked");
        });

    try {
      manager.loadPluginFromPath(pluginPath);
    } catch (RuntimeException expected) {
      events.add("blocked");
    } finally {
      manager.close();
    }

    assertEquals("verify:verified", events.get(0));
    assertTrue(events.contains("blocked"));
  }

  @Test
  public void upgradeRestoresRollbackPluginWhenNewPackageFails() throws Exception {
    pluginManager.addResolvedPlugin("upgradable", LifecyclePlugin.class);
    pluginManager.startPlugin("upgradable");
    Path brokenPath = pluginsRoot.resolve("broken-upgrade");
    Path rollbackPath = pluginsRoot.resolve("rollback-upgradable");
    Files.createDirectories(brokenPath);
    Files.createDirectories(rollbackPath);

    try {
      pluginManager.upgradePlugin("upgradable", brokenPath, rollbackPath);
    } catch (RuntimeException expected) {
    }

    assertEquals(PluginState.STARTED, pluginManager.getPlugin("upgradable").getPluginState());
    assertTrue(LifecyclePlugin.events.contains("upgradable:stop"));
    assertEquals(2, LifecyclePlugin.events.stream()
        .filter(event -> event.equals("upgradable:start"))
        .count());
  }

  @Test
  public void enforceCompatibilityBlocksUnsupportedSystemVersion() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginCompatibilityVerificationMode(PluginPackageVerificationMode.ENFORCE);
    TestPluginManager manager = new TestPluginManager(applicationContext, pluginsRoot, properties);
    manager.setSystemVersion("1.0.0");
    Path pluginPath = pluginsRoot.resolve("requires-newer");
    Files.createDirectories(pluginPath);

    try {
      manager.loadPluginFromPath(pluginPath);
    } catch (RuntimeException expected) {
      return;
    } finally {
      manager.close();
    }

    throw new AssertionError("unsupported plugin should be blocked before class loader creation");
  }

  private static class TestPluginManager extends Pf4bootPluginManagerImpl {
    private final Pf4bootProperties testProperties;

    TestPluginManager(AnnotationConfigApplicationContext applicationContext, Path pluginsRoot) {
      this(applicationContext, pluginsRoot, new Pf4bootProperties());
    }

    TestPluginManager(AnnotationConfigApplicationContext applicationContext, Path pluginsRoot,
        Pf4bootProperties properties) {
      this(applicationContext, pluginsRoot, properties, new Pf4bootPluginSupport() { });
    }

    TestPluginManager(AnnotationConfigApplicationContext applicationContext, Path pluginsRoot,
        Pf4bootProperties properties, Pf4bootPluginSupport support) {
      super(applicationContext, properties, support, new DefaultShareBeanMgr(new DefaultAutoExportMgr()), pluginsRoot);
      testProperties = properties;
    }

    TestPluginManager(AnnotationConfigApplicationContext applicationContext, Path pluginsRoot,
        PluginPackageVerifier pluginPackageVerifier) {
      super(applicationContext, new Pf4bootProperties(), new Pf4bootPluginSupport() {
      }, new DefaultShareBeanMgr(new DefaultAutoExportMgr()),
          Collections.singletonList(pluginPackageVerifier), pluginsRoot);
      testProperties = null;
    }

    void addResolvedPlugin(String pluginId, Class<? extends Pf4bootPlugin> pluginClass, String... dependencies) {
      Pf4bootPluginWrapper wrapper = createWrapper(pluginId, pluginClass, dependencies);
      try {
        Files.createDirectories(wrapper.getPluginPath());
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      plugins.put(pluginId, wrapper);
      pluginClassLoaders.put(pluginId, wrapper.getPluginClassLoader());
      resolvedPlugins.add(wrapper);
      rebuildDependencyResolver();
    }

    void rebuildDependencyResolver() {
      dependencyResolver = new DependencyResolver(getVersionManager());
      dependencyResolver.resolve(plugins.values().stream()
          .map(PluginWrapper::getDescriptor)
          .collect(Collectors.toList()));
    }

    @Override
    protected PluginWrapper loadPluginFromPath(Path pluginPath) {
      String pluginId = pluginPath.getFileName().toString();
      if ("reloadable".equals(pluginId)) {
        Pf4bootPluginWrapper wrapper = createWrapper(pluginId, LifecyclePlugin.class);
        plugins.put(pluginId, wrapper);
        pluginClassLoaders.put(pluginId, wrapper.getPluginClassLoader());
        resolvedPlugins.add(wrapper);
        rebuildDependencyResolver();
        return wrapper;
      }
      if ("broken-upgrade".equals(pluginId)) {
        throw new IllegalStateException("broken upgrade");
      }
      if ("rollback-upgradable".equals(pluginId)) {
        Pf4bootPluginWrapper wrapper = createWrapper("upgradable", LifecyclePlugin.class);
        plugins.put("upgradable", wrapper);
        pluginClassLoaders.put("upgradable", wrapper.getPluginClassLoader());
        resolvedPlugins.add(wrapper);
        rebuildDependencyResolver();
        return wrapper;
      }
      return super.loadPluginFromPath(pluginPath);
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
      return new PluginDescriptorFinder() {
        @Override
        public boolean isApplicable(Path pluginPath) {
          return true;
        }

        @Override
        public PluginDescriptor find(Path pluginPath) {
          String requires = "requires-newer".equals(pluginPath.getFileName().toString()) ? ">=2.0.0" : "";
          return new DefaultPluginDescriptor(pluginPath.getFileName().toString(),
              pluginPath.getFileName().toString(), LifecyclePlugin.class.getName(),
              "1.0.0", requires, "test", "Apache-2.0");
        }
      };
    }

    private Pf4bootPluginWrapper createWrapper(
        String pluginId,
        Class<? extends Pf4bootPlugin> pluginClass,
        String... dependencies) {
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
          pluginId, pluginId, pluginClass.getName(), "1.0.0", "", "test", "Apache-2.0");
      for (String dependency : dependencies) {
        descriptor.addDependency(new PluginDependency(dependency));
      }
      ClassLoader pluginClassLoader = new TestPluginClassLoader(pluginClass.getClassLoader());
      Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(
          this, descriptor, getPluginsRoot().resolve(pluginId), pluginClassLoader);
      wrapper.setPluginFactory(getPluginFactory());
      wrapper.setPluginState(PluginState.RESOLVED);
      return wrapper;
    }
  }

  private static class TestPluginClassLoader extends ClassLoader implements Closeable {
    private boolean closed;

    TestPluginClassLoader(ClassLoader parent) {
      super(parent);
    }

    @Override
    public void close() throws IOException {
      closed = true;
    }

    boolean isClosed() {
      return closed;
    }
  }

  @PluginStarter(LifecycleConfig.class)
  public static class LifecyclePlugin extends Pf4bootPlugin {
    static final List<String> events = new ArrayList<>();

    public LifecyclePlugin(PluginWrapper wrapper) {
      super(wrapper);
    }

    @Override
    public void initiate() {
      events.add(getPluginId() + ":initiate");
    }

    @Override
    public void start() {
      events.add(getPluginId() + ":start");
    }

    @Override
    public void stop() {
      events.add(getPluginId() + ":stop");
    }

    @Override
    public void closed() {
      events.add(getPluginId() + ":closed");
    }
  }

  @PluginStarter(LifecycleConfig.class)
  public static class FailingPlugin extends Pf4bootPlugin {
    static final List<String> events = new ArrayList<>();

    public FailingPlugin(PluginWrapper wrapper) {
      super(wrapper);
    }

    @Override
    public void start() {
      events.add(getPluginId() + ":start");
      throw new IllegalStateException("boom");
    }
  }

  @PluginStarter(ResourceConfig.class)
  public static class ResourcePlugin extends LifecyclePlugin {
    public ResourcePlugin(PluginWrapper wrapper) { super(wrapper); }
  }

  public static class ResourceConfig {
    @Bean(destroyMethod = "close")
    public Resource resource() { return new Resource(); }
  }

  public static class Resource implements AutoCloseable {
    static final AtomicInteger closed = new AtomicInteger();
    public void close() { closed.incrementAndGet(); }
  }

  @PluginStarter(LifecycleConfig.class)
  public static class StopFailurePlugin extends LifecyclePlugin {
    boolean fail;
    public StopFailurePlugin(PluginWrapper wrapper) { super(wrapper); }
    public void stop() {
      if (fail) { throw new AssertionError("stop failure"); }
      super.stop();
    }
  }

  @PluginStarter(LifecycleConfig.class)
  public static class InitFailurePlugin extends LifecyclePlugin {
    public InitFailurePlugin(PluginWrapper wrapper) { super(wrapper); }
    public void initiate() {
      setProperty("pf4boot.test.init-failure", "temporary");
      throw new IllegalStateException("init failure");
    }
  }

  @PluginStarter(BlockingTask.class)
  public static class BlockingPlugin extends LifecyclePlugin {
    public BlockingPlugin(PluginWrapper wrapper) { super(wrapper); }
  }

  @Component
  public static class BlockingTask {
    static CountDownLatch entered;
    static CountDownLatch release;
    @Scheduled(fixedDelay = 1000000)
    public void run() throws InterruptedException {
      entered.countDown();
      release.await();
    }
  }

  @PluginStarter(LifecycleConfig.class)
  public static class SlowStartPlugin extends Pf4bootPlugin {
    static final List<String> events = new ArrayList<>();

    public SlowStartPlugin(PluginWrapper wrapper) {
      super(wrapper);
    }

    @Override
    public void start() {
      events.add(getPluginId() + ":start");
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public static class LifecycleConfig {
  }

  @PluginStarter(LifecycleConfig.class)
  public static class BeforeRefreshPlugin extends Pf4bootPlugin implements org.springframework.beans.factory.DisposableBean {
    boolean failBeforeRefresh = true, failDestroy;
    java.net.ServerSocket socket;
    int port, destroys, opens, markerDestroyed;
    public BeforeRefreshPlugin(PluginWrapper wrapper) { super(wrapper); }
    @Override
    protected org.springframework.context.ConfigurableApplicationContext createPluginContext(
        org.springframework.context.ConfigurableApplicationContext parent) {
      org.springframework.context.ConfigurableApplicationContext context = super.createPluginContext(parent);
      org.springframework.beans.factory.support.DefaultListableBeanFactory factory =
          (org.springframework.beans.factory.support.DefaultListableBeanFactory) context.getBeanFactory();
      factory.registerSingleton("earlyMarker", new Object());
      factory.registerDisposableBean("earlyMarker", () -> markerDestroyed++);
      try { socket = new java.net.ServerSocket(port); port = socket.getLocalPort(); opens++; }
      catch (IOException e) { throw new IllegalStateException(e); }
      if (failBeforeRefresh) { throw new IllegalStateException("failure before refresh"); }
      return context;
    }
    @Override
    public void destroy() throws Exception {
      destroys++;
      if (failDestroy) { throw new IOException("destruction blocked"); }
      closeSocket();
    }
    void closeSocket() throws IOException { if (socket != null) { socket.close(); } }
  }
}
