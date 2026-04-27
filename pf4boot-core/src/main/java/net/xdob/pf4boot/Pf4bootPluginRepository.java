package net.xdob.pf4boot;

import org.pf4j.BasePluginRepository;
import org.pf4j.DefaultPluginRepository;
import org.pf4j.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Pf4bootPluginRepository
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginRepository extends BasePluginRepository {

  private static final Logger log = LoggerFactory.getLogger(Pf4bootPluginRepository.class);

  public Pf4bootPluginRepository(Path... pluginsRoots) {
    this(Arrays.asList(pluginsRoots));
  }

  public Pf4bootPluginRepository(List<Path> pluginsRoots) {
    super(pluginsRoots);

    AndFileFilter pluginsFilter = new AndFileFilter(new DirectoryFileFilter());
    pluginsFilter.addFileFilter(new NotFileFilter(createHiddenPluginFilter()));
    setFilter(pluginsFilter);
  }


  @Override
  public boolean deletePluginPath(Path pluginPath) {
    Path zip = FileUtils.findWithEnding(pluginPath, ".zip", ".ZIP", ".Zip");
    if (zip != null && zip.toFile().exists()) {
      FileUtils.optimisticDelete(zip);
    }
    return super.deletePluginPath(pluginPath);
  }

  protected FileFilter createHiddenPluginFilter() {
    return new OrFileFilter(new HiddenFilter(),new NameFileFilter("cache"));
  }
}
