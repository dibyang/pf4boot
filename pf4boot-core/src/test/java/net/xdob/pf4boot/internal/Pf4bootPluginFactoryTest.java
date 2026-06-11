package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Pf4bootPluginFactoryTest {

  @Test
  public void returnsOriginalPf4bootPluginIfTypeMatches() {
    Pf4bootAwarePlugin.startCalled = false;

    Plugin plugin = new Pf4bootPluginFactory().create(createWrapper(Pf4bootAwarePlugin.class));

    assertTrue(plugin instanceof Pf4bootAwarePlugin);
    assertFalse(plugin instanceof Pf4bootPluginProxy);
    plugin.start();
    assertTrue(Pf4bootAwarePlugin.startCalled);
  }

  @Test
  public void wrapsLegacyPluginWhenNotPf4bootType() {
    LegacyPlugin.startCalled = false;

    Plugin plugin = new Pf4bootPluginFactory().create(createWrapper(LegacyPlugin.class));

    assertTrue(plugin instanceof Pf4bootPluginProxy);
    plugin.start();
    assertTrue(LegacyPlugin.startCalled);
  }

  private PluginWrapper createWrapper(Class<? extends Plugin> pluginClass) {
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        pluginClass.getSimpleName(),
        pluginClass.getSimpleName(),
        pluginClass.getName(),
        "1.0.0",
        "",
        "test",
        "Apache-2.0");
    return new net.xdob.pf4boot.Pf4bootPluginWrapper(
        new DefaultPluginManager(),
        descriptor,
        Paths.get(pluginClass.getSimpleName()),
        pluginClass.getClassLoader());
  }

  public static class Pf4bootAwarePlugin extends Pf4bootPlugin {
    static boolean startCalled;

    public Pf4bootAwarePlugin(PluginWrapper wrapper) {
      super(wrapper);
      startCalled = false;
    }

    @Override
    public void start() {
      startCalled = true;
    }
  }

  public static class LegacyPlugin extends Plugin {
    static boolean startCalled;

    public LegacyPlugin(PluginWrapper wrapper) {
      super(wrapper);
    }

    @Override
    public void start() {
      startCalled = true;
    }
  }
}
