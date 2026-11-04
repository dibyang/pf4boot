package net.xdob.pf4boot;

import com.google.common.base.Strings;
import net.xdob.pf4boot.exception.DependencyNotStartedException;
import net.xdob.pf4boot.internal.*;
import net.xdob.pf4boot.loader.JarPf4bootPluginLoader;
import net.xdob.pf4boot.loader.Pf4bootPluginLoader;
import net.xdob.pf4boot.loader.ZipPf4bootPluginLoader;
import net.xdob.pf4boot.modal.PluginError;
import net.xdob.pf4boot.modal.DynamicBeanConflictPolicy;
import net.xdob.pf4boot.modal.SharingScope;
import net.xdob.pf4boot.spring.boot.*;
import net.xdob.pf4boot.util.AutoCloseableLock;
import net.xdob.pf4boot.util.PluginCleanup;
import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import org.pf4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
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
	public static final int MAX_FAILED_NUM = 6;

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

  private final Pf4bootProperties properties;

  public static final String PLUGINS_DIR_CONFIG_PROPERTY_NAME = "pf4j.pluginsConfigDir";


	private final ReentrantLock stateLock = new ReentrantLock();
	private final AtomicBoolean starting = new AtomicBoolean(false);
  private final AtomicBoolean stopping = new AtomicBoolean(false);
  private boolean shutdownStarted;

  private final Pf4bootPluginSupport pluginSupport;
  private ScheduledExecutorService scheduled;
  private ScheduledFuture<?> autoStartFuture;
	private final ShareBeanMgr shareBeanMgr;
  private final List<PluginPackageVerifier> pluginPackageVerifiers;
  private final Map<String, PluginCleanup> pendingCleanup = new HashMap<>();

  public Pf4bootPluginManagerImpl(ApplicationContext applicationContext, Pf4bootProperties properties,
																	Pf4bootPluginSupport pluginSupport, ShareBeanMgr shareBeanMgr, Path... pluginsRoots) {
    this(applicationContext, properties, pluginSupport, shareBeanMgr, Collections.emptyList(), pluginsRoots);
  }

  public Pf4bootPluginManagerImpl(ApplicationContext applicationContext, Pf4bootProperties properties,
																	Pf4bootPluginSupport pluginSupport, ShareBeanMgr shareBeanMgr,
                                  Collection<PluginPackageVerifier> pluginPackageVerifiers, Path... pluginsRoots) {
    super(pluginsRoots);
    this.applicationContext = (ConfigurableApplicationContext)applicationContext;

    this.properties = properties;
    this.pluginSupport = pluginSupport;
		this.shareBeanMgr = shareBeanMgr;
    this.pluginPackageVerifiers = createPluginPackageVerifiers(properties, pluginPackageVerifiers);
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

  private List<PluginPackageVerifier> createPluginPackageVerifiers(
      Pf4bootProperties properties,
      Collection<PluginPackageVerifier> customVerifiers) {
    List<PluginPackageVerifier> verifiers = new ArrayList<>();
    verifiers.add(new DefaultPluginPackageVerifier(properties));
    verifiers.add(new DefaultPluginPackageTrustVerifier(properties));
    if (customVerifiers != null) {
      verifiers.addAll(customVerifiers);
    }
    return Collections.unmodifiableList(verifiers);
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
    // 排空中的在途任务仍可查询已有上下文，不能让只读查询等待生命周期锁。
    ConfigurableApplicationContext existing = platformContexts.get(group);
    if (existing != null) { return existing; }
    try (AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      existing = platformContexts.get(group);
      if (existing != null) { return existing; }
      checkOpen();
      return platformContexts.computeIfAbsent(group, key -> {
        ConfigurableApplicationContext context = new AnnotationConfigApplicationContext();
        context.setId("platform_" + key);
        context.setParent(platformContext);
        context.refresh();
        context.getBeanFactory().setParentBeanFactory(platformContext.getBeanFactory());
        LOG.info("create PlatformContext {} for {}", context.getId(), group);
        return context;
      });
    }
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
    if (rootContext != null) { publishEvent(new AppCacheFreeEvent(applicationContext)); }
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
  public Path getPluginCacheDir(){
    Path cacheDir = getPluginsRoot().resolveSibling("plugin-cache");
    if(!cacheDir.toFile().exists()){
      cacheDir.toFile().mkdirs();
    }
    return cacheDir;
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
		this.setResolveRecoveryStrategy(ResolveRecoveryStrategy.IGNORE_PLUGIN_AND_CONTINUE);
    if(scheduled ==null) {
      CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("pm4schedule");
      scheduled = Executors.newScheduledThreadPool(1, threadFactory);
    }
    addPluginStateListener(new Pf4bootPluginStateListener(platformContext));


		shareBeanMgr.initiatePluginManager( this);
    LOG.info("PF4J version {} in '{}' mode", getVersion(), getRuntimeMode());

  }


  public ScheduledExecutorService getScheduled() {
    return scheduled;
  }


	@Override
	public void releasePlugin(Pf4bootPlugin plugin) {
    PluginCleanup cleanup = new PluginCleanup();
    cleanup.run("shared resources", () -> shareBeanMgr.stopPlugin(plugin));
    cleanup.run("metadata caches", this::clearMetadataCache);
    cleanup.throwIfFailed();
  }

  private void clearMetadataCache() {
    clearMetadataCache(rootContext);
    clearMetadataCache(platformContext);
    clearMetadataCache(applicationContext);
    for (ConfigurableApplicationContext context : platformContexts.values()) {
      clearMetadataCache(context);
    }
    for (PluginWrapper pluginWrapper : this.startedPlugins) {
      Pf4bootPlugin plugin1 = (Pf4bootPlugin)pluginWrapper.getPlugin();
      ConfigurableApplicationContext context = plugin1.getPluginContext();
      if (context != null) {
        clearMetadataCache(context);
      }
    }
  }

  private void clearMetadataCache(ConfigurableApplicationContext context) {
    DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
    beanFactory.clearMetadataCache();
  }

  /**
   * This method load, start plugins and inject extensions in Spring
   */
  @PostConstruct
  public void init() {
    this.registerBeanToRootContext("pluginManager", this);

		shareBeanMgr.initiatedPluginManager(this);
    File cacheDir = getPluginCacheDir().toFile();
    File[] files = cacheDir.listFiles();
    if (files != null) {
      for (File file : files) {
        deleteDir(file);
      }
    }
    loadPlugins();

  }

  public static void deleteDir(File dir) {
    if (dir.isDirectory()) {
      for (File f : dir.listFiles()) {
        deleteDir(f);
      }
    }
    dir.delete();
  }


  @PreDestroy
  public void close(){
    try (AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      if (rootContext == null) { return; }
      if (starting.get()) { throw new PluginRuntimeException("Cannot close plugin manager from an activation callback"); }
      shutdownStarted = true;
      mainApplicationStarted = false;
      cancelAutoStartPlugins();
      if (scheduled != null) {
        scheduled.shutdownNow();
        scheduled = null;
      }
      stopPlugins();
      List<String> remaining = new ArrayList<>(pendingCleanup.keySet());
      for (PluginWrapper wrapper : getPlugins()) {
        if (wrapper.getPluginState().isStarted()
            || (wrapper.getPluginState() == PluginState.FAILED
                && ((Pf4bootPlugin) wrapper.getPlugin()).getPluginContext() != null)) {
          if (!remaining.contains(wrapper.getPluginId())) { remaining.add(wrapper.getPluginId()); }
        }
      }
      if (!remaining.isEmpty()) {
        throw new PluginRuntimeException("Cannot close plugin manager before plugin cleanup succeeds: %s", remaining);
      }
      closePlatformContexts();
      closeContext(platformContext);
      closeContext(rootContext);
      rootContext = null;
    }

  }

  /** 关闭开始后只允许清理重试，不允许通过回调或并发调用重新激活插件。 */
  private void checkOpen() {
    if (shutdownStarted) { throw new PluginRuntimeException("Plugin manager is closing or closed"); }
  }

  private void closePlatformContexts() {
    for (ConfigurableApplicationContext context : platformContexts.values()) {
      closeContext(context);
    }
    platformContexts.clear();
  }

  private void closeContext(ConfigurableApplicationContext context) {
    if (context != null && context.isActive()) {
      try {
        context.close();
      } catch (Exception e) {
        LOG.warn("close context {} failed", context.getId(), e);
      }
    }
  }

  @Override
  public PluginDescriptorFinder getPluginDescriptorFinder() {
    return super.getPluginDescriptorFinder();
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
    verifyPluginPackage(pluginPath, pluginDescriptor);
    verifyPluginCompatibility(pluginPath, pluginDescriptor);

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

  private void verifyPluginPackage(Path pluginPath, PluginDescriptor pluginDescriptor) {
    for (PluginPackageVerifier verifier : pluginPackageVerifiers) {
      PluginPackageVerificationResult result = verifier.verify(pluginPath, pluginDescriptor);
      if (result == null) {
        throw new PluginRuntimeException("Plugin package verifier returned null: " + verifier.getClass().getName());
      }
      if (!result.isValid()) {
        throw new PluginRuntimeException(result.getMessage());
      }
      if (result.isWarning()) {
        LOG.warn("[PF4BOOT] {}", result.getMessage());
      }
    }
  }

  private void verifyPluginCompatibility(Path pluginPath, PluginDescriptor pluginDescriptor) {
    PluginPackageVerificationMode mode = properties.getPluginCompatibilityVerificationMode();
    if (mode == null || PluginPackageVerificationMode.DISABLED.equals(mode)) {
      return;
    }
    String requires = pluginDescriptor.getRequires();
    if (Strings.isNullOrEmpty(requires)) {
      return;
    }
    if (versionManager.checkVersionConstraint(getSystemVersion(), requires)) {
      return;
    }
    String message = String.format(
        "Plugin '%s' requires system version '%s', current system version is '%s': %s",
        pluginDescriptor.getPluginId(), requires, getSystemVersion(), pluginPath);
    if (PluginPackageVerificationMode.ENFORCE.equals(mode)) {
      throw new PluginRuntimeException(message);
    }
    LOG.warn("[PF4BOOT] {}", message);
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
  public void setApplicationStarted(boolean mainApplicationStarted) {
    try (AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      if (mainApplicationStarted) { checkOpen(); }
      this.mainApplicationStarted = mainApplicationStarted;
      if (mainApplicationStarted) {
        scheduleAutoStartPlugins();
      } else {
        cancelAutoStartPlugins();
      }
    }
  }

  private void scheduleAutoStartPlugins() {
    if (scheduled == null || scheduled.isShutdown()) {
      return;
    }
    if (autoStartFuture == null || autoStartFuture.isCancelled() || autoStartFuture.isDone()) {
      autoStartFuture = scheduled.scheduleWithFixedDelay(() -> {
        try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
          if (!shutdownStarted && mainApplicationStarted) { doStartPlugins(true); }
        }
      }, 10, 20, TimeUnit.SECONDS);
    }
  }

  private void cancelAutoStartPlugins() {
    if (autoStartFuture != null) {
      autoStartFuture.cancel(false);
      autoStartFuture = null;
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
    Throwable e = getPlugin(pluginId).getFailedException();
    return e!=null?PluginError.of(pluginId, e.getMessage(), e.toString()):null;
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

  /**
   *
   * @param autoStartPlugin 此次是否是自动启动
   */
  protected void doStartPlugins(boolean autoStartPlugin) {
    int totalCount = 0;
		int failedCount = 0;
    long ts = System.currentTimeMillis();
    List<Pf4bootPluginWrapper> wrappers = resolvedPlugins.stream()
				.map(p -> (Pf4bootPluginWrapper)p)
      	.collect(Collectors.toList());
    if(!wrappers.isEmpty()){
      for (Pf4bootPluginWrapper pluginWrapper : wrappers) {
				if(stopping.get()){
					break;
				}
				if(!needStart(autoStartPlugin, pluginWrapper)){
					continue;
				}
				PluginState pluginState = pluginWrapper.getPluginState();
				if(PluginState.DISABLED == pluginState){
					continue;
				}

				totalCount += 1;
				if (canStart(pluginWrapper, autoStartPlugin)) {
					if(stopping.get()){
						break;
					}
					try {
						doStartPlugin(pluginWrapper, autoStartPlugin);
					} catch (Throwable e) {
						LOG.warn("Can't start plugin {}", pluginWrapper.getPluginId(), e);
						pluginWrapper.setPluginState(PluginState.FAILED);
						pluginWrapper.setFailedException(e);
					}
					if (!pluginWrapper.getPluginState().isStarted()) {
						failedCount += 1;
					}
				}
				if(stopping.get()){
					break;
				}
      }
			if(totalCount>0) {
				LOG.info("[PF4BOOT] {} plugins are started in {}ms. {} failed", totalCount,
						System.currentTimeMillis() - ts, failedCount);
			}
    }
  }

	private static boolean canStart(Pf4bootPluginWrapper pluginWrapper, boolean autoStartPlugin) {
		PluginState pluginState = pluginWrapper.getPluginState();
		if(!autoStartPlugin){
			return PluginState.RESOLVED == pluginState
					|| PluginState.FAILED == pluginState
					|| PluginState.STOPPED == pluginState;
		}else{
			return (PluginState.RESOLVED == pluginState)
					|| (PluginState.FAILED == pluginState && pluginWrapper.getStartFailed().get() < MAX_FAILED_NUM)
			    || (PluginState.STOPPED == pluginState && !pluginWrapper.getStopped().get());
		}
	}

	private boolean needStart(boolean autoStartPlugin, PluginWrapper p) {
    if(!autoStartPlugin){
      return p.getPluginState().isResolved()
          ||p.getPluginState().isFailed()
          ||p.getPluginState().isStopped();
    }else {
      Set<Pf4bootPluginWrapper> requiredManualPlugins = ((Pf4bootPluginWrapper)p).findRequiredManualPlugins();
      return requiredManualPlugins.isEmpty()
          &&(p.getPluginState().isResolved()
          || p.getPluginState().isFailed());
    }
  }


  private void doStopPlugins() {
    // stop started plugins in reverse order, but do not mutate startedPlugins ordering directly
    List<PluginWrapper> pluginsToStop = new ArrayList<>(startedPlugins);
    Collections.reverse(pluginsToStop);
    for (PluginWrapper wrapper : getPlugins()) {
      if (!pluginsToStop.contains(wrapper) && (pendingCleanup.containsKey(wrapper.getPluginId())
          || (wrapper.getPluginState() == PluginState.FAILED
              && ((Pf4bootPlugin) wrapper.getPlugin()).getPluginContext() != null))) {
        pluginsToStop.add(wrapper);
      }
    }
    for (PluginWrapper pluginWrapper : pluginsToStop) {
      PluginState pluginState = pluginWrapper.getPluginState();
      if (PluginState.STOPPED != pluginState) {
        try {
          stopPlugin(pluginWrapper.getPluginId());
        } catch (PluginRuntimeException e) {
          LOG.warn("stop plugin {} error", pluginWrapper.getPluginId(), e);
        }
      }
    }
  }

  /**
   * Release plugin holding release on stop.
   */
  public void releaseResource(Pf4bootPlugin pf4bootPlugin) {
    PluginCleanup cleanup = new PluginCleanup();
    for (Consumer<Pf4bootPlugin> releaseHook : new ArrayList<>(pf4bootPlugin.getReleaseHooks())) {
      cleanup.run("release hook", () -> {
        releaseHook.accept(pf4bootPlugin);
        pf4bootPlugin.getReleaseHooks().remove(releaseHook);
      });
    }
    cleanup.throwIfFailed();
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
    prepareDynamicBeanRegistration(context, scope, group, beanName);
    DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
    registerDynamicBeanDefinition(beanFactory, beanName, bean);
		beanFactory.registerSingleton(beanName, bean);
		LOG.info("[PF4BOOT] register bean {} [{}] to {} context [{}]", bean, beanName, scope, context.getId());
    BeanRegisterEvent registerBeanEvent = new BeanRegisterEvent(scope, context, beanName, bean);
    publishEvent(registerBeanEvent);
  }

  private void registerDynamicBeanDefinition(
      DefaultListableBeanFactory beanFactory,
      String beanName,
      Object bean) {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(bean.getClass());
    beanDefinition.setInstanceSupplier(() -> bean);
    beanDefinition.setRole(BeanDefinition.ROLE_APPLICATION);
    beanFactory.registerBeanDefinition(beanName, beanDefinition);
  }

  private void prepareDynamicBeanRegistration(
      ConfigurableApplicationContext context,
      SharingScope scope,
      String group,
      String beanName) {
    DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
    boolean hasSingleton = beanFactory.containsSingleton(beanName);
    boolean hasBeanDefinition = beanFactory.containsBeanDefinition(beanName);
    if (!hasSingleton && !hasBeanDefinition) {
      return;
    }

    DynamicBeanConflictPolicy policy = properties.getDynamicBeanConflictPolicy();
    if (DynamicBeanConflictPolicy.REPLACE.equals(policy)) {
      Object existing = null;
      if (context.containsBean(beanName)) {
        try {
          existing = context.getBean(beanName);
        } catch (Exception ignored) {
        }
      }
      LOG.warn("[PF4BOOT] replace dynamic bean [{}] in {} context [{}], group [{}], existing={}",
          beanName, scope, context.getId(), group, existing);
      unregisterBeanFromContext(group, scope, beanName);
      return;
    }

    throw new IllegalStateException(String.format(
        "Dynamic bean name conflict: beanName [%s] already exists in %s context [%s], group [%s]",
        beanName, scope, context.getId(), group));
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
    Assert.notNull(beanName, "beanName must not be null");
    ConfigurableApplicationContext context = getContext(group, scope);
    DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
    Object bean = beanFactory.containsLocalBean(beanName) ? beanFactory.getBean(beanName) : null;

    if (beanFactory.containsSingleton(beanName)) {
      beanFactory.destroySingleton(beanName);
    }
    if (beanFactory.containsBeanDefinition(beanName)) {
      beanFactory.removeBeanDefinition(beanName);
    }
    removeMergedBeanDefinition(beanFactory, beanName);

    BeanUnregisterEvent unRegisterBeanEvent = new BeanUnregisterEvent(scope, context, beanName, bean);
    publishEvent(unRegisterBeanEvent);
  }

  /** Spring 5.3 的 stale 合并定义仍可持有旧类型和 supplier，只移除已注销的动态项。 */
  private void removeMergedBeanDefinition(DefaultListableBeanFactory beanFactory, String beanName) {
    Field field = ReflectionUtils.findField(AbstractBeanFactory.class, "mergedBeanDefinitions");
    Assert.state(field != null, "Unsupported Spring merged bean definition metadata");
    ReflectionUtils.makeAccessible(field);
    Map<?, ?> definitions = (Map<?, ?>) ReflectionUtils.getField(field, beanFactory);
    Assert.state(definitions != null, "Missing Spring merged bean definition metadata");
    definitions.remove(beanName);
  }

  private void removeBeanDefinition(String beanName) {
    try {
      DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory)this.rootContext.getBeanFactory();
      beanFactory.removeBeanDefinition(beanName);
      LOG.info("[PF4BOOT] remove bean definition {} from root", beanName);
    } catch (Exception ignore) {
    }
    try {
      DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory)this.applicationContext.getBeanFactory();
      beanFactory.removeBeanDefinition(beanName);
      LOG.info("[PF4BOOT] remove bean definition {} from app", beanName);
    } catch (Exception ignore) {
    }
  }


  @Override
  public void startPlugins() {
    try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkOpen();
      doStartPlugins(false);
      notifyAppCacheFree();
    }
  }

  @Override
  public PluginState startPlugin(String pluginId) {
    return startPlugin(pluginId, false);
  }

	protected PluginState startPlugin(String pluginId, boolean autoStartPlugin) {
    try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkOpen();
      checkPluginId(pluginId);
      Pf4bootPluginWrapper pluginWrapper = (Pf4bootPluginWrapper) getPlugin(pluginId);
      if(pluginWrapper!=null) {
        try {
          doStartPlugin(pluginWrapper, autoStartPlugin);
          pluginWrapper.getStartFailed().set(0);
        } catch (Throwable e) {
          int startFailed = pluginWrapper.getStartFailed().incrementAndGet();
          pluginWrapper.setFailedException(e);
          pluginWrapper.setPluginState(PluginState.FAILED);
          LOG.warn("Plugin {} is failed to start, startFailed={}", pluginWrapper.getPluginId(), startFailed, e);

        }
        return pluginWrapper.getPluginState();
      }else{
        throw new PluginNotFoundException(pluginId);
      }
    }
	}

  /**
   *
   * @param pluginWrapper 插件包装信息
   * @param autoStartPlugin 是否是自动启动
   */
  protected void doStartPlugin(PluginWrapper pluginWrapper, boolean autoStartPlugin) {

    long startTs = System.currentTimeMillis();
    PluginDescriptor pluginDescriptor = pluginWrapper.getDescriptor();
    PluginState pluginState = pluginWrapper.getPluginState();
    if (pluginState.isStarted()) {
      LOG.debug("Already started plugin '{}'", getPluginLabel(pluginDescriptor));
      return;
    }

    Pf4bootPlugin plugin = (Pf4bootPlugin) pluginWrapper.getPlugin();
    if (pendingCleanup.containsKey(pluginWrapper.getPluginId())
        || (pluginState == PluginState.FAILED && plugin.getPluginContext() != null)) {
      stopPlugin(pluginWrapper.getPluginId(), false);
      pluginState = pluginWrapper.getPluginState();
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

    if(autoStartPlugin){
      Pf4bootPluginWrapper wrapper = (Pf4bootPluginWrapper)pluginWrapper;
      Set<Pf4bootPluginWrapper> requiredManualPlugins = wrapper.findRequiredManualPlugins();
      if(!requiredManualPlugins.isEmpty()){
        LOG.info("Plugins {} is required manual intervention. stop start {}",
            requiredManualPlugins.stream()
                .map(PluginWrapper::getPluginId)
                .collect(Collectors.toList()), pluginDescriptor.getPluginId());
        return;
      }
    }



    ClassLoader oldClassLoader = replaceClassLoader(plugin.getWrapper().getPluginClassLoader());
    boolean alreadyStarting = starting.getAndSet(true);
		try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock, 10, TimeUnit.SECONDS)) {
			for (PluginDependency dependency : pluginDescriptor.getDependencies()) {
				// start dependency only if it marked as required (non-optional) or if it optional and loaded
				if (!dependency.isOptional() || plugins.containsKey(dependency.getPluginId())) {
					startPlugin(dependency.getPluginId(), autoStartPlugin);
				}
				if (!dependency.isOptional()){
					Pf4bootPluginWrapper wrapper = (Pf4bootPluginWrapper) plugins.get(dependency.getPluginId());
					if(wrapper == null || !wrapper.getPluginState().isStarted()){
						LOG.info("Plugin {} is not started. stop start {}", dependency.getPluginId(), pluginWrapper.getPluginId());
						pluginWrapper.setPluginState(PluginState.FAILED);
						pluginWrapper.setFailedException(new DependencyNotStartedException(dependency.getPluginId()));
						return;
					}
				}
			}
			LOG.info("Start plugin '{}'", getPluginLabel(pluginDescriptor));

			PluginState state = pluginWrapper.getPluginState();
			if (state.isStarted() || state.isDisabled()) {
				return;
			}
			pluginWrapper.setFailedException(null);
			((Pf4bootPluginWrapper)pluginWrapper).getStopped().set( false);
      //初始化插件前置处理
			pluginSupport.initiatePlugin(plugin);
      String group = plugin.getGroup();
      ConfigurableApplicationContext platformContext = this.getPlatformContext(group);

      plugin.initiate();

      //初始化插件后置处理
			pluginSupport.initiatedPlugin(plugin);
      ConfigurableApplicationContext pluginContext = plugin.createPluginContext(platformContext);

			pluginContext.refresh();


      publishEvent(pluginContext, new PreStartPluginEvent(plugin));
      ApplicationContextProvider.registerApplicationContext(pluginContext);

      pluginContext.getBeanFactory().autowireBean(plugin);

      //插件启动前置处理
			pluginSupport.startPlugin(plugin);
      publishEvent(pluginContext, new StartingPluginEvent(plugin));

      plugin.start();
			shareBeanMgr.startedPlugin(plugin);
      //插件启动后置处理
			pluginSupport.startedPlugin(plugin);
      pluginWrapper.setPluginState(PluginState.STARTED);
      ((Pf4bootPluginWrapper)pluginWrapper).getLastStartDurationMillis()
          .set(System.currentTimeMillis() - startTs);
			if (!startedPlugins.contains(pluginWrapper)) {
				startedPlugins.add(pluginWrapper);
			}
			((Pf4bootPluginWrapper)pluginWrapper).getStartFailed().set(0);
			firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, PluginState.STARTED));
      publishEvent(pluginContext, new StartedPluginEvent(plugin));
			notifyAppCacheFree();
    } catch (Throwable e) {
      pluginWrapper.setPluginState(PluginState.FAILED);
      // 排空可能失败；即使尚无上下文，也必须保留欠清理记录。null 表示计划尚未执行。
      pendingCleanup.putIfAbsent(pluginWrapper.getPluginId(), null);
      try (PluginDrainScope drain = drainPlugins(Collections.singletonList(pluginWrapper.getPluginId()))) {
        cleanupPlugin(plugin);
      } catch (Throwable cleanupFailure) {
        e.addSuppressed(cleanupFailure);
      }
      getStartedPlugins().remove(pluginWrapper);
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      if (e instanceof Error) {
        throw (Error) e;
      }
      throw new PluginRuntimeException(e, "Plugin start failed: %s", pluginWrapper.getPluginId());
		}finally {
      starting.set(alreadyStarting);
      replaceClassLoader(oldClassLoader);
    }
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
    try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      doStopPlugins();
      notifyAppCacheFree();
    }
  }

  @Override
  public PluginState stopPlugin(String pluginId) {
    PluginState state = stopPlugin(pluginId, true);
    notifyAppCacheFree();
    return state;
  }

  protected PluginState stopPlugin(String pluginId, boolean stopDependents) {
		return stopPlugin(pluginId, stopDependents, null);
  }

  /**
   *
   * @param pluginId 插件id
   * @param stopDependents 是否停止依赖于我的插件
   * @param culpritDependency  导致插件意外停止的依赖插件
   */
  protected PluginState stopPlugin(String pluginId, boolean stopDependents, String culpritDependency ) {
    try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkPluginId(pluginId);

      LinkedHashSet<String> affected = new LinkedHashSet<>();
      collectStopScope(pluginId, stopDependents, affected);
      try (PluginDrainScope drain = drainPlugins(affected)) {
        return doStopPlugin(pluginId, stopDependents, culpritDependency);
      }
    }
	}

  /** 在修改运行态之前排空整个依赖影响范围。 */
  private void collectStopScope(String pluginId, boolean dependents, Set<String> affected) {
    if (!affected.add(pluginId)) {
      return;
    }
    if (dependents) {
      for (String dependent : dependencyResolver.getDependents(pluginId)) {
        collectStopScope(dependent, true, affected);
      }
    }
  }

  private PluginDrainScope drainPlugins(Collection<String> pluginIds) {
    Set<PluginTrafficDrainer> drainers = new LinkedHashSet<>();
    if (shareBeanMgr instanceof PluginTrafficDrainer) {
      drainers.add((PluginTrafficDrainer) shareBeanMgr);
    }
    // 宿主销毁期间不创建新 Bean，也不调用已标记关闭的 ApplicationContext 查询入口。
    DefaultListableBeanFactory factory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
    for (String name : factory.getBeanNamesForType(PluginTrafficDrainer.class, false, false)) {
      Object drainer = applicationContext.isActive() ? factory.getBean(name) : factory.getSingleton(name);
      if (drainer instanceof PluginTrafficDrainer) {
        drainers.add((PluginTrafficDrainer) drainer);
      }
    }
    return PluginDrainScope.open(drainers, pluginIds, properties.getPluginDrainTimeoutMillis());
  }

  /** 逐项清理并只保留失败步骤供下次 stop/start 重试。 */
  private void cleanupPlugin(Pf4bootPlugin plugin) {
    String id = plugin.getPluginId();
    PluginCleanup previous = pendingCleanup.get(id);
    if (previous != null) {
      previous.retry();
      pendingCleanup.remove(id);
      return;
    }
    PluginCleanup cleanup = new PluginCleanup();
    pendingCleanup.put(id, cleanup);
    ConfigurableApplicationContext context = plugin.getPluginContext();
    cleanup.run("pre-stop event", () -> publishCleanupEvent(plugin, new PreStopPluginEvent(plugin)));
    cleanup.run("support stop", () -> pluginSupport.stopPlugin(plugin));
    cleanup.run("stopping event", () -> publishCleanupEvent(plugin, new StoppingPluginEvent(plugin)));
    cleanup.run("plugin stop", plugin::stop);
    cleanup.run("support stopped", () -> pluginSupport.stoppedPlugin(plugin));
    cleanup.run("stopped event", () -> publishCleanupEvent(plugin, new StoppedPluginEvent(plugin)));
    cleanup.run("release hooks", () -> releaseResource(plugin));
    cleanup.run("shared resources", () -> releasePlugin(plugin));
    cleanup.run("support release", () -> pluginSupport.releasePlugin(plugin));
    cleanup.run("unregister context", () -> ApplicationContextProvider.unregisterApplicationContext(context));
    cleanup.run("close context", plugin::closePluginContext);
    cleanup.run("clear plugin properties", plugin::clearAllProperties);
    cleanup.run("plugin closed", plugin::closed);
    cleanup.throwIfFailed();
    pendingCleanup.remove(id);
  }

  private void publishCleanupEvent(Pf4bootPlugin plugin, Object event) {
    ConfigurableApplicationContext context = plugin.getPluginContext();
    publishEvent(context != null && context.isActive() ? context : null, event);
  }

	protected PluginState doStopPlugin(String pluginId, boolean stopDependents, String culpritDependency) {
		//停止依赖于我的插件（是否是自动停止）
		if (stopDependents) {
			List<String> dependents = dependencyResolver.getDependents(pluginId);
			for (String dependent : dependents) {
				doStopPlugin(dependent, true, pluginId);
			}
		}
		try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock, 10, TimeUnit.SECONDS)){
			stopping.set( true);
			Pf4bootPluginWrapper pluginWrapper = (Pf4bootPluginWrapper)getPlugin(pluginId);
			PluginState pluginState = pluginWrapper.getPluginState();
			Pf4bootPlugin plugin = (Pf4bootPlugin) pluginWrapper.getPlugin();
			if(!pluginState.isStarted() && !pendingCleanup.containsKey(pluginId)
          && plugin.getPluginContext() == null){
				return pluginState;
			}
			LOG.info("Stop plugin '{}'", getPluginLabel(pluginId));
      if (culpritDependency == null) {
        pluginWrapper.getStopped().set(true);
      }
      try {
        cleanupPlugin(plugin);
      } catch (Throwable failure) {
        pluginWrapper.setPluginState(PluginState.FAILED);
        pluginWrapper.setFailedException(failure);
        getStartedPlugins().remove(pluginWrapper);
        throw new PluginRuntimeException(failure, "Plugin stop cleanup failed: %s", pluginId);
      }
			if(culpritDependency !=null&&!culpritDependency.isEmpty()){
				pluginWrapper.setPluginState(PluginState.FAILED);
				pluginWrapper.setFailedException(new DependencyNotStartedException(culpritDependency));
			}else{
				pluginWrapper.setPluginState(PluginState.STOPPED);
				pluginWrapper.setFailedException(null);
			}
			getStartedPlugins().remove(pluginWrapper);
			firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, pluginWrapper.getPluginState()));
			if(culpritDependency==null){
				((Pf4bootPluginWrapper)pluginWrapper).getStopped().set(true);
			}
			return PluginState.STOPPED;
		}finally {
			stopping.set( false);
		}
	}


  @Override
  public void restartPlugins() {
    try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkOpen();
      doStopPlugins();
      startPlugins();
    }
  }

  @Override
  public PluginState restartPlugin(String pluginId) {
    try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkOpen();
      PluginWrapper plugin = getPlugin(pluginId);
      if(plugin.getPluginState().equals(PluginState.STARTED)){
        this.stopPlugin(pluginId);
      }
      PluginState pluginState = this.startPlugin(pluginId);
      notifyAppCacheFree();
      return pluginState;
    }
  }

  @Override
  public void reloadPlugins(boolean restartStartedOnly) {
    try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkOpen();
      List<String> startedPluginIds = getStartedPlugins().stream()
          .map(PluginWrapper::getPluginId)
          .collect(Collectors.toList());
      doStopPlugins();
      List<String> pluginIds = getPlugins().stream()
          .map(PluginWrapper::getPluginId)
          .collect(Collectors.toList());
      pluginIds.forEach(this::unloadPlugin);
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
  }

  @Override
  public PluginState reloadPlugin(String pluginId) {
    try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkOpen();
      PluginWrapper plugin = getPlugin(pluginId);
      this.stopPlugin(pluginId);
      unloadPlugin(pluginId);
      loadPlugin(plugin.getPluginPath());
      PluginState state = this.startPlugin(pluginId);
      notifyAppCacheFree();
      return state;
    }
  }

  @Override
  public PluginState upgradePlugin(String pluginId, Path newPluginPath, Path rollbackPluginPath) {
    try(AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkOpen();
      checkPluginId(pluginId);
      Assert.notNull(newPluginPath, "newPluginPath must not be null");

      PluginWrapper currentPlugin = getPlugin(pluginId);
      boolean restartAfterUpgrade = currentPlugin != null && currentPlugin.getPluginState().isStarted();
      Path fallbackPath = rollbackPluginPath != null
          ? rollbackPluginPath
          : (currentPlugin == null ? null : currentPlugin.getPluginPath());

      try {
        if (currentPlugin != null) {
          this.stopPlugin(pluginId);
          unloadPlugin(pluginId);
        }
        String loadedPluginId = loadPlugin(newPluginPath);
        PluginState state = restartAfterUpgrade ? this.startPlugin(loadedPluginId) : getPlugin(loadedPluginId).getPluginState();
        notifyAppCacheFree();
        return state;
      } catch (Throwable upgradeFailure) {
        rollbackPlugin(pluginId, fallbackPath, restartAfterUpgrade, upgradeFailure);
        throw new PluginRuntimeException(upgradeFailure,
            "Upgrade plugin '%s' failed and rollback path '%s' was restored", pluginId, fallbackPath);
      }
    }
  }

  private void rollbackPlugin(
      String pluginId,
      Path rollbackPluginPath,
      boolean restartAfterRollback,
      Throwable upgradeFailure) {
    if (rollbackPluginPath == null) {
      throw new PluginRuntimeException(upgradeFailure,
          "Upgrade plugin '%s' failed and rollback path is not available", pluginId);
    }
    try {
      PluginWrapper partialPlugin = getPlugin(pluginId);
      if (partialPlugin != null) {
        unloadPlugin(pluginId);
      }
      String restoredPluginId = loadPlugin(rollbackPluginPath);
      if (restartAfterRollback) {
        startPlugin(restoredPluginId);
      }
      notifyAppCacheFree();
      LOG.warn("[PF4BOOT] rollback plugin {} from {}", pluginId, rollbackPluginPath);
    } catch (Throwable rollbackFailure) {
      throw new PluginRuntimeException(rollbackFailure,
          "Upgrade plugin '%s' failed and rollback from '%s' also failed", pluginId, rollbackPluginPath);
    }
  }
  


  @Override
  public String loadPlugin(Path pluginPath) {
    try (AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkOpen();
      boolean alreadyStarting = starting.getAndSet(true);
      try { return super.loadPlugin(pluginPath); }
      finally { starting.set(alreadyStarting); }
    }
  }

  @Override
  public void loadPlugins() {
    try (AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      checkOpen();
      boolean alreadyStarting = starting.getAndSet(true);
      try { super.loadPlugins(); }
      finally { starting.set(alreadyStarting); }
    }
  }

  @Override
  public void unloadPlugins() {
    super.unloadPlugins();
  }


  @Override
  protected boolean unloadPlugin(String pluginId, boolean unloadDependents, boolean resolveDependencies) {
    try (AutoCloseableLock lock = AutoCloseableLock.acquire(stateLock)) {
      if (!plugins.containsKey(pluginId)) {
        return false;
      }
      LinkedHashSet<String> affected = new LinkedHashSet<>();
      collectUnloadOrder(pluginId, unloadDependents, affected);
      try (PluginDrainScope drain = drainPlugins(affected)) {
        doStopPlugin(pluginId, unloadDependents, null);
        PluginCleanup cleanup = new PluginCleanup();
        List<PluginStateEvent> events = new ArrayList<>();
        cleanup.run("unload resources", () -> {
          for (String id : affected) {
            unloadStoppedPlugin(id, events);
          }
        });
        if (resolveDependencies) {
          cleanup.run("resolve dependencies", this::resolveDependencies);
        }
        for (PluginStateEvent event : events) {
          cleanup.run("unload notification " + event.getPlugin().getPluginId(), () -> firePluginStateEvent(event));
        }
        cleanup.throwIfFailed();
        return true;
      }
    }
  }

  /** PF4J 依赖图已排除环；后序去重保证菱形依赖中消费者也先于所有提供者卸载。 */
  private void collectUnloadOrder(String pluginId, boolean dependents, Set<String> order) {
    if (order.contains(pluginId)) {
      return;
    }
    if (dependents) {
      for (String dependent : dependencyResolver.getDependents(pluginId)) {
        collectUnloadOrder(dependent, true, order);
      }
    }
    order.add(pluginId);
  }

  /** 已排空并停止；关闭成功后提交记录并收集事件，由整个批次收尾后通知观察者。 */
  private void unloadStoppedPlugin(String pluginId, List<PluginStateEvent> events) {
    PluginWrapper pluginWrapper = getPlugin(pluginId);
    PluginState previousState = pluginWrapper.getPluginState();
    ClassLoader classLoader = getPluginClassLoaders().get(pluginId);
    if (classLoader instanceof Closeable) {
      try {
        ((Closeable) classLoader).close();
      } catch (Throwable failure) {
        pluginWrapper.setPluginState(PluginState.FAILED);
        pluginWrapper.setFailedException(failure);
        getResolvedPlugins().remove(pluginWrapper);
        throw new PluginRuntimeException(failure, "Cannot close classloader for plugin: %s", pluginId);
      }
    }
    LOG.info("Unload plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()));
    pluginWrapper.setPluginState(PluginState.UNLOADED);
    plugins.remove(pluginId);
    getResolvedPlugins().remove(pluginWrapper);
    getUnresolvedPlugins().remove(pluginWrapper);
    getPluginClassLoaders().remove(pluginId);
    events.add(new PluginStateEvent(this, pluginWrapper, previousState));
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
			pluginSupport.deletePlugin(pf4bootPlugin);
      pf4bootPlugin.delete();
			pluginSupport.deletedPlugin(pf4bootPlugin);
    }else {
      // notify the plugin as it's deleted
      plugin.delete();
    }

    return pluginRepository.deletePluginPath(pluginWrapper.getPluginPath());
  }

}
