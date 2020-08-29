package com.ls.pf4boot;

import com.ls.pf4boot.loader.JarPf4bootPluginLoader;
import com.ls.pf4boot.loader.Pf4bootPluginLoader;
import com.ls.pf4boot.loader.ZipPf4bootPluginLoader;
import com.ls.pf4boot.modal.PluginStartingError;
import com.ls.pf4boot.spring.boot.Pf4bootPluginStateChangedEvent;
import com.ls.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.PostConstruct;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Pf4bootPluginManagerImpl
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginManagerImpl extends AbstractPluginManager
    implements Pf4bootPluginManager, ApplicationContextAware {
  static final Logger log = LoggerFactory.getLogger(Pf4bootPluginManagerImpl.class);

  private volatile boolean mainApplicationStarted;
  private ApplicationContext mainApplicationContext;
  public Map<String, Object> presetProperties = new HashMap<>();
  private boolean autoStartPlugin = true;
  private String[] profiles;
  private PluginRepository pluginRepository;
  private final Map<String, PluginStartingError> startingErrors = new HashMap<>();
  private Pf4bootProperties properties;

  public static final String PLUGINS_DIR_CONFIG_PROPERTY_NAME = "pf4j.pluginsConfigDir";


  public Pf4bootPluginManagerImpl(Pf4bootProperties properties) {
    this.properties = properties;
    this.doInitialize();
  }

  public Pf4bootPluginManagerImpl(Path pluginsRoot, Pf4bootProperties properties) {
    this.pluginsRoot = pluginsRoot;
    this.properties = properties;
    this.doInitialize();
  }


  @Override
  protected PluginDescriptorFinder createPluginDescriptorFinder() {
    return new CompoundPluginDescriptorFinder()
        .add(new PropertiesPluginDescriptorFinder2())
        .add(new ManifestPluginDescriptorFinder());
  }

  @Override
  protected ExtensionFinder createExtensionFinder() {
    DefaultExtensionFinder extensionFinder = new DefaultExtensionFinder(this);
    addPluginStateListener(extensionFinder);

    return extensionFinder;
  }

  @Override
  protected PluginFactory createPluginFactory() {
    return new DefaultPluginFactory();
  }

  @Override
  protected ExtensionFactory createExtensionFactory() {
    return new SpringExtensionFactory(this);
  }

  @Override
  protected PluginStatusProvider createPluginStatusProvider() {
    String configDir = System.getProperty(PLUGINS_DIR_CONFIG_PROPERTY_NAME);
    Path configPath = configDir != null ? Paths.get(configDir) : getPluginsRoot();
    return new DefaultPluginStatusProvider(configPath);
  }

  @Override
  protected PluginRepository createPluginRepository() {
    pluginRepository = new CompoundPluginRepository()
        .add(new LinkPluginRepository(pluginsRoot))
        .add(new Pf4bootPluginRepository(pluginsRoot))
        .add(new ZipPluginRepository(pluginsRoot))
        .add(new DevelopmentPluginRepository(pluginsRoot), this::isDevelopment)
        .add(new JarPluginRepository(pluginsRoot), this::isNotDevelopment);
    return pluginRepository;
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
          .add(new ZipPf4bootPluginLoader(this))
          .add(new Pf4bootPluginLoader(this, properties),this::isDevelopment)
          .add(new JarPf4bootPluginLoader(this),this::isDevelopment);
    }
  }

  @Override
  protected VersionManager createVersionManager() {
    return new DefaultVersionManager();
  }

  @Override
  protected void initialize() {

  }

  protected void doInitialize() {
    super.initialize();

    if (isDevelopment()) {
      addPluginStateListener(new LoggingPluginStateListener());
    }

    log.info("PF4J version {} in '{}' mode", getVersion(), getRuntimeMode());
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
  public void loadPlugins() {
    super.loadPlugins();
  }



  @Override
  protected PluginWrapper loadPluginFromPath(Path pluginPath) {
    // Test for plugin path duplication
    String pluginId = idForPath(pluginPath);
    if (pluginId != null) {
      throw new PluginAlreadyLoadedException(pluginId, pluginPath);
    }

    // Retrieve and validate the plugin descriptor
    PluginDescriptorFinder pluginDescriptorFinder = getPluginDescriptorFinder();
    log.debug("Use '{}' to find plugins descriptors", pluginDescriptorFinder);
    log.debug("Finding plugin descriptor for plugin '{}'", pluginPath);
    PluginDescriptor pluginDescriptor = pluginDescriptorFinder.find(pluginPath);
    validatePluginDescriptor(pluginDescriptor);

    // Check there are no loaded plugins with the retrieved id
    pluginId = pluginDescriptor.getPluginId();
    if (plugins.containsKey(pluginId)) {
      PluginWrapper loadedPlugin = getPlugin(pluginId);
      log.warn("duplicate plugin found : {}, {} and {}",pluginId,loadedPlugin.getDescriptor().getVersion(), pluginDescriptor.getVersion());
      if(versionManager.compareVersions(loadedPlugin.getDescriptor().getVersion(),pluginDescriptor.getVersion())<0){
        unloadPlugin(pluginId);
      }else{
        return loadedPlugin;
      }
      /*
      throw new PluginRuntimeException("There is an already loaded plugin ({}) "
          + "with the same id ({}) as the plugin at path '{}'. Simultaneous loading "
          + "of plugins with the same PluginId is not currently supported.\n"
          + "As a workaround you may include PluginVersion and PluginProvider "
          + "in PluginId.",
          loadedPlugin, pluginId, pluginPath);//*/
    }

    log.debug("Found descriptor {}", pluginDescriptor);
    String pluginClassName = pluginDescriptor.getPluginClass();
    log.debug("Class '{}' for plugin '{}'",  pluginClassName, pluginPath);

    // load plugin
    log.debug("Loading plugin '{}'", pluginPath);
    ClassLoader pluginClassLoader = getPluginLoader().loadPlugin(pluginPath, pluginDescriptor);
    log.debug("Loaded plugin '{}' with class loader '{}'", pluginPath, pluginClassLoader);

    // create the plugin wrapper
    log.debug("Creating wrapper for plugin '{}'", pluginPath);
    PluginWrapper pluginWrapper = new PluginWrapper(this, pluginDescriptor, pluginPath, pluginClassLoader);
    pluginWrapper.setPluginFactory(getPluginFactory());

    // test for disabled plugin
    if (isPluginDisabled(pluginDescriptor.getPluginId())) {
      log.info("Plugin '{}' is disabled", pluginPath);
      pluginWrapper.setPluginState(PluginState.DISABLED);
    }

    // validate the plugin
    if (!isPluginValid(pluginWrapper)) {
      log.warn("Plugin '{}' is invalid and it will be disabled", pluginPath);
      pluginWrapper.setPluginState(PluginState.DISABLED);
    }

    log.debug("Created wrapper '{}' for plugin '{}'", pluginWrapper, pluginPath);

    pluginId = pluginDescriptor.getPluginId();

    // add plugin to the list with plugins
    plugins.put(pluginId, pluginWrapper);
    getUnresolvedPlugins().add(pluginWrapper);

    // add plugin class loader to the list with class loaders
    getPluginClassLoaders().put(pluginId, pluginClassLoader);

    return pluginWrapper;
  }

  public PluginRepository getPluginRepository() {
    return pluginRepository;
  }

  @Override
  public void setAutoStartPlugin(boolean autoStartPlugin) {
    this.autoStartPlugin = autoStartPlugin;
  }

  @Override
  public boolean isAutoStartPlugin() {
    return autoStartPlugin;
  }

  @Override
  public void setMainApplicationStarted(boolean mainApplicationStarted) {
    this.mainApplicationStarted = mainApplicationStarted;
  }

  @Override
  public void setProfiles(String[] profiles) {
    this.profiles = profiles;
  }

  @Override
  public String[] getProfiles() {
    return profiles;
  }

  @Override
  public void presetProperties(Map<String, Object> presetProperties) {
    this.presetProperties.putAll(presetProperties);
  }

  @Override
  public void presetProperties(String name, Object value) {
    this.presetProperties.put(name, value);
  }

  @Override
  public Map<String, Object> getPresetProperties() {
    return presetProperties;
  }

  @Override
  public ApplicationContext getMainApplicationContext() {
    return mainApplicationContext;
  }

  @Override
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

  @Override
  public void restartPlugins() {
    doStopPlugins();
    startPlugins();
  }

  @Override
  public PluginState restartPlugin(String pluginId) {
    PluginState pluginState = doStopPlugin(pluginId, false);
    if (pluginState != PluginState.STARTED) doStartPlugin(pluginId, false);
    doStartPlugin(pluginId, false);
    mainApplicationContext.publishEvent(new Pf4bootPluginStateChangedEvent(mainApplicationContext));
    return pluginState;
  }

  @Override
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

  @Override
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
