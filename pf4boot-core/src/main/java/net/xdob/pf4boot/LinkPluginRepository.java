package net.xdob.pf4boot;

import org.pf4j.BasePluginRepository;
import org.pf4j.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * LinksPluginRepository
 *
 * @author yangzj
 * @version 1.0
 */
public class LinkPluginRepository extends BasePluginRepository {
  static final Logger log = LoggerFactory.getLogger(LinkPluginRepository.class);

  public LinkPluginRepository(Path... pluginsRoots) {
    this(Arrays.asList(pluginsRoots));
  }

  public LinkPluginRepository(List<Path> pluginsRoots) {
    super(pluginsRoots);
    AndFileFilter pluginsFilter = new AndFileFilter(new NameFileFilter("plugins.link"));
    pluginsFilter.addFileFilter(new NotFileFilter(new DirectoryFileFilter()));
    setFilter(pluginsFilter);
  }


  @Override
  public List<Path> getPluginPaths() {
    List<Path> list = super.getPluginPaths();

    if ((list == null) || list.size() != 1) {
      return Collections.emptyList();
    }

    List<File> links = readLinks(list.get(0).toFile());
    if (comparator != null) {
      links.sort(comparator);
    }
    List<Path> paths = new ArrayList<>(links.size());
    for (File file : links) {
      Path pluginPath = file.toPath();
      paths.add(pluginPath);
    }
    return paths;
  }

  private List<File> readLinks(File linksFile) {
    List<File> links = new ArrayList<>();
    try {
      List<String> lines = Files.readAllLines(linksFile.toPath());
      for (String line : lines) {
        if (line != null) {
          if (!line.startsWith("#")) {
            File file = new File(line);
            if (file.exists()) {
              links.add(file);
            }
          }
        }
      }
    } catch (IOException e) {
      log.warn(null, e);
    }
    return links;
  }

  @Override
  public boolean deletePluginPath(Path pluginPath) {
    List<Path> list = super.getPluginPaths();
    if ((list == null) || list.size() != 1) {
      return false;
    }
    removeFromLinks(list.get(0).toFile(),pluginPath);
    return true;
  }

  private void removeFromLinks(File linksFile, Path pluginPath) {
    try {
      List<String> lines = Files.readAllLines(linksFile.toPath());
      Iterator<String> iterator = lines.iterator();
      while (iterator.hasNext()) {
        String line = iterator.next();
        if (line != null) {
          if (!line.startsWith("#")) {
            File file = new File(line);
            if(file.toPath().equals(pluginPath)){
              iterator.remove();
            }
          }
        }
      }
    } catch (IOException e) {
      log.warn(null, e);
    }
  }
}
