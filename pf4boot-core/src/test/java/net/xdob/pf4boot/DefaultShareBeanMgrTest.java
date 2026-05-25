package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.Export;
import net.xdob.pf4boot.annotation.ExportBeans;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.modal.SharingScope;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DependencyResolver;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DefaultShareBeanMgrTest {

  private AnnotationConfigApplicationContext applicationContext;
  private TestPluginManager pluginManager;

  @Before
  public void setUp() throws Exception {
    applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.registerBean(
        "metadataReaderFactory",
        MetadataReaderFactory.class,
        () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader()));
    applicationContext.refresh();

    Path pluginsRoot = Files.createTempDirectory("pf4boot-sharing-test");
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
  public void exportsBeansToAllScopesAndCleansThemOnStop() {
    pluginManager.addResolvedPlugin("sharing", SharingPlugin.class);

    pluginManager.startPlugin("sharing");

    ConfigurableApplicationContext groupedPlatform = pluginManager.getPlatformContext("g1");
    assertTrue(pluginManager.getRootContext().containsBean("rootService"));
    assertTrue(applicationContext.containsBean("appService"));
    assertTrue(pluginManager.getPlatformContext(PluginStarter.DEFAULT).containsBean("platformService"));
    assertTrue(groupedPlatform.containsBean("groupService"));
    assertTrue(pluginManager.shareBeanMgr.getRegisteredSharingBeanCount("sharing") >= 4);
    assertEquals(1, pluginManager.shareBeanMgr.getScheduledTaskCount("sharing"));

    pluginManager.stopPlugin("sharing");

    assertFalse(pluginManager.getRootContext().containsBean("rootService"));
    assertFalse(applicationContext.containsBean("appService"));
    assertFalse(pluginManager.getPlatformContext(PluginStarter.DEFAULT).containsBean("platformService"));
    assertFalse(groupedPlatform.containsBean("groupService"));
    assertEquals(0, pluginManager.shareBeanMgr.getRegisteredSharingBeanCount("sharing"));
    assertEquals(0, pluginManager.shareBeanMgr.getScheduledTaskCount("sharing"));
  }

  @Test
  public void duplicateExportRegistrationIsRemovedOnceOnStop() {
    pluginManager.addResolvedPlugin("duplicate", DuplicateExportPlugin.class);

    pluginManager.startPlugin("duplicate");

    Object bean = pluginManager.getRootContext().getBean("duplicateRootService");
    assertSame(bean, pluginManager.getRootContext().getBean("duplicateRootService"));

    pluginManager.stopPlugin("duplicate");

    assertFalse(pluginManager.getRootContext().containsBean("duplicateRootService"));
  }

  @Test
  public void dynamicBeanNameConflictIsRejectedByDefault() {
    pluginManager.registerBeanToRootContext("rootService", new Object());
    pluginManager.addResolvedPlugin("conflict", SharingPlugin.class);

    assertEquals(PluginState.FAILED, pluginManager.startPlugin("conflict"));
    assertTrue(pluginManager.getPlugin("conflict").getFailedException()
        instanceof IllegalStateException);
  }

  private static class TestPluginManager extends Pf4bootPluginManagerImpl {
    private final DefaultShareBeanMgr shareBeanMgr;

    TestPluginManager(AnnotationConfigApplicationContext applicationContext, Path pluginsRoot) {
      this(applicationContext, pluginsRoot, new DefaultShareBeanMgr(new DefaultAutoExportMgr()));
    }

    private TestPluginManager(
        AnnotationConfigApplicationContext applicationContext,
        Path pluginsRoot,
        DefaultShareBeanMgr shareBeanMgr) {
      super(applicationContext, new Pf4bootProperties(), new Pf4bootPluginSupport() {
      }, shareBeanMgr, pluginsRoot);
      this.shareBeanMgr = shareBeanMgr;
    }

    void addResolvedPlugin(String pluginId, Class<? extends Pf4bootPlugin> pluginClass) {
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
          pluginId, pluginId, pluginClass.getName(), "1.0.0", "", "test", "Apache-2.0");
      ClassLoader pluginClassLoader = new TestPluginClassLoader(pluginClass.getClassLoader());
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
    }
  }

  private static class TestPluginClassLoader extends ClassLoader {
    TestPluginClassLoader(ClassLoader parent) {
      super(parent);
    }
  }

  @PluginStarter(SharingConfig.class)
  public static class SharingPlugin extends Pf4bootPlugin {
    public SharingPlugin(PluginWrapper wrapper) {
      super(wrapper);
    }
  }

  @PluginStarter(
      value = DuplicateExportConfig.class,
      name4Beans = @ExportBeans.Name4Bean(
          names = "duplicateRootService",
          scope = SharingScope.ROOT))
  public static class DuplicateExportPlugin extends Pf4bootPlugin {
    public DuplicateExportPlugin(PluginWrapper wrapper) {
      super(wrapper);
    }
  }

  @Configuration
  public static class SharingConfig {
    @Bean
    @Export(scope = SharingScope.ROOT)
    public Object rootService() {
      return new Object();
    }

    @Bean
    @Export(scope = SharingScope.APPLICATION)
    public Object appService() {
      return new Object();
    }

    @Bean
    @Export(scope = SharingScope.PLATFORM)
    public Object platformService() {
      return new Object();
    }

    @Bean
    @Export(scope = SharingScope.PLATFORM, group = "g1")
    public Object groupService() {
      return new Object();
    }

    @Bean
    public ScheduledService scheduledService() {
      return new ScheduledService();
    }
  }

  @Component
  public static class ScheduledService {
    @Scheduled(fixedDelay = 1000)
    public void tick() {
    }
  }

  @Configuration
  public static class DuplicateExportConfig {
    @Bean
    @Export(scope = SharingScope.ROOT)
    public Object duplicateRootService() {
      return new Object();
    }
  }
}
