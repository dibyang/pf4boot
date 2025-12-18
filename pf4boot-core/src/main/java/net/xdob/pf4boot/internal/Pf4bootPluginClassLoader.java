package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.PluginClassLoader4boot;
import net.xdob.pf4boot.util.SpringCglibCleaner;
import org.pf4j.ClassLoadingStrategy;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pf4bootPluginClassLoader
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginClassLoader extends PluginClassLoader implements PluginClassLoader4boot {

  private static final Logger log = LoggerFactory.getLogger(Pf4bootPluginClassLoader.class);


  private List<String> pluginOnlyResources;

  private final PluginManager pluginManager;
  private final PluginDescriptor pluginDescriptor;
  private final ClassLoadingStrategy classLoadingStrategy;

  public Pf4bootPluginClassLoader(PluginManager pluginManager, PluginDescriptor pluginDescriptor) {
    // load class from parent first to avoid same class loaded by different classLoader,
    // so Spring could autowired bean by type correctly.
    this(pluginManager, pluginDescriptor, ((Pf4bootPluginManager)pluginManager).getApplicationContext().getClassLoader());
  }

  public Pf4bootPluginClassLoader(PluginManager pluginManager, PluginDescriptor pluginDescriptor, ClassLoader parent) {
    this(pluginManager, pluginDescriptor, parent, ClassLoadingStrategy.PDA);
  }

  public Pf4bootPluginClassLoader(PluginManager pluginManager, PluginDescriptor pluginDescriptor, ClassLoader parent, ClassLoadingStrategy classLoadingStrategy) {
    super(pluginManager, pluginDescriptor, parent, classLoadingStrategy);
    this.pluginManager = pluginManager;
    this.pluginDescriptor = pluginDescriptor;
    this.classLoadingStrategy =  classLoadingStrategy;
  }

	@Override
	public void close() throws IOException {
		super.close();
		cleanup();
	}

	@Override
	public void cleanup() {
//		try {
//			SpringCglibCleaner.clearAll(this);
//		} catch (Exception e) {
//			log.warn("Failed to clean up classes", e);
//		}
	}



  @Override
  public void setPluginFirstClasses(List<String> pluginFirstClasses) {

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


  private boolean isPluginOnlyResources(String name) {
    if (pluginOnlyResources == null || pluginOnlyResources.size() <= 0) return false;
    for (String pluginOnlyResource : pluginOnlyResources) {
      if (name.matches(pluginOnlyResource)) return true;
    }
    return false;
  }



  @Override
  public String toString() {
    return "Pf4bootPluginClassLoader["+pluginDescriptor.getPluginId()+"]";
  }
}
