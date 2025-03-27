package net.xdob.pf4boot;

import com.google.common.base.Strings;
import net.xdob.pf4boot.internal.Pf4bootPluginFactory;
import net.xdob.pf4boot.internal.Pf4bootPluginStateListener;
import net.xdob.pf4boot.internal.SpringExtensionFactory;
import net.xdob.pf4boot.loader.JarPf4bootPluginLoader;
import net.xdob.pf4boot.loader.Pf4bootPluginLoader;
import net.xdob.pf4boot.loader.ZipPf4bootPluginLoader;
import net.xdob.pf4boot.modal.PluginError;
import net.xdob.pf4boot.spring.boot.*;
import org.pf4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.boot.Banner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

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
  private ConfigurableApplicationContext mainApplicationContext;
  public Map<String, Object> presetProperties = new HashMap<>();
  private boolean autoStartPlugin = true;
  private String[] profiles;
  private PluginRepository pluginRepository;
  private final Map<String, PluginError> pluginErrors = new HashMap<>();
  private final Pf4bootProperties properties;
  private final Pf4bootEventBus eventBus;

  public static final String PLUGINS_DIR_CONFIG_PROPERTY_NAME = "pf4j.pluginsConfigDir";

  private final List<Pf4bootPluginSupport> pluginSupports = new ArrayList<>();
  private ScheduledExecutorService scheduledExecutor;


  public Pf4bootPluginManagerImpl(Pf4bootProperties properties, Pf4bootEventBus eventBus,List<Pf4bootPluginSupport> pluginSupports, Path... pluginsRoots) {
    super(pluginsRoots);
    this.properties = properties;
    this.eventBus = eventBus;
    this.pluginSupports.addAll(pluginSupports);
    this.doInitialize();
  }


  @Override
  protected PluginDescriptorFinder createPluginDescriptorFinder() {
    return new CompoundPluginDescriptorFinder()
        .add(new PropertiesPluginDescriptorFinder())
        .add(new ManifestPluginDescriptorFinder2());
  }

  @Override
  protected ExtensionFinder createExtensionFinder() {
    DefaultExtensionFinder extensionFinder = new DefaultExtensionFinder(this);
    addPluginStateListener(extensionFinder);

    return extensionFinder;
  }

  @Override
  protected PluginFactory createPluginFactory() {
    return new Pf4bootPluginFactory();
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
        .add(new LinkPluginRepository(getPluginsRoots()))
        .add(new Pf4bootPluginRepository(getPluginsRoots()))
        .add(new ZipPluginRepository(getPluginsRoots()))
        .add(new DevelopmentPluginRepository(getPluginsRoots()), this::isDevelopment)
        .add(new JarPluginRepository(getPluginsRoots()), this::isNotDevelopment);
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
          .add(new JarPf4bootPluginLoader(this),this::isNotDevelopment)
          .add(new ZipPf4bootPluginLoader(this))
          .add(new Pf4bootPluginLoader(this, properties),this::isDevelopment);
    }
  }

  @Override
  protected VersionManager createVersionManager() {
    return new DefaultVersionManager();
  }

  @Override
  protected void initialize() {
    //延迟初始化，使用doInitialize()替代
  }

  protected void doInitialize() {
    super.initialize();
    addPluginStateListener(new Pf4bootPluginStateListener(eventBus));

    log.info("PF4J version {} in '{}' mode", getVersion(), getRuntimeMode());

  }


  @Override
  public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
    this.mainApplicationContext = (ConfigurableApplicationContext)applicationContext;

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
    }

    log.debug("Found descriptor {}", pluginDescriptor);
    String pluginClassName = pluginDescriptor.getPluginClass();
    log.debug("Class '{}' for plugin '{}'",  pluginClassName, pluginPath);

    // load plugin
    log.debug("Loading plugin '{}'", pluginPath);
    ClassLoader pluginClassLoader;
    try {
      pluginClassLoader = getPluginLoader().loadPlugin(pluginPath, pluginDescriptor);
    } catch (Exception e) {
      throw new PluginRuntimeException(e);
    }
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
  public synchronized void setMainApplicationStarted(boolean mainApplicationStarted) {
    this.mainApplicationStarted = mainApplicationStarted;
    if(mainApplicationStarted){
//      if(scheduledExecutor==null) {
//        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
//      }
//      scheduledExecutor.scheduleAtFixedRate(()->{
//        doStartPlugins(false);
//      }, 5, 20, TimeUnit.SECONDS);
    }else{
      scheduledExecutor.shutdown();
      scheduledExecutor = null;
    }
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
  public ConfigurableApplicationContext getMainApplicationContext() {
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

  public PluginError getPluginErrors(String pluginId) {
    return pluginErrors.get(pluginId);
  }

  @Override
  public Pf4bootEventBus getPf4bootEventBus() {
    return eventBus;
  }

  @Override
  public void post(Object event) {
    eventBus.post(event);
  }

  //*************************************************************************
  // Plugin State Manipulation
  //*************************************************************************

  protected void doStartPlugins(boolean startStoppedPlugin) {
    pluginErrors.clear();
    long ts = System.currentTimeMillis();
    for (PluginWrapper pluginWrapper : resolvedPlugins) {
      PluginState pluginState = pluginWrapper.getPluginState();
      if ((PluginState.DISABLED != pluginState) && (PluginState.STARTED != pluginState)
          && (startStoppedPlugin||PluginState.STOPPED != pluginState)) {
        try {
          doStartPlugin(pluginWrapper);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          pluginErrors.put(pluginWrapper.getPluginId(), PluginError.of(
              pluginWrapper.getPluginId(), e.getMessage(), e.toString()));
        }
      }
    }

    log.info("[PF4BOOT] {} plugins are started in {}ms. {} failed", getPlugins().size(),
        System.currentTimeMillis() - ts, pluginErrors.size());
  }


  @Override
  public void registerBeanToMainContext(String beanName, Object bean) {
    Assert.notNull(bean, "bean must not be null");
    beanName = Strings.isNullOrEmpty(beanName) ? bean.getClass().getName() : beanName;
    ConfigurableApplicationContext context = this.getMainApplicationContext();
    context.getBeanFactory().registerSingleton(beanName, bean);
    AppBeanRegisterEvent appRegisterBeanEvent = new AppBeanRegisterEvent(context, beanName, bean);
    context.publishEvent(appRegisterBeanEvent);
  }

  private void doStopPlugins() {
    pluginErrors.clear();

    // stop started plugins in reverse order
    Collections.reverse(startedPlugins);
    Iterator<PluginWrapper> itr = startedPlugins.iterator();
    while (itr.hasNext()) {
      PluginWrapper pluginWrapper = itr.next();
      PluginState pluginState = pluginWrapper.getPluginState();
      if (PluginState.STARTED == pluginState) {
        try {
          stopPlugin(pluginWrapper.getPluginId());
          itr.remove();
        } catch (PluginRuntimeException e) {
          log.error(e.getMessage(), e);
          pluginErrors.put(pluginWrapper.getPluginId(), PluginError.of(
              pluginWrapper.getPluginId(), e.getMessage(), e.toString()));
        }
      }
    }
  }


  /**
   * Release plugin holding release on stop.
   */
  public void releaseResource(Pf4bootPlugin pf4bootPlugin) {
  }

  @Override
  public void unregisterBeanFromMainContext(String beanName) {
    Assert.notNull(beanName, "bean must not be null");
    ConfigurableApplicationContext context = getMainApplicationContext();
    Object bean = context.getBean(beanName);
    ((AbstractAutowireCapableBeanFactory) context.getBeanFactory())
        .destroySingleton(beanName);
    AppBeanUnregisterEvent appUnRegisterBeanEvent = new AppBeanUnregisterEvent(context, beanName, bean);
    context.publishEvent(appUnRegisterBeanEvent);
  }


  @Override
  public void startPlugins() {
    doStartPlugins(false);
    mainApplicationContext.publishEvent(new AppCacheFreeEvent(mainApplicationContext));
  }

  @Override
  public PluginState startPlugin(String pluginId) {
    checkPluginId(pluginId);
    PluginWrapper pluginWrapper = getPlugin(pluginId);
    try{
      doStartPlugin(pluginWrapper);
    } catch (Throwable e) {
      log.warn("Plugin {} is failed to start", pluginWrapper.getPluginId(), e);
    }

    return pluginWrapper.getPluginState();
  }

  protected void doStartPlugin(PluginWrapper pluginWrapper) {
    PluginDescriptor pluginDescriptor = pluginWrapper.getDescriptor();
    PluginState pluginState = pluginWrapper.getPluginState();
    if (pluginState.isStarted()) {
      log.debug("Already started plugin '{}'", getPluginLabel(pluginDescriptor));
      return;
    }

    if (!resolvedPlugins.contains(pluginWrapper)) {
      log.warn("Cannot start an unresolved plugin '{}'", getPluginLabel(pluginDescriptor));
      return;
    }

    if (pluginState.isDisabled()) {
      // automatically enable plugin on manual plugin start
      if (!enablePlugin(pluginWrapper.getPluginId())) {
        return;
      }
    }

    for (PluginDependency dependency : pluginDescriptor.getDependencies()) {
      // start dependency only if it marked as required (non-optional) or if it optional and loaded
      if (!dependency.isOptional() || plugins.containsKey(dependency.getPluginId())) {
        startPlugin(dependency.getPluginId());
      }
    }
    log.info("Start plugin '{}'", getPluginLabel(pluginDescriptor));

    Plugin plugin = pluginWrapper.getPlugin();
    if(plugin instanceof Pf4bootPlugin){
      Pf4bootPlugin pf4bootPlugin = (Pf4bootPlugin)plugin;

      List<Pf4bootPluginSupport> pluginSupportList = getPf4bootPluginSupports();
      pluginSupportList.forEach(p->p.initiatePlugin(pf4bootPlugin));
      this.mainApplicationContext.getBeanFactory().autowireBean(pf4bootPlugin);
      pf4bootPlugin.initiate();
      pluginSupportList.forEach(p->p.initiatedPlugin(pf4bootPlugin));

      Pf4bootApplication application = pf4bootPlugin.getApplication();
      application.setBannerMode(Banner.Mode.OFF);
      application.setAllowBeanDefinitionOverriding(true);
      ConfigurableApplicationContext applicationContext = application.run();
      pf4bootPlugin.setApplicationContext(applicationContext);
      ApplicationContextProvider.registerApplicationContext(applicationContext);
      this.post(new PreStartPluginEvent(pf4bootPlugin));
      pluginSupportList.forEach(p->p.startPlugin(pf4bootPlugin));
      this.post(new StartingPluginEvent(pf4bootPlugin));
      plugin.start();
      this.post(new StartedPluginEvent(pf4bootPlugin));
      pluginSupportList.forEach(p->p.startedPlugin(pf4bootPlugin));

    }else {
      plugin.start();
    }
    startedPlugins.add(pluginWrapper);
    pluginWrapper.setPluginState(PluginState.STARTED);
    firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, PluginState.STARTED));
    mainApplicationContext.publishEvent(new AppCacheFreeEvent(mainApplicationContext));
  }

  @Override
  public void stopPlugins() {
    doStopPlugins();
    mainApplicationContext.publishEvent(new AppCacheFreeEvent(mainApplicationContext));
  }

  @Override
  public PluginState stopPlugin(String pluginId) {
    PluginState state = stopPlugin(pluginId, true);

    mainApplicationContext.publishEvent(new AppCacheFreeEvent(mainApplicationContext));
    return state;
  }

  protected PluginState stopPlugin(String pluginId, boolean stopDependents) {
    checkPluginId(pluginId);

    // test for started plugin
    if (!checkPluginState(pluginId, PluginState.STARTED)) {
      // do nothing
      log.debug("Plugin '{}' is not started, nothing to stop", getPluginLabel(pluginId));
      return getPlugin(pluginId).getPluginState();
    }

    if (stopDependents) {
      List<String> dependents = dependencyResolver.getDependents(pluginId);
      while (!dependents.isEmpty()) {
        String dependent = dependents.remove(0);
        stopPlugin(dependent, false);
        dependents.addAll(0, dependencyResolver.getDependents(dependent));
      }
    }

    log.info("Stop plugin '{}'", getPluginLabel(pluginId));
    PluginWrapper pluginWrapper = getPlugin(pluginId);

    Plugin plugin = pluginWrapper.getPlugin();
    if(plugin instanceof Pf4bootPlugin) {
      List<Pf4bootPluginSupport> pluginSupportList = getPf4bootPluginSupports();
      Pf4bootPlugin pf4bootPlugin = (Pf4bootPlugin) plugin;

      this.post(new PreStopPluginEvent(pf4bootPlugin));

      for (Pf4bootPluginSupport pluginSupport : pluginSupportList) {
        pluginSupport.stopPlugin(pf4bootPlugin);
      }

      pf4bootPlugin.stop();
      this.post(new StoppingPluginEvent(pf4bootPlugin));

      releaseResource(pf4bootPlugin);
      ConfigurableApplicationContext applicationContext = pf4bootPlugin.getApplicationContext();
      //applicationContext.publishEvent(new Pf4bootPluginStoppedEvent(applicationContext));


      for (Pf4bootPluginSupport pluginSupport : pluginSupportList) {
        pluginSupport.stoppedPlugin(pf4bootPlugin);
      }

      this.post(new StoppedPluginEvent(pf4bootPlugin));
      firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, PluginState.STOPPED));
      ApplicationContextProvider.unregisterApplicationContext(applicationContext);
      applicationContext.close();

    }else {
      plugin.stop();
    }

    pluginWrapper.setPluginState(PluginState.STOPPED);
    getStartedPlugins().remove(pluginWrapper);

    firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, PluginState.STOPPED));

    return PluginState.STOPPED;
  }

  private List<Pf4bootPluginSupport> getPf4bootPluginSupports() {
    return pluginSupports.stream()
        .sorted(Comparator.comparingInt(Pf4bootPluginSupport::getPriority).reversed())
        .collect(Collectors.toList());
  }


  @Override
  public void restartPlugins() {
    doStopPlugins();
    startPlugins();
  }

  @Override
  public PluginState restartPlugin(String pluginId) {
    PluginWrapper plugin = getPlugin(pluginId);
    if(plugin.getPluginState().equals(PluginState.STARTED)){
      this.stopPlugin(pluginId);
    }
    PluginState pluginState = this.startPlugin(pluginId);
    mainApplicationContext.publishEvent(new AppCacheFreeEvent(mainApplicationContext));
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
          this.startPlugin(pluginId);
        }
      });
      mainApplicationContext.publishEvent(new AppCacheFreeEvent(mainApplicationContext));
    } else {
      startPlugins();
    }
  }

  @Override
  public PluginState reloadPlugins(String pluginId) {
    PluginWrapper plugin = getPlugin(pluginId);
    this.stopPlugin(pluginId);
    unloadPlugin(pluginId);
    loadPlugin(plugin.getPluginPath());
    PluginState state = this.startPlugin(pluginId);
    mainApplicationContext.publishEvent(new AppCacheFreeEvent(mainApplicationContext));
    return state;
  }

  @Override
  public String loadPlugin(Path pluginPath) {
    return super.loadPlugin(pluginPath);
  }

  @Override
  public void unloadPlugins() {
    super.unloadPlugins();
  }


  @Override
  protected boolean unloadPlugin(String pluginId, boolean unloadDependents, boolean resolveDependencies) {
    if (unloadDependents) {
      List<String> dependents = dependencyResolver.getDependents(pluginId);
      while (!dependents.isEmpty()) {
        String dependent = dependents.remove(0);
        unloadPlugin(dependent, false, false);
        dependents.addAll(0, dependencyResolver.getDependents(dependent));
      }
    }

    if (!plugins.containsKey(pluginId)) {
      // nothing to do
      return false;
    }

    PluginWrapper pluginWrapper = getPlugin(pluginId);
    PluginState pluginState;
    try {
      pluginState = stopPlugin(pluginId, false);
      if (pluginState.isStarted()) {
        return false;
      }

      log.info("Unload plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()));
    } catch (Exception e) {
      log.error("Cannot stop plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()), e);
      pluginState = PluginState.FAILED;
    }

    // remove the plugin
    pluginWrapper.setPluginState(PluginState.UNLOADED);
    plugins.remove(pluginId);
    getResolvedPlugins().remove(pluginWrapper);
    getUnresolvedPlugins().remove(pluginWrapper);

    firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, pluginState));

    // remove the classloader
    Map<String, ClassLoader> pluginClassLoaders = getPluginClassLoaders();
    if (pluginClassLoaders.containsKey(pluginId)) {
      ClassLoader classLoader = pluginClassLoaders.remove(pluginId);
      if (classLoader instanceof Closeable) {
        try {
          ((Closeable) classLoader).close();
        } catch (IOException e) {
          throw new PluginRuntimeException(e, "Cannot close classloader");
        }
      }
    }

    // resolve the plugins again (update plugins graph)
    if (resolveDependencies) {
      resolveDependencies();
    }

    return true;
  }


  @Override
  public boolean deletePlugin(String pluginId) {
    checkPluginId(pluginId);

    PluginWrapper pluginWrapper = getPlugin(pluginId);
    // stop the plugin if it's started
    PluginState pluginState = stopPlugin(pluginId);
    if (pluginState.isStarted()) {
      log.error("Failed to stop plugin '{}' on delete", pluginId);
      return false;
    }

    // get an instance of plugin before the plugin is unloaded
    // for reason see https://github.com/pf4j/pf4j/issues/309
    Plugin plugin = pluginWrapper.getPlugin();

    if (!unloadPlugin(pluginId)) {
      log.error("Failed to unload plugin '{}' on delete", pluginId);
      return false;
    }
    if(plugin instanceof Pf4bootPlugin) {
      Pf4bootPlugin pf4bootPlugin = (Pf4bootPlugin) plugin;
      List<Pf4bootPluginSupport> pluginSupportList = getPf4bootPluginSupports();
      pluginSupportList.forEach(p -> p.deletePlugin(pf4bootPlugin));
      pf4bootPlugin.delete();
      pluginSupportList.forEach(p -> p.deletedPlugin(pf4bootPlugin));
    }else {
      // notify the plugin as it's deleted
      plugin.delete();
    }

    return pluginRepository.deletePluginPath(pluginWrapper.getPluginPath());
  }
}
