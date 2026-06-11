package net.xdob.pf4boot;

import org.junit.Test;
import org.pf4j.PluginDescriptor;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class ManifestPluginDescriptorFinder2Test {

  @Test
  public void finderReturnsDescriptorOnlyWhenPluginIdExists() throws Exception {
    Path pluginJar = writeManifestJar("plugin-id-present", "sample-plugin");

    PluginDescriptor descriptor = new ManifestPluginDescriptorFinder2().find(pluginJar);

    assertNotNull(descriptor);
    assertEquals("sample-plugin", descriptor.getPluginId());
  }

  @Test
  public void finderReturnsNullWhenPluginIdMissing() throws Exception {
    Path pluginJar = writeManifestJar("plugin-id-missing", null);

    assertNull(new ManifestPluginDescriptorFinder2().find(pluginJar));
  }

  private Path writeManifestJar(String filePrefix, String pluginId) throws Exception {
    Path pluginPath = Files.createTempFile(filePrefix, ".jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Plugin-Class", "net.xdob.pf4boot.FakePlugin");
    if (pluginId != null) {
      manifest.getMainAttributes().putValue("Plugin-Id", pluginId);
      manifest.getMainAttributes().putValue("Plugin-Version", "1.0.0");
    }
    try (JarOutputStream stream = new JarOutputStream(new FileOutputStream(pluginPath.toFile()), manifest)) {
      // no entries, only manifest
    }
    return pluginPath;
  }
}
