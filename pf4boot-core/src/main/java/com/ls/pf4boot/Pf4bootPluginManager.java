package com.ls.pf4boot;

import com.ls.pf4boot.loader.JarPf4bootPluginLoader;
import com.ls.pf4boot.loader.Pf4bootPluginLoader;
import com.ls.pf4boot.spring.boot.Pf4bootProperties;
import com.ls.pf4boot.spring.boot.PluginStartingError;
import com.ls.pf4boot.spring.boot.Pf4bootPluginStateChangedEvent;
import com.ls.pf4boot.internal.SpringExtensionFactory;
import com.ls.pf4boot.spring.boot.PropertyPluginStatusProvider;
import org.pf4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.PostConstruct;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.*;

/**
 * Pf4bootPluginManager
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginManager extends DefaultPluginManager
    implements ApplicationContextAware {
  static final Logger log = LoggerFactory.getLogger(Pf4bootPluginManager.class);

  private volatile boolean mainApplicationStarted;
  private ApplicationContext mainApplicationContext;
  public Map<String, Object> presetProperties = new HashMap<>();
  private boolean autoStartPlugin = true;
  private String[] profiles;
  private PluginRepository pluginRepository;
  private final Map<String, PluginStartingError> startingErrors = new HashMap<>();
  private Pf4bootProperties properties;

  public Pf4bootPluginManager(Pf4bootProperties properties) {
    this.properties = properties;
    this.initialize();
  }

  public Pf4bootPluginManager(Path pluginsRoot, Pf4bootProperties properties) {
    this.pluginsRoot = pluginsRoot;
    this.properties = properties;
    this.initialize();
  }

  @Override
  protected void initialize() {
    if(properties!=null) {
      super.initialize();
    }
  }

  @Override
  protected ExtensionFactory createExtensionFactory() {
    return new SpringExtensionFactory(this);
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.mainApplicationContext = applicationContext;
  }

  @Override
  public PluginDescriptorFinder getPluginDescriptorFinder() {
    return super.getPluginDescriptorFinder();
  }

  @Override
  protected PluginRepository createPluginRepository() {
    this.pluginRepository = new CompoundPluginRepository()
        .add(new LinkPluginRepository(getPluginsRoot()))
        .add(new DevelopmentPluginRepository(getPluginsRoot()), this::isDevelopment)
        .add(new JarPluginRepository(getPluginsRoot()), this::isNotDevelopment)
        .add(new DefaultPluginRepository(getPluginsRoot()), this::isNotDevelopment);
    return this.pluginRepository;
  }

  @Override
  protected PluginDescriptorFinder createPluginDescriptorFinder() {
    return new CompoundPluginDescriptorFinder()
        .add(new PropertiesPluginDescriptorFinder2())
        .add(new ManifestPluginDescriptorFinder());
  }

  @Override
  protected PluginLoader createPluginLoader() {
    if (properties.getCustomPluginLoader() != null) {
      Class<PluginLoader> clazz = properties.getCustomPluginLoader();
      try {
        Constructor<?> constructor = clazz.getConstructor(PluginManager.class);
        return (PluginLoader) constructor.newInstance(this);
      } catch (Exception ex) {
        throw new IllegalArgumentException(String.format("Create custom PluginLoader %s failed. Make sure" +
            "there is a constructor with one argument that accepts PluginLoader", clazz.getName()));
      }
    } else {
      return new CompoundPluginLoader()
          .add(new Pf4bootPluginLoader(this,properties), this::isDevelopment)
          .add(new JarPf4bootPluginLoader(this));
    }
  }

  @Override
  protected PluginStatusProvider createPluginStatusProvider() {
    if (PropertyPluginStatusProvider.isPropertySet(properties)) {
      return new PropertyPluginStatusProvider(properties);
    }
    return super.createPluginStatusProvider();
  }

  public PluginRepository getPluginRepository() {
    return pluginRepository;
  }

  public void setAutoStartPlugin(boolean autoStartPlugin) {
    this.autoStartPlugin = autoStartPlugin;
  }

  public boolean isAutoStartPlugin() {
    return autoStartPlugin;
  }

  public void setMainApplicationStarted(boolean mainApplicationStarted) {
    this.mainApplicationStarted = mainApplicationStarted;
  }

  public void setProfiles(String[] profiles) {
    this.profiles = profiles;
  }

  public String[] getProfiles() {
    return profiles;
  }

  public void presetProperties(Map<String, Object> presetProperties) {
    this.presetProperties.putAll(presetProperties);
  }

  public void presetProperties(String name, Object value) {
    this.presetProperties.put(name, value);
  }

  public Map<String, Object> getPresetProperties() {
    return presetProperties;
  }

  public ApplicationContext getMainApplicationContext() {
    return mainApplicationContext;
  }

  public boolean isMainApplicationStarted() {
    return mainApplicationStarted;
  }

  /**
   * This method load, start plugins and inject extensions in Spring
   */
  @PostConstruct
  public void init() {
    loadPlugins();
  }

  public PluginStartingError getPluginStartingError(String pluginId) {
    return startingErrors.get(pluginId);
  }

  //*************************************************************************
  // Plugin State Manipulation
  //*************************************************************************

  private void doStartPlugins() {
    startingErrors.clear();
    long ts = System.currentTimeMillis();

    for (PluginWrapper pluginWrapper : resolvedPlugins) {
      PluginState pluginState = pluginWrapper.getPluginState();
      if ((PluginState.DISABLED != pluginState) && (PluginState.STARTED != pluginState)) {
        try {
          pluginWrapper.getPlugin().start();
          pluginWrapper.setPluginState(PluginState.STARTED);
          startedPlugins.add(pluginWrapper);

          firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, pluginState));
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          startingErrors.put(pluginWrapper.getPluginId(), PluginStartingError.of(
              pluginWrapper.getPluginId(), e.getMessage(), e.toString()));
        }
      }
    }

    log.info("[PF4BOOT] {} plugins are started in {}ms. {} failed", getPlugins().size(),
        System.currentTimeMillis() - ts, startingErrors.size());
  }

  private void doStopPlugins() {
    startingErrors.clear();
    // stop started plugins in reverse order
    Collections.reverse(startedPlugins);
    Iterator<PluginWrapper> itr = startedPlugins.iterator();
    while (itr.hasNext()) {
      PluginWrapper pluginWrapper = itr.next();
      PluginState pluginState = pluginWrapper.getPluginState();
      if (PluginState.STARTED == pluginState) {
        try {
          log.info("Stop plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()));
          pluginWrapper.getPlugin().stop();
          pluginWrapper.setPluginState(PluginState.STOPPED);
          itr.remove();

          firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, pluginState));
        } catch (PluginRuntimeException e) {
          log.error(e.getMessage(), e);
          startingErrors.put(pluginWrapper.getPluginId(), PluginStartingError.of(
              pluginWrapper.getPluginId(), e.getMessage(), e.toString()));
        }
      }
    }
  }

  private PluginState doStartPlugin(String pluginId, boolean sendEvent) {
    PluginWrapper plugin = getPlugin(pluginId);
    try {
      PluginState pluginState = super.startPlugin(pluginId);
      if (sendEvent) mainApplicationContext.publishEvent(new Pf4bootPluginStateChangedEvent(mainApplicationContext));
      return pluginState;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      startingErrors.put(plugin.getPluginId(), PluginStartingError.of(
          plugin.getPluginId(), e.getMessage(), e.toString()));
    }
    return plugin.getPluginState();
  }

  private PluginState doStopPlugin(String pluginId, boolean sendEvent) {
    PluginWrapper plugin = getPlugin(pluginId);
    try {
      PluginState pluginState = super.stopPlugin(pluginId);
      if (sendEvent) mainApplicationContext.publishEvent(new Pf4bootPluginStateChangedEvent(mainApplicationContext));
      return pluginState;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      startingErrors.put(plugin.getPluginId(), PluginStartingError.of(
          plugin.getPluginId(), e.getMessage(), e.toString()));
    }
    return plugin.getPluginState();
  }

  @Override
  public void startPlugins() {
    doStartPlugins();
    mainApplicationContext.publishEvent(new Pf4bootPluginStateChangedEvent(mainApplicationContext));
  }

  @Override
  public PluginState startPlugin(String pluginId) {
    return doStartPlugin(pluginId, true);
  }

  @Override
  public void stopPlugins() {
    doStopPlugins();
    mainApplicationContext.publishEvent(new Pf4bootPluginStateChangedEvent(mainApplicationContext));
  }

  @Override
  public PluginState stopPlugin(String pluginId) {
    return doStopPlugin(pluginId, true);
  }

  public void restartPlugins() {
    doStopPlugins();
    startPlugins();
  }

  public PluginState restartPlugin(String pluginId) {
    PluginState pluginState = doStopPlugin(pluginId, false);
    if (pluginState != PluginState.STARTED) doStartPlugin(pluginId, false);
    doStartPlugin(pluginId, false);
    mainApplicationContext.publishEvent(new Pf4bootPluginStateChangedEvent(mainApplicationContext));
    return pluginState;
  }

  public void reloadPlugins(boolean restartStartedOnly) {
    doStopPlugins();
    List<String> startedPluginIds = new ArrayList<>();
    getPlugins().forEach(plugin -> {
      if (plugin.getPluginState() == PluginState.STARTED) {
        startedPluginIds.add(plugin.getPluginId());
      }
      unloadPlugin(plugin.getPluginId());
    });
    loadPlugins();
    if (restartStartedOnly) {
      startedPluginIds.forEach(pluginId -> {
        // restart started plugin
        if (getPlugin(pluginId) != null) {
          doStartPlugin(pluginId, false);
        }
      });
      mainApplicationContext.publishEvent(new Pf4bootPluginStateChangedEvent(mainApplicationContext));
    } else {
      startPlugins();
    }
  }

  public PluginState reloadPlugins(String pluginId) {
    PluginWrapper plugin = getPlugin(pluginId);
    doStopPlugin(pluginId, false);
    unloadPlugin(pluginId);
    try {
      loadPlugin(plugin.getPluginPath());
    } catch (Exception ex) {
      return null;
    }

    return doStartPlugin(pluginId, true);
  }

}
