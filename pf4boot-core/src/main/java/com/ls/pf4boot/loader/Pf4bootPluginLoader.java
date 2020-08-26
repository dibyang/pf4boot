package com.ls.pf4boot.loader;

import com.ls.pf4boot.Pf4bootPluginManager;
import com.ls.pf4boot.internal.Pf4bootPluginClassLoader;
import com.ls.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.DefaultPluginLoader;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;

import java.nio.file.Path;

/**
 * LinkPluginLoader
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginLoader extends DefaultPluginLoader {
  protected Pf4bootProperties properties;

  public Pf4bootPluginLoader(Pf4bootPluginManager pluginManager, Pf4bootProperties properties) {
    super(pluginManager);
    this.properties = properties;
  }

  @Override
  protected PluginClassLoader createPluginClassLoader(Path pluginPath,
                                                      PluginDescriptor pluginDescriptor) {
    if (properties.getClassesDirectories() != null && properties.getClassesDirectories().size() > 0) {
      for (String classesDirectory : properties.getClassesDirectories()) {
        pluginClasspath.addClassesDirectories(classesDirectory);
      }
    }
    if (properties.getLibDirectories() != null && properties.getLibDirectories().size() > 0) {
      for (String libDirectory : properties.getLibDirectories()) {
        pluginClasspath.addJarsDirectories(libDirectory);
      }
    }
    return new Pf4bootPluginClassLoader(pluginManager, pluginDescriptor);
  }

}
