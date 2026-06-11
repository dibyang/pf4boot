package net.xdob.pf4boot;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class LinkPluginRepositoryTest {

  @Test
  public void resolvesAbsoluteAndRelativeLinks() throws Exception {
    Path pluginsRoot = Files.createTempDirectory("pf4boot-link-repo");
    Path pluginA = pluginsRoot.resolve("plugin-a");
    Path pluginB = pluginsRoot.resolve("plugin-b");
    Files.createDirectories(pluginA);
    Files.createDirectories(pluginB);

    Path linkFile = pluginsRoot.resolve(LinkPluginRepository.LINK_FILE_NAME);
    Files.write(
        linkFile,
        Arrays.asList(
            "# managed plugin links",
            "plugin-a",
            pluginB.toAbsolutePath().toString(),
            "",
            "# end"),
        StandardCharsets.UTF_8);

    LinkPluginRepository repo = new LinkPluginRepository(pluginsRoot);
    List<Path> pluginPaths = repo.getPluginPaths();

    assertEquals(2, pluginPaths.size());
    assertTrue(pluginPaths.contains(pluginA.toRealPath()));
    assertTrue(pluginPaths.contains(pluginB.toRealPath()));
  }

  @Test
  public void deletePluginPathOnlyRemovesMatchedLines() throws Exception {
    Path pluginsRoot = Files.createTempDirectory("pf4boot-link-delete");
    Path keep = pluginsRoot.resolve("keep-plugin");
    Path remove = pluginsRoot.resolve("remove-plugin");
    Files.createDirectories(keep);
    Files.createDirectories(remove);

    Path linkFile = pluginsRoot.resolve(LinkPluginRepository.LINK_FILE_NAME);
    Files.write(
        linkFile,
        Arrays.asList(
            "# keep",
            keep.toAbsolutePath().toString(),
            remove.toAbsolutePath().toString(),
            "  ",
            "# tail"),
        StandardCharsets.UTF_8,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.CREATE);

    LinkPluginRepository repo = new LinkPluginRepository(pluginsRoot);
    boolean removed = repo.deletePluginPath(remove);

    List<String> lines = Files.readAllLines(linkFile, StandardCharsets.UTF_8);
    assertTrue(removed);
    assertTrue(lines.contains("# keep"));
    assertTrue(lines.contains("# tail"));
    assertFalse(lines.contains(remove.toAbsolutePath().toString()));
    assertTrue(lines.contains(keep.toAbsolutePath().toString()));
  }

  @Test
  public void deletePluginPathReturnsFalseWhenNoLinkFileExists() throws Exception {
    Path pluginsRoot = Files.createTempDirectory("pf4boot-link-miss");
    boolean removed = new LinkPluginRepository(pluginsRoot).deletePluginPath(pluginsRoot);

    assertFalse(removed);
  }
}
