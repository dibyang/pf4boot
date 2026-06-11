package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.Pf4bootPluginWrapper;
import net.xdob.pf4boot.modal.PluginRuntimeSnapshot;
import net.xdob.pf4boot.modal.PluginError;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginDependency;
import org.pf4j.PluginState;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class DefaultPluginRuntimeInspectorTest {

  @Test
  public void snapshotOfMapsPluginMetadataAndError() throws Exception {
    Path root = Files.createTempDirectory("pf4boot-inspector");
    Path pluginPath = root.resolve("sample-plugin");
    Files.createDirectories(pluginPath);
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        "sample", "sample", "java.lang.String", "1.2.3", "", "test", "Apache-2.0");
    descriptor.addDependency(new PluginDependency("dep-a"));
    descriptor.addDependency(new PluginDependency("dep-b"));

    Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(
        new DefaultPluginManager(),
        descriptor,
        pluginPath,
        getClass().getClassLoader());
    wrapper.setPluginState(PluginState.STARTED);
    wrapper.getLastStartDurationMillis().set(128);

    PluginError pluginError = PluginError.of("sample", "boom", "detail");
    DefaultPluginRuntimeInspector inspector = new DefaultPluginRuntimeInspector(null);

    PluginRuntimeSnapshot snapshot = inspector.snapshotOf(wrapper, pluginError);

    assertEquals("sample", snapshot.getPluginId());
    assertEquals("1.2.3", snapshot.getVersion());
    assertEquals(PluginState.STARTED, snapshot.getState());
    assertEquals(pluginPath.toString(), snapshot.getPluginPath());
    assertEquals(128, snapshot.getLastStartDurationMillis());
    assertEquals(Arrays.asList("dep-a", "dep-b"), snapshot.getDependencies());
    assertEquals("boom", snapshot.getErrorMessage());
    assertEquals("detail", snapshot.getErrorDetail());
  }
}
