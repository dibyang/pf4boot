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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pf4bootPluginClassLoader
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginClassLoader extends PluginClassLoader implements PluginClassLoader4boot {

  private static final Logger log = LoggerFactory.getLogger(Pf4bootPluginClassLoader.class);


  private List<Pattern> pluginFirstClasses = Collections.emptyList();

  private List<Pattern> pluginOnlyResources = Collections.emptyList();

  final PluginManager pluginManager;
  final PluginDescriptor pluginDescriptor;
  final ClassLoadingStrategy classLoadingStrategy;


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
	}

  @Override
  public void setPluginFirstClasses(List<String> pluginFirstClasses) {
    this.pluginFirstClasses = toPatterns(pluginFirstClasses);
  }

  @Override
  public void setPluginOnlyResources(List<String> pluginOnlyResources) {
    this.pluginOnlyResources = toPatterns(pluginOnlyResources);
  }

  @Override
  public Class<?> loadClass(String className) throws ClassNotFoundException {
    if (!isPluginFirstClass(className)) {
      return super.loadClass(className);
    }

    synchronized (getClassLoadingLock(className)) {
      Class<?> loadedClass = findLoadedClass(className);
      if (loadedClass != null) {
        return loadedClass;
      }

      try {
        return findClass(className);
      } catch (ClassNotFoundException ignored) {
        return super.loadClass(className);
      }
    }
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
    return matches(pluginOnlyResources, name);
  }

  private boolean isPluginFirstClass(String className) {
    if (className.startsWith("java.") || shouldDelegateToParent(className)) {
      return false;
    }
    return matches(pluginFirstClasses, className);
  }

  private boolean matches(List<Pattern> patterns, String value) {
    for (Pattern pattern : patterns) {
      if (pattern.matcher(value).matches()) {
        return true;
      }
    }
    return false;
  }

  private List<Pattern> toPatterns(List<String> globs) {
    if (globs == null || globs.isEmpty()) {
      return Collections.emptyList();
    }

    return globs.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(glob -> !glob.isEmpty())
        .map(this::toPattern)
        .collect(Collectors.toList());
  }

  private Pattern toPattern(String glob) {
    StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < glob.length(); i++) {
      char ch = glob.charAt(i);
      if (ch == '*') {
        regex.append(".*");
      } else if (ch == '?') {
        regex.append('.');
      } else {
        if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
          regex.append('\\');
        }
        regex.append(ch);
      }
    }
    regex.append('$');
    return Pattern.compile(regex.toString());
  }


  @Override
  public String toString() {
    return "Pf4bootPluginClassLoader["+pluginDescriptor.getPluginId()+"]";
  }
}
