package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.*;
import net.xdob.pf4boot.annotation.EventListener;
import net.xdob.pf4boot.internal.SpringExtensionFactory;
import net.xdob.pf4boot.modal.AutowiredElement;
import net.xdob.pf4boot.modal.DynamicBean;
import net.xdob.pf4boot.modal.SharingBeans;
import net.xdob.pf4boot.modal.SharingScope;
import net.xdob.pf4boot.util.Injections;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class DefaultPf4bootPluginSupport implements Pf4bootPluginSupport{
  static final Logger logger = LoggerFactory.getLogger(DefaultPf4bootPluginSupport.class);

  final ConcurrentHashMap<String, DynamicBean> dynamicImportBeans = new ConcurrentHashMap<>();

  @Override
  public int getPriority() {
    return Pf4bootPluginSupport.HEIGHT_PRIORITY;
  }

  @Override
  public void initiatePluginManager(Pf4bootPluginManager pluginManager) {
    ConfigurableApplicationContext applicationContext = pluginManager.getApplicationContext();
    registerEventListeners(pluginManager, applicationContext);
    registerShareServices(pluginManager, null, applicationContext);
    registerDynamicImportBeans(null, applicationContext);
  }

  @Override
  public void initiatedPluginManager(Pf4bootPluginManager pluginManager) {

  }

  private <A extends Annotation> List<A> getAnnotations(Class<?> primarySource, Class<A> annotationClazz) {
    List<A> annotations = new ArrayList<>();
    Optional.ofNullable(primarySource.getAnnotation(annotationClazz))
        .ifPresent(annotations::add);

    PluginStarter pluginStarter = primarySource.getAnnotation(PluginStarter.class);
    if(pluginStarter!=null){
      Class<?>[] starterClasses = pluginStarter.value();
      for (Class<?> starterClass : starterClasses) {
        Optional.ofNullable(starterClass.getAnnotation(annotationClazz))
            .ifPresent(annotations::add);
      }
    }
    return annotations;
  }

  @Override
  public void initiatePlugin(Pf4bootPlugin pf4bootPlugin) {

  }

  @Override
  public void initiatedPlugin(Pf4bootPlugin pf4bootPlugin) {

  }

  @Override
  public void startedPlugin(Pf4bootPlugin pf4bootPlugin) {
    //register EventListeners
    registerEventListeners(pf4bootPlugin);
    //register ShareServices
    registerShareServices(pf4bootPlugin);
    // register Extensions
    registerExtensions(pf4bootPlugin);
    //register dynamicImportBeans
    registerDynamicImportBeans(pf4bootPlugin);
  }



  private void registerEventListeners(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    ConfigurableApplicationContext context = pf4bootPlugin.getPluginContext();
    registerEventListeners(pluginManager, context);
  }

  private static void registerEventListeners(Pf4bootPluginManager pluginManager, ConfigurableApplicationContext context ) {
    Map<String, Object> beans = context.getBeansWithAnnotation(EventListener.class);
    for (String beanName : beans.keySet()) {
      Object bean = beans.get(beanName);
      pluginManager.getPf4bootEventBus().register(bean);
      logger.debug("register Event Listener: {}={}", beanName , bean);
    }
  }

  private void registerExtensions(Pf4bootPlugin pf4bootPlugin) {
    PluginWrapper wrapper = pf4bootPlugin.getWrapper();
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    Set<String> extensionClassNames = pluginManager.getExtensionClassNames(wrapper.getPluginId());
    for (String extensionClassName : extensionClassNames) {
      try {
        logger.debug("register extension <{}> to main ApplicationContext", extensionClassName);
        Class<?> extensionClass = wrapper.getPluginClassLoader().loadClass(extensionClassName);
        SpringExtensionFactory extensionFactory = (SpringExtensionFactory) wrapper
            .getPluginManager().getExtensionFactory();
        Object bean = extensionFactory.create(extensionClass);
        String beanName = extensionFactory.getExtensionBeanName(extensionClass);
        pluginManager.registerBeanToPlatformContext(beanName, bean);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }
  }

  private void registerShareServices(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    Class<?> primarySource = pf4bootPlugin.getClass();
    ConfigurableApplicationContext context = pf4bootPlugin.getPluginContext();
    registerShareServices(pluginManager, primarySource, context);
  }

  private void registerShareServices(Pf4bootPluginManager pluginManager, Class<?> primarySource, ConfigurableApplicationContext context) {
    SharingBeans sharingBeans = getExportBeans(primarySource, context);
    sharingBeans.getPlatformBeans().forEach((beanName, bean) -> {
      pluginManager.registerBeanToPlatformContext(beanName, bean);
      logger.info("register export bean {} platform success", beanName);
      dynamicImport(bean.getClass(), bean);
    });
    sharingBeans.getAppBeans().forEach((beanName, bean) -> {
      pluginManager.registerBeanToApplicationContext(beanName, bean);
      logger.info("register export bean {} to application success", beanName);
      dynamicImport(bean.getClass(), bean);
    });
  }

  private SharingBeans getExportBeans(Class<?> primarySource, ApplicationContext context) {
    SharingBeans sharingBeans = new SharingBeans();

    Map<String, Object> beans = context.getBeansWithAnnotation(Export.class);
    logger.info("get export beans : {}", beans);
    beans.forEach((key, value) -> {
      Export export = context.findAnnotationOnBean(key, Export.class);
      if (export != null) {
        if (SharingScope.APPLICATION.equals(export.scope())) {
          sharingBeans.getAppBeans().put(key, value);
        } else {
          sharingBeans.getPlatformBeans().put(key, value);
        }
      } else {
        sharingBeans.getPlatformBeans().put(key, value);
      }
    });

    context.getBeansWithAnnotation(ExportBeans.class)
      .forEach((key, value) -> {
      Optional.ofNullable(context.findAnnotationOnBean(key, ExportBeans.class))
        .ifPresent(exportBeans -> {
          getSharingBeans(sharingBeans, context, exportBeans);
        });
    });
    if (primarySource != null) {

      this.getAnnotations(primarySource, ExportBeans.class)
        .forEach(exportBeans -> {
          getSharingBeans(sharingBeans, context, exportBeans);
        });
    }
    return sharingBeans;
  }

  private  void getSharingBeans(SharingBeans sharingBeans, ApplicationContext context, ExportBeans exportBeans) {
    Map<String, Object> map = (SharingScope.APPLICATION.equals(exportBeans.scope()))
      ? sharingBeans.getAppBeans() : sharingBeans.getPlatformBeans();

    for (String beanName : exportBeans.beanNames()) {
      try {
        Object bean = context.getBean(beanName);
        map.put(beanName, bean);
      } catch (BeansException e) {
        logger.warn("get export bean {} is failed", beanName, e);
      }
    }
    for (Class<?> beanClass : exportBeans.beans()) {
      String[] beanNames = context.getBeanNamesForType(beanClass);
      for (String beanName : beanNames) {
        try {
          Object bean = context.getBean(beanName);
          map.put(beanName, bean);
        } catch (BeansException e) {
          logger.warn("export bean {} is failed", beanName, e);
        }
      }
    }
  }

  private void dynamicImport(Class<?> beanClass, Object value){
    for (DynamicBean dynamicBean : dynamicImportBeans.values()) {
      BeanWrapper beanWrapper = new BeanWrapperImpl(dynamicBean.getBean());
      DirectFieldAccessor accessor = new DirectFieldAccessor(dynamicBean.getBean());
      beanWrapper.setAutoGrowNestedPaths(true);
      dynamicBean.findElement(beanClass)
          .ifPresent(e->{
            try{
              accessor.setPropertyValue(e.getPropertyName(), value);
            } catch (NotWritablePropertyException ignore) {
              beanWrapper.setPropertyValue(e.getPropertyName(), value);
            }
            logger.info("dynamic import bean {} set {}={}", dynamicBean.getBean(), e.getPropertyName(), value);
          });
    }

  }

  private void registerDynamicImportBeans(Pf4bootPlugin pf4bootPlugin) {
    Class<?> primarySource = pf4bootPlugin.getClass();
    ConfigurableApplicationContext context = pf4bootPlugin.getPluginContext();
    registerDynamicImportBeans(primarySource, context);
  }

  private void registerDynamicImportBeans(Class<?> primarySource, ConfigurableApplicationContext context) {
    Map<String, Object> beans = getDynamicImportBeans(primarySource, context);
    for (String beanName : beans.keySet()) {
      Object bean = beans.get(beanName);
      Injections injections = new Injections();
      List<AutowiredElement> elements = injections.buildAutowiringMetadata(bean.getClass());
      DynamicBean dynamicBean = new DynamicBean(bean);
      for (AutowiredElement element : elements) {
        if (!element.isRequired()){
          dynamicBean.getElements().add(element);
        }
      }
      if(!dynamicBean.getElements().isEmpty()) {
        dynamicImportBeans.put(beanName, dynamicBean);
        logger.debug("register DynamicImport Bean: {}={}", beanName, bean);
      }
    }
  }

  private Map<String, Object> getDynamicImportBeans(Class<?> primarySource, ConfigurableApplicationContext context) {
    Map<String, Object> beans = context.getBeansWithAnnotation(DynamicImport.class);
    context.getBeansWithAnnotation(DynamicImportBeans.class)
      .forEach((key,bean)->{
      Optional.ofNullable(context.findAnnotationOnBean(key, DynamicImportBeans.class))
        .ifPresent(dynamic -> {
          beans.putAll(getDynamicImportBeans(context, dynamic));
        });
    });
    if (primarySource != null) {
      this.getAnnotations(primarySource, DynamicImportBeans.class)
        .forEach(dynamic -> {
          beans.putAll(getDynamicImportBeans(context, dynamic));
        });
    }
    return beans;
  }

  private Map<String, Object> getDynamicImportBeans(ConfigurableApplicationContext context, DynamicImportBeans dynamic) {
    Map<String, Object> beans = new HashMap<>();
    for (Class<?> beanClass : dynamic.beans()) {
      String[] beanNames = context.getBeanNamesForType(beanClass);
      for (String beanName : beanNames) {
        Object bean = context.getBean(beanName);
        beans.put(beanName, bean);
      }
    }
    for (String beanName : dynamic.beanNames()) {
      Object bean = context.getBean(beanName);
      beans.put(beanName, bean);
    }
    return beans;
  }

  @Override
  public void stopPlugin(Pf4bootPlugin pf4bootPlugin) {
    // unregister Extensions
    unregisterExtensions(pf4bootPlugin);
    //unregister ShareServices
    unregisterShareServices(pf4bootPlugin);
    //unregister PluginListeners
    unregisterEventListeners(pf4bootPlugin);
    //unregister DynamicImportBeans
    unregisterDynamicImportBeans(pf4bootPlugin);
  }


  private void unregisterExtensions(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    PluginWrapper wrapper = pf4bootPlugin.getWrapper();
    Set<String> extensionClassNames = pluginManager
        .getExtensionClassNames(wrapper.getPluginId());
    for (String extensionClassName : extensionClassNames) {
      try {
        logger.debug("unregister extension <{}> to main ApplicationContext", extensionClassName);
        Class<?> extensionClass = wrapper.getPluginClassLoader().loadClass(extensionClassName);
        SpringExtensionFactory extensionFactory = (SpringExtensionFactory) wrapper
            .getPluginManager().getExtensionFactory();
        String beanName = extensionFactory.getExtensionBeanName(extensionClass);
        pluginManager.unregisterBeanFromPlatformContext(beanName);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }
    ConfigurableApplicationContext applicationContext = pluginManager.getApplicationContext();
    for (Object bean : dynamicImportBeans.values()) {
      applicationContext.getBeanFactory().autowireBean(bean);
    }
  }

  private void unregisterDynamicImportBeans(Pf4bootPlugin pf4bootPlugin) {
    Map<String, Object> beans = getDynamicImportBeans(pf4bootPlugin.getClass(), pf4bootPlugin.getPluginContext());
    for (String beanName : beans.keySet()) {
      dynamicImportBeans.remove(beanName);
      logger.debug("unregister DynamicImport Bean: {}", beanName);
    }
  }

  private void unregisterShareServices(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    SharingBeans sharingBeans = getExportBeans(pf4bootPlugin.getClass(), pf4bootPlugin.getPluginContext());
    sharingBeans.getAppBeans().forEach((beanName, bean) -> {
      try {
        pluginManager.unregisterBeanFromApplicationContext(beanName);
        logger.info("unregister export bean {} from application.", beanName);
        dynamicImport(bean.getClass(), null);
      } catch (BeansException e) {
        logger.warn("unregister export bean {} from application failed", beanName, e);
      }
    });
    sharingBeans.getPlatformBeans().forEach((beanName, bean) -> {
      try {
        pluginManager.unregisterBeanFromPlatformContext(beanName);
        logger.info("unregister export bean {} from platform.", beanName);
        dynamicImport(bean.getClass(), null);
      } catch (BeansException e) {
        logger.warn("unregister export bean {} from platform failed", beanName, e);
      }
    });


  }


  private void unregisterEventListeners(Pf4bootPlugin pf4bootPlugin) {
    Pf4bootPluginManager pluginManager = pf4bootPlugin.getPluginManager();
    ApplicationContext applicationContext = pf4bootPlugin.getPluginContext();
    Map<String, Object> beans = applicationContext.getBeansWithAnnotation(EventListener.class);
    for (String beanName : beans.keySet()) {
      Object bean = beans.get(beanName);
      pluginManager.getPf4bootEventBus().unregister(bean);
    }
  }

}
