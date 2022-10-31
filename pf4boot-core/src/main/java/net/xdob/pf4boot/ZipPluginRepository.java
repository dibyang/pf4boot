package net.xdob.pf4boot;

import org.pf4j.BasePluginRepository;
import org.pf4j.util.ZipFileFilter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * ZipPluginRepository
 *
 * @author yangzj
 * @version 1.0
 */
public class ZipPluginRepository extends BasePluginRepository {

  public ZipPluginRepository(Path... pluginsRoots) {
    this(Arrays.asList(pluginsRoots));
  }

  public ZipPluginRepository(List<Path> pluginsRoots) {
    super(pluginsRoots, new ZipFileFilter());
  }

}
