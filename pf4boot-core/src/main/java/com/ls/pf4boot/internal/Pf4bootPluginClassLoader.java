package com.ls.pf4boot.internal;

import com.ls.pf4boot.Pf4bootPluginManager;
import com.ls.pf4boot.PluginClassLoader4boot;
import org.pf4j.ClassLoadingStrategy;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pf4bootPluginClassLoader
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginClassLoader extends PluginClassLoader implements PluginClassLoader4boot {

  private static final Logger log = LoggerFactory.getLogger(Pf4bootPluginClassLoader.class);

  private List<String> pluginFirstClasses;

  private List<String> pluginOnlyResources;

  public Pf4bootPluginClassLoader(PluginManager pluginManager, PluginDescriptor pluginDescriptor) {
    // load class from parent first to avoid same class loaded by different classLoader,
    // so Spring could autowired bean by type correctly.
    super(pluginManager, pluginDescriptor, ((Pf4bootPluginManager)pluginManager).getMainApplicationContext().getClassLoader(), ClassLoadingStrategy.APD);
  }

  @Override
  public void setPluginFirstClasses(List<String> pluginFirstClasses) {
    this.pluginFirstClasses = pluginFirstClasses.stream()
        .map(pluginFirstClass -> pluginFirstClass
            .replaceAll(".", "[$0]")
            .replace("[*]", ".*?")
            .replace("[?]", ".?"))
        .collect(Collectors.toList());
  }

  @Override
  public void setPluginOnlyResources(List<String> pluginOnlyResources) {
    this.pluginOnlyResources = pluginOnlyResources.stream()
        .map(pluginFirstClass -> pluginFirstClass
            .replaceAll(".", "[$0]")
            .replace("[*]", ".*?")
            .replace("[?]", ".?"))
        .collect(Collectors.toList());
  }

  @Override
  public URL getResource(String name) {
    if (name.endsWith(".class")) return super.getResource(name);

    // load plain resource from local classpath
    URL url = findResource(name);
    if (url != null) {
      log.trace("Found resource '{}' in plugin classpath", name);
      return url;
    }
    log.trace("Couldn't find resource '{}' in plugin classpath. Delegating to parent", name);
    return super.getResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    return isPluginOnlyResources(name) ? findResources(name) : super.getResources(name);
  }

  @Override
  public Class<?> loadClass(String className) throws ClassNotFoundException {
    try {
      // if specified, try to load from plugin classpath first
      if (isPluginFirstClass(className)) {
        try {
          return loadClassFromPlugin(className);
        } catch (ClassNotFoundException ignored) {
        }
      }
      // not found, load from parent
      return super.loadClass(className);
    } catch (ClassNotFoundException ignored) {
    }

    // try again in in dependencies classpath
    return loadClassFromDependencies(className);
  }

  private boolean isPluginFirstClass(String name) {
    if (pluginFirstClasses == null || pluginFirstClasses.size() <= 0) return false;
    for (String pluginFirstClass : pluginFirstClasses) {
      if (name.matches(pluginFirstClass)) return true;
    }
    return false;
  }

  private boolean isPluginOnlyResources(String name) {
    if (pluginOnlyResources == null || pluginOnlyResources.size() <= 0) return false;
    for (String pluginOnlyResource : pluginOnlyResources) {
      if (name.matches(pluginOnlyResource)) return true;
    }
    return false;
  }

  private Class<?> loadClassFromPlugin(String className) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(className)) {
      log.trace("Received request to load class '{}'", className);

      // second check whether it's already been loaded
      Class<?> loadedClass = findLoadedClass(className);
      if (loadedClass != null) {
        log.trace("Found loaded class '{}'", className);
        return loadedClass;
      }

      // nope, try to load locally
      try {
        loadedClass = findClass(className);
        log.trace("Found class '{}' in plugin classpath", className);
        return loadedClass;
      } catch (ClassNotFoundException ignored) {
      }

      // try next step
      return loadClassFromDependencies(className);
    }
  }
}
