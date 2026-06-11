package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.AutoExports;
import net.xdob.pf4boot.annotation.Export;
import net.xdob.pf4boot.annotation.ExportBeans;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.internal.SpringExtensionFactory;
import net.xdob.pf4boot.AutoExportMgr;
import net.xdob.pf4boot.deployment.DeploymentCheckResult;
import net.xdob.pf4boot.deployment.PluginCleanupVerifier;
import net.xdob.pf4boot.deployment.PluginHealthContext;
import net.xdob.pf4boot.deployment.PluginHealthVerifier;
import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import net.xdob.pf4boot.modal.SharingBean;
import net.xdob.pf4boot.modal.SharingBeans;
import net.xdob.pf4boot.modal.SharingScope;
import net.xdob.pf4boot.scheduling.DefaultScheduledMgr;
import net.xdob.pf4boot.scheduling.ScheduledMgr;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DefaultShareBeanMgr
 *
 * 负责插件共享 Bean、扩展 Bean、自动导出规则、定时任务的注册和注销。
 *
 * 关键设计：
 * 1. 插件启动时记录本插件实际注册过的 SharingBeans。
 * 2. 插件停止时按记录注销，不重新扫描插件 Context。
 * 3. 避免插件 Context 已经变化或 Bean 已销毁时，重新扫描导致注销错乱。
 */
