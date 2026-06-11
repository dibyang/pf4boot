package net.xdob.pf4boot;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class ZipPluginRepositoryTest {

  @Test
  public void resolvesZipAndJarPluginPathsOnly() throws Exception {
    Path pluginsRoot = Files.createTempDirectory("pf4boot-zip-repo");
    Path zipPlugin = pluginsRoot.resolve("plugin.zip");
    Path jarPlugin = pluginsRoot.resolve("plugin.jar");
    Files.write(zipPlugin, "zip".getBytes(StandardCharsets.UTF_8));
    Files.write(jarPlugin, "jar".getBytes(StandardCharsets.UTF_8));
    Files.write(pluginsRoot.resolve("ignore.txt"), "text".getBytes(StandardCharsets.UTF_8));

    List<Path> pluginPaths = new ZipPluginRepository(pluginsRoot).getPluginPaths();
    assertEquals(1, pluginPaths.size());
    assertTrue(pluginPaths.contains(zipPlugin.toRealPath()));
    assertFalse(pluginPaths.contains(pluginsRoot.resolve("ignore.txt").toRealPath()));
  }

  @Test
  public void returnsEmptyForNoPluginArtifacts() throws Exception {
    Path pluginsRoot = Files.createTempDirectory("pf4boot-zip-repo-empty");
    Files.write(pluginsRoot.resolve("ignore.txt"), "text".getBytes(StandardCharsets.UTF_8));

    List<Path> pluginPaths = new ZipPluginRepository(pluginsRoot).getPluginPaths();
    assertTrue(pluginPaths.isEmpty());
  }
}
