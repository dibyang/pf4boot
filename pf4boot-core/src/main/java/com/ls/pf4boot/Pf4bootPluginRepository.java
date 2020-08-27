package com.ls.pf4boot;

import org.pf4j.BasePluginRepository;
import org.pf4j.DefaultPluginRepository;
import org.pf4j.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Pf4bootPluginRepository
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginRepository extends BasePluginRepository {

  private static final Logger log = LoggerFactory.getLogger(DefaultPluginRepository.class);

  public Pf4bootPluginRepository(Path pluginsRoot) {
    super(pluginsRoot);

    AndFileFilter pluginsFilter = new AndFileFilter(new DirectoryFileFilter());
    pluginsFilter.addFileFilter(new NotFileFilter(createHiddenPluginFilter()));
    setFilter(pluginsFilter);
  }

  @Override
  public List<Path> getPluginPaths() {
    return super.getPluginPaths();
  }

  @Override
  public boolean deletePluginPath(Path pluginPath) {
    FileUtils.optimisticDelete(FileUtils.findWithEnding(pluginPath, ".zip", ".ZIP", ".Zip"));
    return super.deletePluginPath(pluginPath);
  }

  protected FileFilter createHiddenPluginFilter() {
    return new OrFileFilter(new HiddenFilter(),new NameFileFilter("cache"));
  }
}
