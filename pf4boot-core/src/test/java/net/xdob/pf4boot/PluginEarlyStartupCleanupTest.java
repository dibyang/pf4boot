package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import net.xdob.pf4boot.deployment.PluginWorkTracker;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static org.junit.Assert.*;

/** 创建上下文前失败且无法排空时，欠清理记录不能丢失。 */
public class PluginEarlyStartupCleanupTest {
  private static final String ID = "early-startup";
  private AnnotationConfigApplicationContext host;
  private TestManager manager;
  private EarlyPlugin plugin;
  private final Drainer drainer = new Drainer();
  private final ClosingLoader loader = new ClosingLoader();

  @Before public void setUp() throws Exception {
    host = new AnnotationConfigApplicationContext();
    host.registerBean("metadataReaderFactory", MetadataReaderFactory.class,
        () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader()));
    host.getBeanFactory().registerSingleton("startupDrainer", drainer); host.refresh();
    Pf4bootProperties properties = new Pf4bootProperties(); properties.setPluginDrainTimeoutMillis(10);
    manager = new TestManager(host, properties, Files.createTempDirectory("pf4boot-early-startup"));
    plugin = manager.addPlugin(loader);
    assertTrue(drainer.work.tryEnter(ID));
  }

  @After public void tearDown() throws Exception {
    releaseWork();
    try { if (manager != null) { manager.close(); } }
    finally {
      if (plugin != null) { plugin.closeSocket(); }
      host.close(); loader.close();
    }
  }

  @Test public void failedInitiateDrainTimeoutIsCleanedBeforeRestart() throws Exception {
    failBeforeContext();
    assertEquals(PluginState.FAILED, manager.startPlugin(ID));
    assertEquals("activation bypassed pending cleanup", 1, plugin.attempts);
    releaseWork(); plugin.failInitiate = false;
    assertEquals(PluginState.STARTED, manager.startPlugin(ID));
    assertEquals(2, plugin.attempts); assertEquals(1, plugin.stops);
    assertEquals(PluginState.STOPPED, manager.stopPlugin(ID));
    assertEquals(2, plugin.stops); assertTrue(plugin.socket.isClosed()); assertRebind();
  }

  @Test public void unloadRetainsResourcesUntilDrainAndCleanupSucceed() throws Exception {
    failBeforeContext();
    expectFailure(() -> manager.unloadPlugin(ID));
    assertNotNull(manager.getPlugin(ID)); assertFalse(loader.closed); assertFalse(plugin.socket.isClosed());
    releaseWork();
    assertTrue(manager.unloadPlugin(ID));
    assertTrue("unloaded a live early resource", plugin.socket.isClosed());
    assertEquals(1, plugin.stops); assertTrue(loader.closed); assertRebind();
  }

  @Test public void managerCloseRetainsParentsUntilEarlyResourcesAreCleaned() throws Exception {
    failBeforeContext();
    expectFailure(manager::close);
    assertTrue(manager.getRootContext().isActive()); assertFalse(plugin.socket.isClosed());
    releaseWork(); manager.close(); manager.close();
    assertTrue(plugin.socket.isClosed()); assertEquals(1, plugin.stops); assertRebind();
  }

  private void failBeforeContext() {
    assertEquals(PluginState.FAILED, manager.startPlugin(ID));
    assertNull(plugin.getPluginContext()); assertFalse(plugin.socket.isClosed()); assertEquals(1, plugin.attempts);
  }
  private void releaseWork() { if (drainer.work.getRunningCount(ID) != 0) { drainer.work.exit(ID); } }
  private void assertRebind() throws IOException { try (ServerSocket rebound = new ServerSocket(plugin.port)) { assertTrue(rebound.isBound()); } }
  private void expectFailure(Runnable action) {
    try { action.run(); fail("operation should reject un-drained resources"); }
    catch (PluginRuntimeException expected) { assertNotNull(expected.getMessage()); }
  }

  private static class Drainer implements PluginTrafficDrainer {
    final PluginWorkTracker work = new PluginWorkTracker();
    public void beginDrain(Collection<String> ids) { work.beginDrain(ids); }
    public boolean awaitDrain(Collection<String> ids, long timeout) throws InterruptedException { return work.awaitDrain(ids, timeout); }
    public void endDrain(Collection<String> ids) { work.endDrain(ids); }
  }
  private static class ClosingLoader extends URLClassLoader {
    boolean closed;
    ClosingLoader() { super(new URL[0], PluginEarlyStartupCleanupTest.class.getClassLoader()); }
    public void close() throws IOException { super.close(); closed = true; }
  }
  private static class TestManager extends Pf4bootPluginManagerImpl {
    TestManager(AnnotationConfigApplicationContext host, Pf4bootProperties properties, Path root) {
      super(host, properties, new Pf4bootPluginSupport() { }, new DefaultShareBeanMgr(new DefaultAutoExportMgr()), root);
    }
    EarlyPlugin addPlugin(ClassLoader loader) {
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(ID, ID, EarlyPlugin.class.getName(), "1.0.0", "", "test", "Apache-2.0");
      Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(this, descriptor, getPluginsRoot().resolve(ID), loader);
      wrapper.setPluginFactory(getPluginFactory()); wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put(ID, wrapper); resolvedPlugins.add(wrapper); pluginClassLoaders.put(ID, loader);
      dependencyResolver = new DependencyResolver(getVersionManager()); dependencyResolver.resolve(java.util.Collections.singletonList(descriptor));
      return (EarlyPlugin) wrapper.getPlugin();
    }
  }
  public static class Config { }
  @PluginStarter(Config.class)
  public static class EarlyPlugin extends Pf4bootPlugin {
    boolean failInitiate = true;
    ServerSocket socket; int port, attempts, stops;
    public EarlyPlugin(PluginWrapper wrapper) { super(wrapper); }
    public void initiate() {
      attempts++;
      try { socket = new ServerSocket(port); port = socket.getLocalPort(); }
      catch (IOException e) { throw new IllegalStateException(e); }
      if (failInitiate) { throw new IllegalStateException("failure before context creation"); }
    }
    public void stop() { stops++; try { closeSocket(); } catch (IOException e) { throw new IllegalStateException(e); } }
    void closeSocket() throws IOException { if (socket != null) { socket.close(); } }
  }
}
