package com.ls.pf4boot.loader;

import com.ls.pf4boot.internal.Pf4bootPluginClassLoader;
import com.ls.pf4boot.loader.archive.Archive;
import com.ls.pf4boot.loader.archive.JarFileArchive;
import com.ls.pf4boot.loader.jar.JarFile;
import org.pf4j.*;
import org.pf4j.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * JarPf4bootPluginLoader
 *
 * @author yangzj
 * @version 1.0
 */
public class JarPf4bootPluginLoader implements PluginLoader {
  static final Logger log = LoggerFactory.getLogger(JarPf4bootPluginLoader.class);
  protected PluginManager pluginManager;

  public JarPf4bootPluginLoader(PluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @Override
  public boolean isApplicable(Path pluginPath) {
    return Files.exists(pluginPath) && FileUtils.isJarFile(pluginPath);
  }

  @Override
  public ClassLoader loadPlugin(Path pluginPath, PluginDescriptor pluginDescriptor) {
    PluginClassLoader pluginClassLoader = new Pf4bootPluginClassLoader(pluginManager, pluginDescriptor);
    pluginClassLoader.addFile(pluginPath.toFile());
    JarFile.registerUrlProtocolHandler();
    try {
      JarFileArchive archive = new JarFileArchive(pluginPath.toFile());
      Iterator<Archive> archives = archive.getNestedArchives(s -> s.getName().startsWith("lib/"), n -> n.getName().endsWith(".jar"));
      while(archives.hasNext()){
        pluginClassLoader.addURL(archives.next().getUrl());
      }
    } catch (IOException e) {
      log.warn(null,e);
    }
    return pluginClassLoader;
  }


}
