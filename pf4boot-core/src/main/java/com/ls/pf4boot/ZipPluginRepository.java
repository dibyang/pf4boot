package com.ls.pf4boot;

import org.pf4j.BasePluginRepository;
import org.pf4j.util.ZipFileFilter;

import java.nio.file.Path;

/**
 * ZipPluginRepository
 *
 * @author yangzj
 * @version 1.0
 */
public class ZipPluginRepository extends BasePluginRepository {

  public ZipPluginRepository(Path pluginsRoot) {
    super(pluginsRoot, new ZipFileFilter());
  }

}
