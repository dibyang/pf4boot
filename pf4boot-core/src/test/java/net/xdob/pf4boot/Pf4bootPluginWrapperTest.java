package net.xdob.pf4boot;

import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DependencyResolver;
import org.pf4j.PluginDependency;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Pf4bootPluginWrapperTest {

  @Test
  public void stoppedPluginRequiresManualIntervention() throws Exception {
    TestPluginManager manager = new TestPluginManager();
    Pf4bootPluginWrapper wrapper = manager.createResolved("stopped");
    wrapper.getStopped().set(true);

    assertTrue(wrapper.isManualInterventionRequired());
  }

  @Test
  public void failedPluginWithManualInterventionRequiredIsDetected() throws Exception {
    TestPluginManager manager = new TestPluginManager();
    Pf4bootPluginWrapper wrapper = manager.createResolved("failed");
    wrapper.setFailedException(new ManualInterventionRequiredException());

    assertTrue(wrapper.isManualInterventionRequired());
  }

  @Test
  public void requiredManualPluginsWalksDependencyChain() throws Exception {
    TestPluginManager manager = new TestPluginManager();
    Pf4bootPluginWrapper leaf = manager.createResolved("leaf");
    leaf.setFailedException(new ManualInterventionRequiredException());

    Pf4bootPluginWrapper root = manager.createResolved("root", "leaf");

    Set<Pf4bootPluginWrapper> required = root.findRequiredManualPlugins();
    assertEquals(1, required.size());
    assertTrue(required.contains(leaf));
  }

  private static class TestPluginManager extends Pf4bootPluginManagerImpl {
    private TestPluginManager() throws Exception {
      super(createApplicationContext(), new Pf4bootProperties(),
          new Pf4bootPluginSupport() {
          }, new DefaultShareBeanMgr(new DefaultAutoExportMgr()), Files.createTempDirectory("pf4boot-wrapper-test"));
    }

    private static AnnotationConfigApplicationContext createApplicationContext() {
      AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
      applicationContext.refresh();
      return applicationContext;
    }

    Pf4bootPluginWrapper createResolved(String pluginId, String... dependencies) {
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
          pluginId, pluginId, TestPlugin.class.getName(), "1.0.0", "", "test", "Apache-2.0");
      for (String dependency : dependencies) {
        descriptor.addDependency(new PluginDependency(dependency));
      }
      ClassLoader pluginClassLoader = TestPlugin.class.getClassLoader();
      Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(
          this, descriptor, getPluginsRoot().resolve(pluginId), pluginClassLoader);
      wrapper.setPluginFactory(getPluginFactory());
      wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put(pluginId, wrapper);
      pluginClassLoaders.put(pluginId, pluginClassLoader);
      resolvedPlugins.add(wrapper);
      dependencyResolver = new DependencyResolver(getVersionManager());
      dependencyResolver.resolve(plugins.values().stream()
          .map(PluginWrapper::getDescriptor)
          .collect(Collectors.toList()));
      return wrapper;
    }
  }

  private static class TestPlugin extends Pf4bootPlugin {
    private TestPlugin(PluginWrapper wrapper) {
      super(wrapper);
    }
  }

  private static class ManualInterventionRequiredException extends Exception implements ManualInterventionRequired {
    ManualInterventionRequiredException() {
      super("manual intervention required");
    }
  }
}
