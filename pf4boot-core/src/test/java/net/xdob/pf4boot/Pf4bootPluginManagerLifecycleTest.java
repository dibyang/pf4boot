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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    pluginManager.addResolvedPlugin("failing", FailingPlugin.class);

    assertEquals(PluginState.FAILED, pluginManager.startPlugin("failing"));

    FailingPlugin plugin = (FailingPlugin) pluginManager.getPlugin("failing").getPlugin();
    assertNull(plugin.getPluginContext());
    assertTrue(FailingPlugin.events.contains("failing:start"));
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

  private static class TestPluginManager extends Pf4bootPluginManagerImpl {

    TestPluginManager(AnnotationConfigApplicationContext applicationContext, Path pluginsRoot) {
      super(applicationContext, new Pf4bootProperties(), new Pf4bootPluginSupport() {
      }, new DefaultShareBeanMgr(new DefaultAutoExportMgr()), pluginsRoot);
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
      return super.loadPluginFromPath(pluginPath);
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
}
