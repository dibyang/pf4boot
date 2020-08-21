package com.ls.pf4boot;

import org.pf4j.BasePluginRepository;
import org.pf4j.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * LinksPluginRepository
 *
 * @author yangzj
 * @version 1.0
 */
public class LinkPluginRepository extends BasePluginRepository {
  static final Logger log = LoggerFactory.getLogger(Pf4bootPluginManager.class);

  public LinkPluginRepository(Path pluginsRoot) {
    super(pluginsRoot);

    AndFileFilter pluginsFilter = new AndFileFilter(new NameFileFilter("plugins.link"));
    pluginsFilter.addFileFilter(new NotFileFilter(new DirectoryFileFilter()));
    setFilter(pluginsFilter);
  }


  @Override
  public List<Path> getPluginPaths() {
    File[] files = pluginsRoot.toFile().listFiles(filter);

    if ((files == null) || files.length == 0) {
      return Collections.emptyList();
    }

    List<File> pFiles = new ArrayList<>();
    List<File> links = readLinks(files[0]);
    pFiles.addAll(links);
    if (comparator != null) {
      Collections.sort(pFiles, comparator);
    }
    List<Path> paths = new ArrayList<>(pFiles.size());
    for (File file : pFiles) {
      paths.add(file.toPath());
    }
    return paths;
  }

  private List<File> readLinks(File linksFile) {
    List<File> links = new ArrayList<>();
    try {
      List<String> lines = Files.readAllLines(linksFile.toPath());
      if (lines != null) {
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
      }
    } catch (IOException e) {
      log.warn(null, e);
    }
    return links;
  }

  @Override
  public boolean deletePluginPath(Path pluginPath) {
    File[] files = pluginsRoot.toFile().listFiles(filter);
    if ((files == null) || files.length == 0) {
      return false;
    }
    removeFromLinks(files[0],pluginPath);
    return true;
  }

  private void removeFromLinks(File linksFile, Path pluginPath) {
    try {
      List<String> lines = Files.readAllLines(linksFile.toPath());
      if (lines != null) {
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
      }
    } catch (IOException e) {
      log.warn(null, e);
    }
  }
}
