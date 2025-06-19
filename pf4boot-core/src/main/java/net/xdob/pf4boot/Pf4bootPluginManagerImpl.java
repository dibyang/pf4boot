package net.xdob.pf4boot;

import com.google.common.base.Strings;
import net.xdob.pf4boot.internal.Pf4bootPluginFactory;
import net.xdob.pf4boot.internal.Pf4bootPluginStateListener;
import net.xdob.pf4boot.internal.SpringExtensionFactory;
import net.xdob.pf4boot.loader.JarPf4bootPluginLoader;
import net.xdob.pf4boot.loader.Pf4bootPluginLoader;
import net.xdob.pf4boot.loader.ZipPf4bootPluginLoader;
import net.xdob.pf4boot.modal.PluginError;
import net.xdob.pf4boot.modal.SharingScope;
import net.xdob.pf4boot.spring.boot.*;
import org.pf4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;


/**
 * Pf4bootPluginManagerImpl
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginManagerImpl extends AbstractPluginManager
    implements Pf4bootPluginManager {
  static final Logger LOG = LoggerFactory.getLogger(Pf4bootPluginManagerImpl.class);

  private volatile boolean mainApplicationStarted;
  /**
   * 根级上下文，全局可见
   */
  private ConfigurableApplicationContext rootContext = null;
  /**
   * 应用级上下文
   */
  private final ConfigurableApplicationContext applicationContext;

  private final ConcurrentHashMap<String, ConfigurableApplicationContext> platformContexts = new ConcurrentHashMap<>();
  private final ConfigurableApplicationContext platformContext;

  public Map<String, Object> presetProperties = new HashMap<>();
  private boolean autoStartPlugin = true;
  private String[] profiles;
  private PluginRepository pluginRepository;
  private final Map<String, PluginError> pluginErrors = new HashMap<>();
  private final Pf4bootProperties properties;

  public static final String PLUGINS_DIR_CONFIG_PROPERTY_NAME = "pf4j.pluginsConfigDir";


  private final ObjectProvider<Pf4bootPluginSupport> pluginSupportProvider;
  private ScheduledExecutorService scheduled;


  public Pf4bootPluginManagerImpl(ApplicationContext applicationContext, Pf4bootProperties properties,
                                  ObjectProvider<Pf4bootPluginSupport> pluginSupportProvider, Path... pluginsRoots) {
    super(pluginsRoots);
    this.applicationContext = (ConfigurableApplicationContext)applicationContext;

    this.properties = properties;
    this.pluginSupportProvider = pluginSupportProvider;
    this.rootContext = new AnnotationConfigApplicationContext();
    this.rootContext.refresh();
    ConfigurableApplicationContext topContext = (ConfigurableApplicationContext)getTopContext(applicationContext);
    topContext.setParent(this.rootContext);
    //仅共享Bean定义，不继承事件监听链1
    topContext.getBeanFactory().setParentBeanFactory(this.rootContext.getBeanFactory());
    platformContext = new AnnotationConfigApplicationContext();
    platformContext.setId("platform");
    platformContext.setParent(this.rootContext);
    platformContext.refresh();
    //仅共享Bean定义，不继承事件监听链1
    platformContext.getBeanFactory().setParentBeanFactory(this.rootContext.getBeanFactory());
    this.doInitialize();

  }

  /**
   * 获取顶层上下文
   */
  private ApplicationContext getTopContext(ApplicationContext context) {
    while (context.getParent() != null){
      context = context.getParent();
    }
    return context;
  }

  /**
   * 根级上下文，全局可见
   */
  public ConfigurableApplicationContext getRootContext() {
    return rootContext;
  }

  /**
   * 平台级上下文，插件间共享
   */
  public ConfigurableApplicationContext getPlatformContext(String group) {
    if(group==null||group.isEmpty()){
      return platformContext;
    }

		return platformContexts.computeIfAbsent(Strings.nullToEmpty(group),
			k -> {
				ConfigurableApplicationContext context = new AnnotationConfigApplicationContext();
				context.setId("platform_"+k);
				context.setParent(platformContext);
				context.refresh();
				//仅共享Bean定义，不继承事件监听链1
				context.getBeanFactory().setParentBeanFactory(platformContext.getBeanFactory());
				LOG.info("create PlatformContext {} for {}", context.getId(), group);
				return context;
			});
  }

  public void publishEvent(ConfigurableApplicationContext pluginContext, Object event){

    rootContext.publishEvent(event);
    applicationContext.publishEvent(event);
    platformContext.publishEvent(event);
    platformContexts.values().forEach(platformContext -> platformContext.publishEvent(event));
    List<ConfigurableApplicationContext> contexts = new ArrayList<>();
    for (PluginWrapper startedPlugin : getPlugins(PluginState.STARTED)) {
      if(startedPlugin.getPluginState().isStarted()){
        contexts.add(((Pf4bootPlugin)startedPlugin.getPlugin()).getPluginContext());
      }
    }
    if(pluginContext!=null){
      pluginContext.publishEvent(event);
      contexts.removeIf(e-> Objects.equals(e.getId(), pluginContext.getId()));
    }
    for (ConfigurableApplicationContext context : contexts) {
      context.publishEvent(event);
    }
  }

  public void publishEvent(Object event){
    publishEvent(null, event);
  }


  private void notifyAppCacheFree() {
    publishEvent(new AppCacheFreeEvent(applicationContext));
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
    if(scheduled ==null) {
      CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("pm4schedule");
      scheduled = Executors.newScheduledThreadPool(2, threadFactory);
    }
    addPluginStateListener(new Pf4bootPluginStateListener(platformContext));

    List<Pf4bootPluginSupport> pluginSupports = getPluginSupports(true);
    pluginSupports.forEach(pluginSupport -> {
      pluginSupport.initiatePluginManager(this);
    });
    LOG.info("PF4J version {} in '{}' mode", getVersion(), getRuntimeMode());

  }

  public ScheduledExecutorService getScheduled() {
    return scheduled;
  }

  /**
   * This method load, start plugins and inject extensions in Spring
   */
  @PostConstruct
  public void init() {
    List<Pf4bootPluginSupport> pluginSupports = getPluginSupports(true);
    pluginSupports.forEach(pluginSupport -> {
      pluginSupport.initiatedPluginManager(this);
    });
    loadPlugins();

  }

  public void close(){
    if(scheduled !=null) {
      scheduled.shutdown();
      scheduled = null;
    }
    stopPlugins();

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
    LOG.debug("Use '{}' to find plugins descriptors", pluginDescriptorFinder);
    LOG.info("Finding plugin descriptor for plugin '{}'", pluginPath);
    PluginDescriptor pluginDescriptor = pluginDescriptorFinder.find(pluginPath);
    validatePluginDescriptor(pluginDescriptor);

    // Check there are no loaded plugins with the retrieved id
    pluginId = pluginDescriptor.getPluginId();
    if (plugins.containsKey(pluginId)) {
      PluginWrapper loadedPlugin = getPlugin(pluginId);
      LOG.warn("duplicate plugin found : {}, {} and {}",pluginId,loadedPlugin.getDescriptor().getVersion(), pluginDescriptor.getVersion());
      if(versionManager.compareVersions(loadedPlugin.getDescriptor().getVersion(),pluginDescriptor.getVersion())<0){
        unloadPlugin(pluginId);
      }else{
        return loadedPlugin;
      }
    }

    LOG.debug("Found descriptor {}", pluginDescriptor);
    String pluginClassName = pluginDescriptor.getPluginClass();
    LOG.debug("Class '{}' for plugin '{}'",  pluginClassName, pluginPath);

    // load plugin
    LOG.debug("Loading plugin '{}'", pluginPath);
    ClassLoader pluginClassLoader;
    try {
      pluginClassLoader = getPluginLoader().loadPlugin(pluginPath, pluginDescriptor);
    } catch (Exception e) {
      throw new PluginRuntimeException(e);
    }
    LOG.debug("Loaded plugin '{}' with class loader '{}'", pluginPath, pluginClassLoader);

    // create the plugin wrapper
    LOG.debug("Creating wrapper for plugin '{}'", pluginPath);
    Pf4bootPluginWrapper pluginWrapper = new Pf4bootPluginWrapper(this, pluginDescriptor, pluginPath, pluginClassLoader);
    pluginWrapper.setPluginFactory(getPluginFactory());

    // test for disabled plugin
    if (isPluginDisabled(pluginDescriptor.getPluginId())) {
      LOG.info("Plugin '{}' is disabled", pluginPath);
      pluginWrapper.setPluginState(PluginState.DISABLED);
    }

    // validate the plugin
    if (!isPluginValid(pluginWrapper)) {
      LOG.warn("Plugin '{}' is invalid and it will be disabled", pluginPath);
      pluginWrapper.setPluginState(PluginState.DISABLED);
    }

    LOG.debug("Created wrapper '{}' for plugin '{}'", pluginWrapper, pluginPath);

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
  public synchronized void setApplicationStarted(boolean mainApplicationStarted) {
    this.mainApplicationStarted = mainApplicationStarted;
    if(mainApplicationStarted){
//      scheduled.scheduleAtFixedRate(() -> {
//        doStartPlugins(false);
//      }, 30, 30, TimeUnit.SECONDS);
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
  public ConfigurableApplicationContext getApplicationContext() {
    return applicationContext;
  }

  @Override
  public boolean isApplicationStarted() {
    return mainApplicationStarted;
  }



  public PluginError getPluginErrors(String pluginId) {
    return pluginErrors.get(pluginId);
  }

//  @Override
//  public Pf4bootEventBus getPf4bootEventBus() {
//    return eventBus;
//  }
//
//  @Override
//  public void post(Object event) {
//    eventBus.post(event);
//  }



  //*************************************************************************
  // Plugin State Manipulation
  //*************************************************************************

  protected synchronized void doStartPlugins(boolean startStoppedPlugin) {
    pluginErrors.clear();
    int failedCount = 0;
    long ts = System.currentTimeMillis();
    List<PluginWrapper> wrappers = resolvedPlugins.stream().filter(p -> p.getPluginState().isResolved()
        || p.getPluginState().isFailed()
        || (p.getPluginState().isStopped() && startStoppedPlugin))
      .collect(Collectors.toList());
    for (PluginWrapper pluginWrapper : wrappers) {
      PluginState pluginState = pluginWrapper.getPluginState();
      if ((PluginState.RESOLVED == pluginState) || (PluginState.FAILED == pluginState)
        || (PluginState.STOPPED == pluginState && startStoppedPlugin)) {
        try {
          doStartPlugin(pluginWrapper);
        } catch (Exception e) {
          LOG.warn("Can't start plugin " + pluginWrapper.getPluginId(), e);
          pluginErrors.put(pluginWrapper.getPluginId(), PluginError.of(
            pluginWrapper.getPluginId(), e.getMessage(), e.toString()));
        }
        if (!pluginWrapper.getPluginState().isStarted()) {
          failedCount += 1;
        }
      }
    }
    LOG.info("[PF4BOOT] {} plugins are started in {}ms. {} failed", wrappers.size(),
      System.currentTimeMillis() - ts, failedCount);
  }

  private synchronized void doStopPlugins() {
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
          LOG.warn("stop plugin {} error", pluginWrapper.getPluginId(), e);
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
    for (Consumer<Pf4bootPlugin> releaseHook : pf4bootPlugin.getReleaseHooks()) {
      try{
        releaseHook.accept(pf4bootPlugin);
      } catch (Exception e) {
        LOG.warn("releaseHook {} failed", releaseHook, e);
      }
    }

  }

  @Override
  public void registerBeanToPlatformContext(String group, String beanName, Object bean) {
    registerBeanToContext(group, SharingScope.PLATFORM, beanName, bean);
  }

  @Override
  public void unregisterBeanFromPlatformContext(String group, String beanName) {
    unregisterBeanFromContext(group, SharingScope.PLATFORM, beanName);
  }

  @Override
  public void registerBeanToApplicationContext(String beanName, Object bean) {
    registerBeanToContext("", SharingScope.APPLICATION, beanName, bean);
  }

  @Override
  public void registerBeanToRootContext(String beanName, Object bean) {
    registerBeanToContext("", SharingScope.ROOT, beanName, bean);
  }

  @Override
  public void unregisterBeanFromApplicationContext(String beanName) {
    unregisterBeanFromContext("", SharingScope.APPLICATION, beanName);
  }

  @Override
  public void unregisterBeanFromRootContext(String beanName) {
    unregisterBeanFromContext("", SharingScope.ROOT, beanName);
  }

  private void registerBeanToContext(String group, SharingScope scope, String beanName, Object bean) {
    Assert.notNull(bean, "bean must not be null");
    beanName = Strings.isNullOrEmpty(beanName) ? bean.getClass().getName() : beanName;
    ConfigurableApplicationContext context = getContext(group, scope);
    context.getBeanFactory().registerSingleton(beanName, bean);
    LOG.info("[PF4BOOT] register bean {} [{}] to {} context [{}]", bean, beanName, scope, context.getId());
    BeanRegisterEvent registerBeanEvent = new BeanRegisterEvent(scope, context, beanName, bean);
    publishEvent(registerBeanEvent);
  }

  private ConfigurableApplicationContext getContext(String group, SharingScope scope) {
    ConfigurableApplicationContext context = null;
    if(SharingScope.APPLICATION.equals(scope)) {
      context= this.applicationContext;
    }else if(SharingScope.ROOT.equals(scope)) {
      context= this.rootContext;
    }else{
      context = this.getPlatformContext(group);
    }
    return context;
  }

  private void unregisterBeanFromContext(String group, SharingScope scope, String beanName) {
    Assert.notNull(beanName, "bean must not be null");
    ConfigurableApplicationContext context = getContext(group, scope);
    Object bean = context.getBean(beanName);
    ((AbstractAutowireCapableBeanFactory) context.getBeanFactory())
        .destroySingleton(beanName);
    BeanUnregisterEvent unRegisterBeanEvent = new BeanUnregisterEvent(scope, context, beanName, bean);
    publishEvent(unRegisterBeanEvent);
  }



  @Override
  public void startPlugins() {
    doStartPlugins(true);
    notifyAppCacheFree();
  }

  @Override
  public PluginState startPlugin(String pluginId) {
    checkPluginId(pluginId);
    Pf4bootPluginWrapper pluginWrapper = (Pf4bootPluginWrapper)getPlugin(pluginId);
    try{
      doStartPlugin(pluginWrapper);
      pluginWrapper.getStartFailed().set(0);
    } catch (Throwable e) {
      int startFailed = pluginWrapper.getStartFailed().incrementAndGet();
      LOG.warn("Plugin {} is failed to start, startFailed={}", pluginWrapper.getPluginId(), startFailed, e);

    }

    return pluginWrapper.getPluginState();
  }

  protected void doStartPlugin(PluginWrapper pluginWrapper) {
    PluginDescriptor pluginDescriptor = pluginWrapper.getDescriptor();
    PluginState pluginState = pluginWrapper.getPluginState();
    if (pluginState.isStarted()) {
      LOG.debug("Already started plugin '{}'", getPluginLabel(pluginDescriptor));
      return;
    }

    if (!resolvedPlugins.contains(pluginWrapper)) {
      LOG.warn("Cannot start an unresolved plugin '{}'", getPluginLabel(pluginDescriptor));
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
      if (!dependency.isOptional()){
        PluginWrapper wrapper = plugins.get(dependency.getPluginId());
        if(wrapper == null || !wrapper.getPluginState().isStarted()){
          LOG.info("Plugin {} is not started. stop start {}", dependency.getPluginId(), pluginDescriptor.getPluginId());
          return;
        }
      }
    }
    LOG.info("Start plugin '{}'", getPluginLabel(pluginDescriptor));

    Pf4bootPlugin plugin = (Pf4bootPlugin)pluginWrapper.getPlugin();
    ClassLoader oldClassLoader = replaceClassLoader(plugin.getWrapper().getPluginClassLoader());
    try {
      //初始化插件前置处理
      preHandlePlugin(p -> p.initiatePlugin(plugin));
      String group = plugin.getGroup();
      ConfigurableApplicationContext platformContext = this.getPlatformContext(group);
      platformContext.getBeanFactory().autowireBean(plugin);
      plugin.initiate();

      //初始化插件后置处理
      lastHandlePlugin(p -> p.initiatedPlugin(plugin));
      ConfigurableApplicationContext pluginContext = plugin.createPluginContext(platformContext);
      pluginContext.refresh();
      publishEvent(pluginContext, new PreStartPluginEvent(plugin));

      ApplicationContextProvider.registerApplicationContext(pluginContext);

      //插件启动前置处理
      preHandlePlugin(p -> p.startPlugin(plugin));
      publishEvent(pluginContext, new StartingPluginEvent(plugin));

      plugin.start();
      //插件启动后置处理
      lastHandlePlugin(p -> p.startedPlugin(plugin));
      publishEvent(pluginContext, new StartedPluginEvent(plugin));
    }finally {
      replaceClassLoader(oldClassLoader);
    }
    startedPlugins.add(pluginWrapper);
    pluginWrapper.setPluginState(PluginState.STARTED);
    firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, PluginState.STARTED));
    notifyAppCacheFree();
  }


  /**
   * 替换使用插件自己的类加载器, 并返回原来的类加载器
   */
  private ClassLoader replaceClassLoader(ClassLoader classLoader) {
    ClassLoader oldClassLoader = null;
    MetadataReaderFactory metadataReaderFactory = getApplicationContext().getBean(MetadataReaderFactory.class);
    if(metadataReaderFactory instanceof SimpleMetadataReaderFactory){
      SimpleMetadataReaderFactory factory = (SimpleMetadataReaderFactory) metadataReaderFactory;
      if(factory.getResourceLoader() instanceof DefaultResourceLoader){
        DefaultResourceLoader resourceLoader = (DefaultResourceLoader) factory.getResourceLoader();
        oldClassLoader = resourceLoader.getClassLoader();
        resourceLoader.setClassLoader(classLoader);
      }
    }
    return oldClassLoader;
  }

  @Override
  public void stopPlugins() {
    doStopPlugins();
    notifyAppCacheFree();
  }

  @Override
  public PluginState stopPlugin(String pluginId) {
    PluginState state = stopPlugin(pluginId, true);

    notifyAppCacheFree();
    return state;
  }

  protected PluginState stopPlugin(String pluginId, boolean stopDependents) {
    checkPluginId(pluginId);

    // test for started plugin
    if (!checkPluginState(pluginId, PluginState.STARTED)) {
      // do nothing
      LOG.debug("Plugin '{}' is not started, nothing to stop", getPluginLabel(pluginId));
      return getPlugin(pluginId).getPluginState();
    }

    if (stopDependents) {
      List<String> dependents = dependencyResolver.getDependents(pluginId);
      while (!dependents.isEmpty()) {
        String dependent = dependents.remove(0);
        stopPlugin(dependent, true);
        //dependents.addAll(0, dependencyResolver.getDependents(dependent));
      }
    }

    LOG.info("Stop plugin '{}'", getPluginLabel(pluginId));
    PluginWrapper pluginWrapper = getPlugin(pluginId);

    Pf4bootPlugin plugin = (Pf4bootPlugin)pluginWrapper.getPlugin();
    ConfigurableApplicationContext pluginContext = plugin.getPluginContext();
    publishEvent(pluginContext, new PreStopPluginEvent(plugin));
    //插件停止前置处理
    preHandlePlugin(p->p.stopPlugin(plugin));
    publishEvent(pluginContext, new StoppingPluginEvent(plugin));
    plugin.stop();

    //插件停止后置处理
    lastHandlePlugin(p->p.stoppedPlugin(plugin));
    publishEvent(pluginContext, new StoppedPluginEvent(plugin));
    firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, PluginState.STOPPED));
    pluginWrapper.setPluginState(PluginState.STOPPED);
    getStartedPlugins().remove(pluginWrapper);

    releaseResource(plugin);
    ApplicationContextProvider.unregisterApplicationContext(pluginContext);
    pluginContext.close();
    plugin.closed();



    return PluginState.STOPPED;
  }

  private List<Pf4bootPluginSupport> getPluginSupports(boolean reversed) {
    Comparator<Pf4bootPluginSupport> comparator = Comparator.comparingInt(Pf4bootPluginSupport::getPriority);
    if(reversed) {
      comparator = comparator.reversed();
    }
    return pluginSupportProvider.stream()
        .sorted(comparator)
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
    notifyAppCacheFree();
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
      notifyAppCacheFree();
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
    notifyAppCacheFree();
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

      LOG.info("Unload plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()));
    } catch (Exception e) {
      LOG.error("Cannot stop plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()), e);
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
      LOG.error("Failed to stop plugin '{}' on delete", pluginId);
      return false;
    }

    // get an instance of plugin before the plugin is unloaded
    // for reason see https://github.com/pf4j/pf4j/issues/309
    Plugin plugin = pluginWrapper.getPlugin();

    if (!unloadPlugin(pluginId)) {
      LOG.error("Failed to unload plugin '{}' on delete", pluginId);
      return false;
    }
    if(plugin instanceof Pf4bootPlugin) {
      Pf4bootPlugin pf4bootPlugin = (Pf4bootPlugin) plugin;
      preHandlePlugin(p -> p.deletePlugin(pf4bootPlugin));
      pf4bootPlugin.delete();
      lastHandlePlugin(p -> p.deletedPlugin(pf4bootPlugin));
    }else {
      // notify the plugin as it's deleted
      plugin.delete();
    }

    return pluginRepository.deletePluginPath(pluginWrapper.getPluginPath());
  }

  private void preHandlePlugin(Consumer<Pf4bootPluginSupport> consumer) {
    List<Pf4bootPluginSupport> pluginSupportList = getPluginSupports(false);
    pluginSupportList.forEach(consumer);
  }

  private void lastHandlePlugin(Consumer<Pf4bootPluginSupport> consumer) {
    List<Pf4bootPluginSupport> pluginSupportList = getPluginSupports(true);
    pluginSupportList.forEach(consumer);
  }
}
