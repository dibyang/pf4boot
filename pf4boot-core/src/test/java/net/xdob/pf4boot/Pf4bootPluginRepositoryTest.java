package net.xdob.pf4boot;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;

public class Pf4bootPluginRepositoryTest {

  @Test
  public void deletePluginPathRemovesZipArtifact() throws Exception {
    Path pluginsRoot = Files.createTempDirectory("pf4boot-repo-root");
    Path pluginPath = pluginsRoot.resolve("sample-plugin");
    Files.createDirectories(pluginPath);
    Path zipPath = pluginPath.resolve("sample-plugin.zip");
    Files.write(zipPath, "dummy".getBytes(StandardCharsets.UTF_8));

    Pf4bootPluginRepository repository = new Pf4bootPluginRepository(pluginsRoot);
    repository.deletePluginPath(pluginPath);

    assertFalse(Files.exists(zipPath));
  }
}
