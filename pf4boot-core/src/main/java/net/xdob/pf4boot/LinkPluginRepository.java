package net.xdob.pf4boot;

import org.pf4j.BasePluginRepository;
import org.pf4j.util.AndFileFilter;
import org.pf4j.util.DirectoryFileFilter;
import org.pf4j.util.NameFileFilter;
import org.pf4j.util.NotFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LinkPluginRepository
 *
 * 通过 plugins.link 文件加载插件路径。
 *
 * plugins.link 示例：
 *
 * # 绝对路径
 * D:/plugins/demo-plugin
 *
 * # 相对路径，基于 plugins.link 所在目录解析
 * ./dev/demo-plugin
 *
 * 注意：
 * - 空行会被忽略
 * - # 开头的行会被忽略
 * - 删除插件路径时会保留原文件里的注释和空行
 *
 * @author yangzj
 * @version 1.0
 */
public class LinkPluginRepository extends BasePluginRepository {
  static final Logger log = LoggerFactory.getLogger(LinkPluginRepository.class);

  public static final String LINK_FILE_NAME = "plugins.link";

  public LinkPluginRepository(Path... pluginsRoots) {
    this(Arrays.asList(pluginsRoots));
  }

  public LinkPluginRepository(List<Path> pluginsRoots) {
    super(pluginsRoots);

    AndFileFilter pluginsFilter = new AndFileFilter(new NameFileFilter(LINK_FILE_NAME));
    pluginsFilter.addFileFilter(new NotFileFilter(new DirectoryFileFilter()));
    setFilter(pluginsFilter);
  }

  @Override
  public List<Path> getPluginPaths() {
    List<Path> linkFiles = super.getPluginPaths();
    Set<Path> pluginPaths = new LinkedHashSet<>();

    if (linkFiles == null || linkFiles.isEmpty()) {
      return new ArrayList<>();
    }

    for (Path linkFile : linkFiles) {
      List<Path> links = readLinks(linkFile);
      if (comparator != null) {
        links.sort((p1, p2) -> comparator.compare(p1.toFile(), p2.toFile()));
      }
      pluginPaths.addAll(links);
    }

    return new ArrayList<>(pluginPaths);
  }

  private List<Path> readLinks(Path linkFile) {
    List<Path> links = new ArrayList<>();

    if (linkFile == null || !Files.isRegularFile(linkFile)) {
      return links;
    }

    Path baseDir = linkFile.toAbsolutePath().normalize().getParent();

    try {
      List<String> lines = Files.readAllLines(linkFile, StandardCharsets.UTF_8);

      for (String rawLine : lines) {
        String line = normalizeLine(rawLine);

        if (isIgnoredLine(line)) {
          continue;
        }

        Path pluginPath = resolveLinkPath(baseDir, line);
        if (pluginPath == null) {
          continue;
        }

        if (Files.exists(pluginPath)) {
          links.add(pluginPath);
        } else {
          log.warn("Plugin link path does not exist, linkFile={}, path={}", linkFile, pluginPath);
        }
      }
    } catch (IOException e) {
      log.warn("Read plugin links failed, linkFile={}", linkFile, e);
    }

    return links;
  }

  @Override
  public boolean deletePluginPath(Path pluginPath) {
    if (pluginPath == null) {
      return false;
    }

    List<Path> linkFiles = super.getPluginPaths();
    if (linkFiles == null || linkFiles.isEmpty()) {
      return false;
    }

    boolean removed = false;
    Path normalizedPluginPath = normalizePath(pluginPath);

    for (Path linkFile : linkFiles) {
      if (removeFromLinks(linkFile, normalizedPluginPath)) {
        removed = true;
      }
    }

    return removed;
  }

  private boolean removeFromLinks(Path linkFile, Path pluginPath) {
    if (linkFile == null || !Files.isRegularFile(linkFile)) {
      return false;
    }

    Path baseDir = linkFile.toAbsolutePath().normalize().getParent();

    try {
      List<String> lines = Files.readAllLines(linkFile, StandardCharsets.UTF_8);
      List<String> newLines = new ArrayList<>(lines.size());

      boolean removed = false;

      for (String rawLine : lines) {
        String line = normalizeLine(rawLine);

        if (isIgnoredLine(line)) {
          newLines.add(rawLine);
          continue;
        }

        Path linkedPath = resolveLinkPath(baseDir, line);
        if (linkedPath != null && samePath(linkedPath, pluginPath)) {
          removed = true;
          continue;
        }

        newLines.add(rawLine);
      }

      if (removed) {
        Files.write(linkFile, newLines, StandardCharsets.UTF_8);
        log.info("Remove plugin link path from {}, path={}", linkFile, pluginPath);
      }

      return removed;
    } catch (IOException e) {
      log.warn("Remove plugin link failed, linkFile={}, pluginPath={}", linkFile, pluginPath, e);
      return false;
    }
  }

  private static String normalizeLine(String rawLine) {
    return rawLine == null ? "" : rawLine.trim();
  }

  private static boolean isIgnoredLine(String line) {
    return line == null || line.isEmpty() || line.startsWith("#");
  }

  private static Path resolveLinkPath(Path baseDir, String line) {
    try {
      Path path = Paths.get(line);

      if (!path.isAbsolute()) {
        if (baseDir == null) {
          path = path.toAbsolutePath();
        } else {
          path = baseDir.resolve(path);
        }
      }

      return normalizePath(path);
    } catch (InvalidPathException e) {
      log.warn("Invalid plugin link path: {}", line, e);
      return null;
    }
  }

  private static Path normalizePath(Path path) {
    if (path == null) {
      return null;
    }

    Path normalized = path.toAbsolutePath().normalize();

    /*
     * toRealPath 可以消除符号链接和大小写差异，
     * 但路径不存在时会抛 IOException。
     * 这里失败时退回 absolute normalize，避免删除不存在路径时报错。
     */
    try {
      return normalized.toRealPath();
    } catch (IOException ignore) {
      return normalized;
    }
  }

  private static boolean samePath(Path p1, Path p2) {
    if (p1 == null || p2 == null) {
      return false;
    }

    Path n1 = normalizePath(p1);
    Path n2 = normalizePath(p2);

    if (n1.equals(n2)) {
      return true;
    }

    /*
     * Windows 下 Path.equals 对大小写有时不符合用户预期，
     * 这里额外做一次字符串忽略大小写比较。
     */
    return n1.toString().equalsIgnoreCase(n2.toString());
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " " + pluginsRoots.stream()
        .map(Path::toString)
        .collect(Collectors.joining(", "));
  }
}
