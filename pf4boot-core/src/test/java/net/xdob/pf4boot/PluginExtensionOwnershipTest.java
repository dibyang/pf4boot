package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.modal.DynamicBeanConflictPolicy;
import net.xdob.pf4boot.spring.boot.*;
import org.junit.Test;
import org.pf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.*;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** 扩展注销只影响本插件仍拥有的注册，原实例的资源仍由其上下文销毁。 */
public class PluginExtensionOwnershipTest {
  public static class Config { }
  @PluginStarter(Config.class)
  public static class Owner extends Pf4bootPlugin { public Owner(PluginWrapper wrapper) { super(wrapper); } }
  public static class Extension implements DisposableBean {
    final ServerSocket socket;
    int destroys;
    public Extension() throws Exception { socket = new ServerSocket(0); }
    public void destroy() throws Exception { destroys++; socket.close(); }
  }
  private static class Manager extends Pf4bootPluginManagerImpl {
    final DefaultShareBeanMgr sharing;
    Manager(AnnotationConfigApplicationContext host, Pf4bootProperties props, Path root, DefaultShareBeanMgr sharing) {
      super(host, props, new Pf4bootPluginSupport(){}, sharing, root); this.sharing = sharing;
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor("owner", "owner", Owner.class.getName(), "1", "", "test", "Apache-2.0");
      PluginWrapper wrapper = new Pf4bootPluginWrapper(this, descriptor, root.resolve("owner"), new ClassLoader(getClass().getClassLoader()){});
      wrapper.setPluginFactory(getPluginFactory()); wrapper.setPluginState(PluginState.RESOLVED);
      plugins.put("owner", wrapper); resolvedPlugins.add(wrapper); pluginClassLoaders.put("owner", wrapper.getPluginClassLoader());
      dependencyResolver = new DependencyResolver(getVersionManager()); dependencyResolver.resolve(Collections.singletonList(descriptor));
    }
    public Set<String> getExtensionClassNames(String id) { return Collections.singleton(Extension.class.getName()); }
    public PluginWrapper whichPlugin(Class<?> type) { return type == Extension.class ? getPlugin("owner") : super.whichPlugin(type); }
  }
  private static class Fixture implements AutoCloseable {
    final AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
    final Manager manager;
    Fixture() throws Exception {
      host.registerBean("metadataReaderFactory", MetadataReaderFactory.class, () -> new SimpleMetadataReaderFactory(new DefaultResourceLoader()));
      host.refresh(); Pf4bootProperties props = new Pf4bootProperties(); props.setDynamicBeanConflictPolicy(DynamicBeanConflictPolicy.REPLACE);
      manager = new Manager(host, props, Files.createTempDirectory("pf4boot-extension-owner"), new DefaultShareBeanMgr(new DefaultAutoExportMgr()));
    }
    public void close() { try { manager.close(); } finally { host.close(); } }
  }
  @Test public void replacingAnExtensionSurvivesStoppingItsPreviousOwner() throws Exception {
    try (Fixture f = new Fixture()) {
      assertEquals(PluginState.STARTED, f.manager.startPlugin("owner"));
      Extension original = ((Pf4bootPlugin) f.manager.getPlugin("owner").getPlugin()).getPluginContext().getBean(Extension.class);
      String name = Extension.class.getName(); Object replacement = new Object();
      f.manager.registerBeanToPlatformContext(PluginStarter.DEFAULT, name, replacement);
      assertEquals(PluginState.STOPPED, f.manager.stopPlugin("owner"));
      assertSame(replacement, f.manager.getPlatformContext(PluginStarter.DEFAULT).getBean(name));
      assertEquals(0, f.manager.sharing.getRegisteredExtensionBeanCount("owner"));
      assertEquals(1, original.destroys); assertTrue(original.socket.isClosed());
      try (ServerSocket rebound = new ServerSocket(original.socket.getLocalPort())) { assertTrue(rebound.isBound()); }
      f.manager.unregisterBeanFromPlatformContext(PluginStarter.DEFAULT, name);
      assertEquals(PluginState.STARTED, f.manager.startPlugin("owner"));
      assertEquals(1, f.manager.sharing.getRegisteredExtensionBeanCount("owner"));
    }
  }
  @Test public void registrationFailureStillRollsBackTheOwnedExtension() throws Exception {
    try (Fixture f = new Fixture()) {
      AtomicReference<Extension> created = new AtomicReference<>();
      ApplicationListener<BeanRegisterEvent> listener = event -> {
        if (event.getBean() instanceof Extension) { created.set((Extension) event.getBean()); throw new IllegalStateException("registration event failure"); }
      };
      f.host.addApplicationListener(listener);
      try { assertEquals(PluginState.FAILED, f.manager.startPlugin("owner")); }
      finally { f.host.getBean("applicationEventMulticaster", org.springframework.context.event.ApplicationEventMulticaster.class).removeApplicationListener(listener); }
      assertNotNull(created.get()); assertEquals(1, created.get().destroys); assertTrue(created.get().socket.isClosed());
      assertFalse(f.manager.getPlatformContext(PluginStarter.DEFAULT).containsLocalBean(Extension.class.getName()));
      assertEquals(0, f.manager.sharing.getRegisteredExtensionBeanCount("owner"));
      assertEquals(PluginState.STARTED, f.manager.startPlugin("owner"));
      assertEquals(1, f.manager.sharing.getRegisteredExtensionBeanCount("owner"));
    }
  }
}
