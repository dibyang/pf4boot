package net.xdob.pf4boot.jpa.starter;

import com.google.common.collect.Sets;
import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.annotation.SpringBootPlugin;
import net.xdob.pf4boot.jpa.domain.JpaDomainDescriptor;
import net.xdob.pf4boot.jpa.starter.reload.JpaPluginBindingRegistry;
import org.hibernate.engine.spi.SessionImplementor;
import org.pf4j.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * PluginJPAStarter
 *
 * 插件 JPA 自动配置。
 *
 * 注意：
 * 1. 默认不启用，避免非 JPA 插件误初始化 EntityManagerFactory。
 * 2. 需要插件显式配置：
 *
 *    pf4boot.plugin.jpa.enabled=true
 *
 * 3. 默认 mode=LOCAL，保持插件本地 EntityManagerFactory/TransactionManager。
 * 4. mode=SHARED 时只校验领域能力插件导出的 EMF/TM，不创建本地 EMF/TM。
 * 5. 如果插件需要指定实体扫描包，可以使用 Spring Boot 原生的 @EntityScan，
 *    或者依赖插件主类所在包作为默认扫描根包。
 *
 * @author yangzj
 * @version 1.0
 */
@ConditionalOnClass({
    LocalContainerEntityManagerFactoryBean.class,
    EntityManager.class,
    SessionImplementor.class
})
@ConditionalOnProperty(
    prefix = "pf4boot.plugin.jpa",
    name = "enabled",
    havingValue = "true"
)
@EnableConfigurationProperties({JpaProperties.class, HibernateProperties.class, Pf4bootJpaProperties.class})
@SpringBootPlugin(exclude = {
    JpaRepositoriesAutoConfiguration.class,
    DataSourceAutoConfiguration.class
})
@Import(SharedJpaAutoConfiguration.class)
public class PluginJPAStarter implements BeanFactoryAware, EnvironmentAware, InitializingBean, DisposableBean {
  static final Logger LOG = LoggerFactory.getLogger(PluginJPAStarter.class);

  private final JpaProperties properties;
  private final HibernateProperties hibernateProperties;
  private final List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers;
  private final Pf4bootJpaProperties pf4bootJpaProperties;

  private BeanFactory beanFactory;
  private Environment environment;
  private String registeredPluginId;

  public PluginJPAStarter(
      JpaProperties properties,
      HibernateProperties hibernateProperties,
      List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers,
      Pf4bootJpaProperties pf4bootJpaProperties) {
    this.properties = properties;
    this.hibernateProperties = hibernateProperties;
    this.hibernatePropertiesCustomizers = hibernatePropertiesCustomizers;
    this.pf4bootJpaProperties = pf4bootJpaProperties;
  }

  public Pf4bootJpaProperties getPf4bootJpaProperties() {
    return pf4bootJpaProperties;
  }

  protected Map<String, Object> getVendorProperties() {
    /*
     * 不建议插件默认 ddl-auto=update。
     * update 会在插件启动时自动改表结构，生产环境风险较高。
     *
     * 如果确实需要自动建表/更新表结构，请在配置里显式指定：
     *
     * spring.jpa.hibernate.ddl-auto=update
     */
    Supplier<String> defaultDdlMode = () -> "none";

    return new LinkedHashMap<>(this.hibernateProperties.determineHibernateProperties(
        properties.getProperties(),
        new HibernateSettings()
            .ddlAuto(defaultDdlMode)
            .hibernatePropertiesCustomizers(this.hibernatePropertiesCustomizers)
    ));
  }

  @Bean
  @Conditional(LocalJpaModeCondition.class)
  @ConditionalOnMissingBean(value = EntityManagerFactory.class, search = SearchStrategy.CURRENT)
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(
      EntityManagerFactoryBuilder factoryBuilder,
      DataSource dataSource) {

    Map<String, Object> vendorProperties = getVendorProperties();
    String[] packagesToScan = getPackagesToScan();

    LOG.info("[PF4BOOT-JPA] create plugin EntityManagerFactory, packages={}", (Object) packagesToScan);

    return factoryBuilder
        .dataSource(dataSource)
        .mappingResources(getMappingResources())
        .properties(vendorProperties)
        .packages(packagesToScan)
        .build();
  }