public class DefaultShareBeanMgr
    implements ShareBeanMgr, PluginTrafficDrainer, PluginCleanupVerifier, PluginHealthVerifier {
  static final Logger logger = LoggerFactory.getLogger(DefaultShareBeanMgr.class);

  private final ScheduledMgr scheduledMgr = new DefaultScheduledMgr();
  private final AutoExportMgr autoExportMgr;

  /**
   * 记录每个插件实际注册到 root/platform/application 的共享 Bean。
   *
   * key: pluginId
   */
  private final Map<String, SharingBeans> pluginSharingBeans = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> pluginExtensionBeans = new ConcurrentHashMap<>();

  public DefaultShareBeanMgr(AutoExportMgr autoExportMgr) {
    this.autoExportMgr = autoExportMgr;
  }

  public int getRegisteredSharingBeanCount(String pluginId) {
    SharingBeans sharingBeans = pluginSharingBeans.get(pluginId);
    if (sharingBeans == null) {
      return 0;
    }
    return sharingBeans.getRootBeans().size()
        + sharingBeans.getPlatformBeans().size()
        + sharingBeans.getAppBeans().size();
  }

  public int getScheduledTaskCount(String pluginId) {
    if (scheduledMgr instanceof DefaultScheduledMgr) {
      return ((DefaultScheduledMgr) scheduledMgr).getScheduledTaskCount(pluginId);
    }
    return 0;
  }

  public int getRunningScheduledTaskCount(String pluginId) {
    if (scheduledMgr instanceof DefaultScheduledMgr) {
      return ((DefaultScheduledMgr) scheduledMgr).getRunningTaskCount(pluginId);
    }
    return 0;
  }

  public int getRegisteredExtensionBeanCount(String pluginId) {
    Set<String> beanNames = pluginExtensionBeans.get(pluginId);
    return beanNames == null ? 0 : beanNames.size();
  }

  @Override
  public void initiatePluginManager(Pf4bootPluginManager pluginManager) {
    ConfigurableApplicationContext applicationContext = pluginManager.getApplicationContext();

    /*
     * 主应用自身导出的共享 Bean 不属于任何插件，所以不放入 pluginSharingBeans。
     */
    registerShareServices(pluginManager, null, applicationContext);
  }

  private <A extends Annotation> List<A> getAnnotations(Class<?> primarySource, Class<A> annotationClazz) {
    List<A> annotations = new ArrayList<>();

    if (primarySource == null) {
      return annotations;
    }

    Optional.ofNullable(primarySource.getAnnotation(annotationClazz))
        .ifPresent(annotations::add);

    PluginStarter pluginStarter = primarySource.getAnnotation(PluginStarter.class);
    if (pluginStarter != null) {
      Class<?>[] starterClasses = pluginStarter.value();
      for (Class<?> starterClass : starterClasses) {
        Optional.ofNullable(starterClass.getAnnotation(annotationClazz))
            .ifPresent(annotations::add);
      }
    }

    return annotations;
  }

  @Override
  public void startedPlugin(Pf4bootPlugin pf4bootPlugin) {
    String pluginId = pf4bootPlugin.getWrapper().getPluginId();

    /*
     * 1. 先注册自动导出规则。
     *    这样本插件注册共享 Bean 时，也可以使用当前插件声明的 AutoExports。
     */
    registerAutoExports(pf4bootPlugin);

    /*
     * 2. 注册共享 Bean，并记录这次实际注册的内容。
     */
    SharingBeans sharingBeans = registerShareServices(pf4bootPlugin);
    pluginSharingBeans.put(pluginId, sharingBeans);

    /*
     * 3. 注册 PF4J Extension 到平台上下文。
     */
    registerExtensions(pf4bootPlugin);

    /*
     * 4. 最后启动定时任务。
     *    这样定时任务启动时，共享 Bean 和 Extension 已经准备好。
     */
    scheduledMgr.registerScheduledTasks(pf4bootPlugin);
  }

  private void registerAutoExports(Pf4bootPlugin pf4bootPlugin) {
    Class<?> primarySource = pf4bootPlugin.getClass();

    getAnnotations(primarySource, AutoExports.class)
        .forEach(autoExports -> {
          for (AutoExports.AutoExport autoExport : autoExports.value()) {
            for (Class<?> clazz : autoExport.types()) {
              autoExportMgr.addAutoExportClass(clazz, autoExport.scope(), autoExport.group());
            }
          }
        });
  }

  private void registerExtensions(Pf4bootPlugin pf4bootPlugin) {
    PluginWrapper wrapper = pf4bootPlugin.getWrapper();
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    Set<String> extensionClassNames = pluginManager.getExtensionClassNames(wrapper.getPluginId());
    String group = pf4bootPlugin.getGroup();
    Set<String> registeredBeanNames = ConcurrentHashMap.newKeySet();

    for (String extensionClassName : extensionClassNames) {
      try {
        logger.debug("register extension <{}> to platform [{}]", extensionClassName, group);

        Class<?> extensionClass = wrapper.getPluginClassLoader().loadClass(extensionClassName);
        SpringExtensionFactory extensionFactory =
            (SpringExtensionFactory) wrapper.getPluginManager().getExtensionFactory();

        Object bean = extensionFactory.create(extensionClass);
        String beanName = extensionFactory.getExtensionBeanName(extensionClass);

        pluginManager.registerBeanToPlatformContext(group, beanName, bean);
        registeredBeanNames.add(beanName);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }
    if (!registeredBeanNames.isEmpty()) {
      pluginExtensionBeans.put(wrapper.getPluginId(), registeredBeanNames);
    }
  }

  private SharingBeans registerShareServices(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    Class<?> primarySource = pf4bootPlugin.getClass();
    ConfigurableApplicationContext context = pf4bootPlugin.getPluginContext();

    return registerShareServices(pluginManager, primarySource, context);
  }

  private SharingBeans registerShareServices(
      Pf4bootPluginManager pluginManager,
      Class<?> primarySource,
      ConfigurableApplicationContext context) {

    SharingBeans sharingBeans = getExportBeans(primarySource, context);

    sharingBeans.getRootBeans().forEach(bean -> {
      pluginManager.registerBeanToRootContext(bean.getBeanName(), bean.getBean());
      logger.info("register export bean {} to root success", bean.getBeanName());
    });

    sharingBeans.getPlatformBeans().forEach(bean -> {
      pluginManager.registerBeanToPlatformContext(bean.getGroup(), bean.getBeanName(), bean.getBean());
      logger.info("register export bean {} to platform [{}] success", bean.getBeanName(), bean.getGroup());
    });

    sharingBeans.getAppBeans().forEach(bean -> {
      pluginManager.registerBeanToApplicationContext(bean.getBeanName(), bean.getBean());
      logger.info("register export bean {} to application success", bean.getBeanName());
    });

    return sharingBeans;
  }

  private SharingBeans getExportBeans(Class<?> primarySource, ApplicationContext context) {
    SharingBeans sharingBeans = new SharingBeans();

    /*
     * 1. @Export 标注的 Bean。
     */
    Map<String, Object> beans = context.getBeansWithAnnotation(Export.class);
    beans.forEach((key, value) -> {
      Export export = context.findAnnotationOnBean(key, Export.class);
      if (export != null) {
        sharingBeans.add(key, value, export.scope(), export.group());
      } else {
        sharingBeans.add(key, value, SharingScope.PLATFORM, PluginStarter.EMPTY);
      }
    });

    /*
     * 2. Bean 上的 @ExportBeans。
     */
    context.getBeansWithAnnotation(ExportBeans.class)
        .forEach((key, value) -> Optional.ofNullable(context.findAnnotationOnBean(key, ExportBeans.class))
            .ifPresent(exportBeans -> getSharingBeans(sharingBeans, context, exportBeans)));

    /*
     * 3. 插件主类或 PluginStarter 指定类上的 @ExportBeans。
     */
    getAnnotations(primarySource, ExportBeans.class)
        .forEach(exportBeans -> getSharingBeans(sharingBeans, context, exportBeans));

    /*
     * 4. AutoExports 规则导出的 Bean。
     *
     * 注意：getBeansOfType 可能触发非 lazy singleton 初始化。
     * 如果后面遇到启动阶段 Bean 被过早初始化的问题，可以改为：
     *
     * context.getBeanNamesForType(autoExport.getClazz(), true, false)
     */
    autoExportMgr.getAutoExportClasses().forEach(autoExport ->
        context.getBeansOfType(autoExport.getClazz())
            .forEach((key, value) ->
                sharingBeans.add(key, value, autoExport.getScope(), autoExport.getGroup()))
    );

    return sharingBeans;
  }

  private void getSharingBeans(
      SharingBeans sharingBeans,
      ApplicationContext context,
      ExportBeans exportBeans) {

    for (ExportBeans.Name4Bean name4Bean : exportBeans.name4Beans()) {
      for (String beanName : name4Bean.names()) {
        try {
          Object bean = context.getBean(beanName);
          sharingBeans.add(beanName, bean, name4Bean.scope(), name4Bean.group());
        } catch (BeansException e) {
          logger.warn("get export bean {} is failed", beanName, e);
        }
      }
    }

    for (ExportBeans.Class4Bean class4Bean : exportBeans.class4Beans()) {
      for (Class<?> beanClass : class4Bean.types()) {
        String[] beanNames = context.getBeanNamesForType(beanClass);
        for (String beanName : beanNames) {
          try {
            Object bean = context.getBean(beanName);
            sharingBeans.add(beanName, bean, class4Bean.scope(), class4Bean.group());
          } catch (BeansException e) {
            logger.warn("export bean {} is failed", beanName, e);
          }
        }
      }
    }
  }

  @Override
  public void stopPlugin(Pf4bootPlugin pf4bootPlugin) {
    /*
     * 1. 先停止定时任务，避免后续注销 Bean 时任务还在执行。
     */
    scheduledMgr.unregisterScheduledTasks(pf4bootPlugin);

    /*
     * 2. 注销扩展 Bean。
     */
    unregisterExtensions(pf4bootPlugin);

    /*
     * 3. 注销共享 Bean。
     *    注意：这里按 startedPlugin() 时记录的 SharingBeans 注销，不再重新扫描插件 Context。
     */
    unregisterShareServices(pf4bootPlugin);

    /*
     * 4. 最后注销自动导出规则。
     */
    unregisterAutoExports(pf4bootPlugin);
  }

  private void unregisterAutoExports(Pf4bootPlugin pf4bootPlugin) {
    Class<?> primarySource = pf4bootPlugin.getClass();

    getAnnotations(primarySource, AutoExports.class)
        .forEach(autoExports -> {
          for (AutoExports.AutoExport autoExport : autoExports.value()) {
            for (Class<?> clazz : autoExport.types()) {
              autoExportMgr.removeAutoExportClass(clazz, autoExport.scope(), autoExport.group());
            }
          }
        });
  }

  private void unregisterExtensions(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    PluginWrapper wrapper = pf4bootPlugin.getWrapper();
    Set<String> extensionClassNames = pluginManager.getExtensionClassNames(wrapper.getPluginId());
    String group = pf4bootPlugin.getGroup();

    for (String extensionClassName : extensionClassNames) {
      try {
        logger.debug("unregister extension <{}> to platform [{}]", extensionClassName, group);

        Class<?> extensionClass = wrapper.getPluginClassLoader().loadClass(extensionClassName);
        SpringExtensionFactory extensionFactory =
            (SpringExtensionFactory) wrapper.getPluginManager().getExtensionFactory();

        String beanName = extensionFactory.getExtensionBeanName(extensionClass);
        pluginManager.unregisterBeanFromPlatformContext(group, beanName);
        Set<String> beanNames = pluginExtensionBeans.get(wrapper.getPluginId());
        if (beanNames != null) {
          beanNames.remove(beanName);
        }
      } catch (Exception e) {
        logger.warn("unregister extension <{}> to platform [{}] failed", extensionClassName, group, e);
      }
    }
    Set<String> beanNames = pluginExtensionBeans.get(wrapper.getPluginId());
    if (beanNames == null || beanNames.isEmpty()) {
      pluginExtensionBeans.remove(wrapper.getPluginId());
    }
  }

  private void unregisterShareServices(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    String pluginId = pf4bootPlugin.getWrapper().getPluginId();

    SharingBeans sharingBeans = pluginSharingBeans.remove(pluginId);
    if (sharingBeans == null) {
      logger.debug("no sharing beans registered for plugin {}", pluginId);
      return;
    }

    /*
     * 注销顺序和注册顺序反过来：
     * application -> platform -> root
     */
    sharingBeans.getAppBeans().forEach(bean ->
        unregisterAppBean(pluginManager, bean));

    sharingBeans.getPlatformBeans().forEach(bean ->
        unregisterPlatformBean(pluginManager, bean));

    sharingBeans.getRootBeans().forEach(bean ->
        unregisterRootBean(pluginManager, bean));
  }

  private void unregisterAppBean(Pf4bootPluginManager pluginManager, SharingBean bean) {
    try {
      pluginManager.unregisterBeanFromApplicationContext(bean.getBeanName());
      logger.info("unregister export bean {} from application.", bean.getBeanName());
    } catch (BeansException e) {
      logger.warn("unregister export bean {} from application failed", bean.getBeanName(), e);
    }
  }

  private void unregisterPlatformBean(Pf4bootPluginManager pluginManager, SharingBean bean) {
    try {
      pluginManager.unregisterBeanFromPlatformContext(bean.getGroup(), bean.getBeanName());
      logger.info("unregister export bean {} from platform [{}].", bean.getBeanName(), bean.getGroup());
    } catch (BeansException e) {
      logger.warn("unregister export bean {} from platform failed", bean.getBeanName(), e);
    }
  }

  private void unregisterRootBean(Pf4bootPluginManager pluginManager, SharingBean bean) {
    try {
      pluginManager.unregisterBeanFromRootContext(bean.getBeanName());
      logger.info("unregister export bean {} from root.", bean.getBeanName());
    } catch (BeansException e) {
      logger.warn("unregister export bean {} from root failed", bean.getBeanName(), e);
    }
  }

  @Override
  public void beginDrain(Collection<String> pluginIds) {
    scheduledMgr.beginDrain(pluginIds);
  }

  @Override
  public boolean awaitDrain(Collection<String> pluginIds, long timeoutMillis) throws InterruptedException {
    return scheduledMgr.awaitDrain(pluginIds, timeoutMillis);
  }

  @Override
  public void endDrain(Collection<String> pluginIds) {
    scheduledMgr.endDrain(pluginIds);
  }

  @Override
  public List<DeploymentCheckResult> verifyStoppedPlugin(String pluginId, ClassLoader pluginClassLoader) {
    List<DeploymentCheckResult> results = new ArrayList<>();
    int sharingCount = getRegisteredSharingBeanCount(pluginId);
    int extensionCount = getRegisteredExtensionBeanCount(pluginId);
    int scheduledCount = getScheduledTaskCount(pluginId);
    int runningTaskCount = getRunningScheduledTaskCount(pluginId);
    if (sharingCount > 0) {
      results.add(DeploymentCheckResult.error(
          "SHARING_BEAN_NOT_CLEANED",
          "Plugin sharing beans remain after stop: " + sharingCount));
    }
    if (extensionCount > 0) {
      results.add(DeploymentCheckResult.error(
          "EXTENSION_BEAN_NOT_CLEANED",
          "Plugin extension beans remain after stop: " + extensionCount));
    }
    if (scheduledCount > 0) {
      results.add(DeploymentCheckResult.error(
          "SCHEDULED_TASK_NOT_CLEANED",
          "Plugin scheduled tasks remain after stop: " + scheduledCount));
    }
    if (runningTaskCount > 0) {
      results.add(DeploymentCheckResult.error(
          "SCHEDULED_TASK_STILL_RUNNING",
          "Plugin scheduled tasks are still running after stop: " + runningTaskCount));
    }
    if (results.isEmpty()) {
      return Collections.singletonList(
          DeploymentCheckResult.info("CORE_CLEANUP_VERIFIED", "Plugin shared resources are cleaned"));
    }
    return results;
  }

  @Override
  public List<DeploymentCheckResult> verifyStartedPlugin(
      PluginHealthContext context,
      ClassLoader pluginClassLoader) {
    List<DeploymentCheckResult> results = new ArrayList<>();
    String pluginId = context.getPluginId();
    results.add(DeploymentCheckResult.info(
        "SHARING_BEAN_HEALTH",
        "Plugin sharing bean count: " + getRegisteredSharingBeanCount(pluginId)));
    results.add(DeploymentCheckResult.info(
        "EXTENSION_BEAN_HEALTH",
        "Plugin extension bean count: " + getRegisteredExtensionBeanCount(pluginId)));
    results.add(DeploymentCheckResult.info(
        "SCHEDULED_TASK_HEALTH",
        "Plugin scheduled task count: " + getScheduledTaskCount(pluginId)));
    return results;
  }
}
