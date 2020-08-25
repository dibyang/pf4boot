package com.ls.pf4boot.internal;

import org.pf4j.PropertiesPluginDescriptorFinder;
import org.pf4j.util.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PropertiesPluginDescriptorFinder2
 *
 * @author yangzj
 * @version 1.0
 */
public class PropertiesPluginDescriptorFinder2 extends PropertiesPluginDescriptorFinder {
  @Override
  public boolean isApplicable(Path pluginPath) {
    return Files.exists(pluginPath) && Files.isDirectory(pluginPath);
  }
}