  @Bean
  @Conditional(LocalJpaModeCondition.class)
  @ConditionalOnMissingBean(value = TransactionManager.class, search = SearchStrategy.CURRENT)
  public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }

  protected String[] getPackagesToScan() {
    Set<String> packages = Sets.newLinkedHashSet();

    /*
     * 1. 优先使用 @EntityScan / EntityScanPackages。
     */
    try {
      List<String> pkgs = EntityScanPackages.get(this.beanFactory).getPackageNames();
      if (!ObjectUtils.isEmpty(pkgs)) {
        packages.addAll(pkgs);
      }
    } catch (Exception e) {
      LOG.debug("[PF4BOOT-JPA] no EntityScanPackages found", e);
    }

    /*
     * 2. 再使用 Spring Boot 自动配置包。
     */
    try {
      if (AutoConfigurationPackages.has(this.beanFactory)) {
        List<String> pkgs = AutoConfigurationPackages.get(this.beanFactory);
        if (!ObjectUtils.isEmpty(pkgs)) {
          packages.addAll(pkgs);
        }
      }
    } catch (Exception e) {
      LOG.debug("[PF4BOOT-JPA] no AutoConfigurationPackages found", e);
    }

    /*
     * 3. 最后追加插件主类所在包。
     *    注意这里必须保护 PluginApplication.BEAN_PLUGIN 不存在的情况，
     *    否则非插件上下文或初始化顺序变化时会直接抛异常。
     */
    Plugin plugin = getPluginIfPresent(this.beanFactory);
    if (plugin != null && plugin.getClass().getPackage() != null) {
      packages.add(plugin.getClass().getPackage().getName());
    }

    if (packages.isEmpty()) {
      throw new IllegalStateException(
          "No JPA entity packages found for plugin. " +
              "Please add @EntityScan or make sure plugin bean '" +
              PluginApplication.BEAN_PLUGIN + "' is registered."
      );
    }

    return StringUtils.toStringArray(packages);
  }

  private String[] getMappingResources() {
    List<String> mappingResources = this.properties.getMappingResources();
    return (!ObjectUtils.isEmpty(mappingResources)
        ? StringUtils.toStringArray(mappingResources)
        : null);
  }

  public static Plugin getPlugin(BeanFactory beanFactory) {
    return beanFactory.getBean(PluginApplication.BEAN_PLUGIN, Plugin.class);
  }

  public static Plugin getPluginIfPresent(BeanFactory beanFactory) {
    if (beanFactory == null) {
      return null;
    }
    try {
      if (beanFactory.containsBean(PluginApplication.BEAN_PLUGIN)) {
        return beanFactory.getBean(PluginApplication.BEAN_PLUGIN, Plugin.class);
      }
    } catch (Exception e) {
      LOG.debug("[PF4BOOT-JPA] get plugin bean failed", e);
    }
    return null;
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
    this.beanFactory = beanFactory;
  }

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    JpaPluginBinding binding = JpaPluginBindingResolver.resolve(this.environment, this.beanFactory);
    if (binding == null) {
      binding = fallbackBinding();
    }
    if (!binding.isShared()) {
      return;
    }

    String domainId = binding.getDomainId();
    if (!StringUtils.hasText(domainId)) {
      throw new IllegalStateException(
          "[PJF-006] Shared JPA binding for plugin '" + binding.getPluginId()
              + "' requires domain-id.");
    }

    validateDomainBinding(binding.getPluginId(), binding.primaryDomain());
    for (JpaDomainBinding additionalDomain : binding.getAdditionalDomains()) {
      validateDomainBinding(binding.getPluginId(), additionalDomain);
    }
    registerBinding(binding);
  }

  @Override
  public void destroy() {
    JpaPluginBindingRegistry registry = findBindingRegistry();
    if (registry != null && StringUtils.hasText(this.registeredPluginId)) {
      registry.remove(this.registeredPluginId);
    }
  }

  private void validateDomainBinding(String pluginId, JpaDomainBinding domainBinding) {
    String domainId = domainBinding.getDomainId();
    if (!StringUtils.hasText(domainId)) {
      throw new IllegalStateException(
          "[PJF-006] Shared JPA binding for plugin '" + pluginId
              + "' requires domain-id.");
    }

    String entityManagerFactoryRef = domainBinding.resolveEntityManagerFactoryRef();
    String transactionManagerRef = domainBinding.resolveTransactionManagerRef();
    JpaDomainDescriptor descriptor = getDomainDescriptor(pluginId, domainBinding);
    if (!entityManagerFactoryRef.equals(descriptor.getEntityManagerFactoryBeanName())) {
      throw new IllegalStateException(
          "[PJF-007] Shared JPA domain '" + domainId + "' descriptor entityManagerFactory '"
              + descriptor.getEntityManagerFactoryBeanName() + "' does not match binding '"
              + entityManagerFactoryRef + "'.");
    }
    if (!transactionManagerRef.equals(descriptor.getTransactionManagerBeanName())) {
      throw new IllegalStateException(
          "[PJF-007] Shared JPA domain '" + domainId + "' descriptor transactionManager '"
              + descriptor.getTransactionManagerBeanName() + "' does not match binding '"
              + transactionManagerRef + "'.");
    }

    getRequiredBean(
        entityManagerFactoryRef,
        EntityManagerFactory.class,
        "[PJF-002] Shared JPA domain '" + domainId + "' requires EntityManagerFactory bean '" +
            entityManagerFactoryRef + "'.");
    getRequiredBean(
        transactionManagerRef,
        TransactionManager.class,
        "[PJF-003] Shared JPA domain '" + domainId + "' requires TransactionManager bean '" +
            transactionManagerRef + "'.");

    LOG.info(
        "[PF4BOOT-JPA] bind shared JPA domain {}, plugin={}, provider={}, entityManagerFactory={}, transactionManager={}",
        domainId, pluginId, descriptor.getProviderPluginId(),
        entityManagerFactoryRef, transactionManagerRef);
  }

  private JpaDomainDescriptor getDomainDescriptor(String pluginId, JpaDomainBinding domainBinding) {
    String descriptorName = domainBinding.resolveDescriptorRef();
    JpaDomainDescriptor descriptor = getRequiredBean(
        descriptorName,
        JpaDomainDescriptor.class,
        "[PJF-007] Shared JPA domain '" + domainBinding.getDomainId()
            + "' requires ready descriptor bean '" + descriptorName
            + "' for consumer plugin '" + pluginId + "'.");
    if (!descriptor.isReady()) {
      throw new IllegalStateException(
          "[PJF-007] Shared JPA domain '" + domainBinding.getDomainId()
              + "' descriptor is not ready for consumer plugin '" + pluginId + "'.");
    }
    return descriptor;
  }

  private JpaPluginBinding fallbackBinding() {
    return new JpaPluginBinding(
        null,
        this.pf4bootJpaProperties.getMode(),
        this.pf4bootJpaProperties.getDomainId(),
        this.pf4bootJpaProperties.getEntityManagerFactoryRef(),
        this.pf4bootJpaProperties.getTransactionManagerRef(),
        this.pf4bootJpaProperties.getDescriptorRef(),
        JpaPluginBindingResolver.additionalDomains(this.pf4bootJpaProperties.getAdditionalDomains()),
        false);
  }

  private void registerBinding(JpaPluginBinding binding) {
    JpaPluginBindingRegistry registry = findBindingRegistry();
    if (registry == null) {
      LOG.debug("[PF4BOOT-JPA] shared JPA binding registry not found, plugin={}", binding.getPluginId());
      return;
    }
    if (StringUtils.hasText(binding.getPluginId())) {
      registry.register(binding);
      this.registeredPluginId = binding.getPluginId();
      LOG.info("[PF4BOOT-JPA] register shared JPA binding, plugin={}, domain={}",
          binding.getPluginId(), binding.getDomainId());
    }
  }

  private JpaPluginBindingRegistry findBindingRegistry() {
    if (this.beanFactory instanceof ListableBeanFactory) {
      String[] names = ((ListableBeanFactory) this.beanFactory).getBeanNamesForType(JpaPluginBindingRegistry.class);
      if (names.length > 0) {
        return ((ListableBeanFactory) this.beanFactory).getBean(names[0], JpaPluginBindingRegistry.class);
      }
    }
    try {
      return this.beanFactory.getBean(JpaPluginBindingRegistry.class);
    } catch (Exception e) {
      LOG.debug("[PF4BOOT-JPA] no shared JPA binding registry available", e);
      return null;
    }
  }

  private <T> T getRequiredBean(String beanName, Class<T> type, String message) {
    try {
      return this.beanFactory.getBean(beanName, type);
    } catch (BeansException e) {
      throw new IllegalStateException(message, e);
    }
  }
}
