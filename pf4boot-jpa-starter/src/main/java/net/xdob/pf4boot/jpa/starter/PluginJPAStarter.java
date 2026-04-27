package net.xdob.pf4boot.jpa.starter;

import com.google.common.collect.Sets;
import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.annotation.SpringBootPlugin;
import org.hibernate.engine.spi.SessionImplementor;
import org.pf4j.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
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
 * 3. 如果插件需要指定实体扫描包，可以使用 Spring Boot 原生的 @EntityScan，
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
@EnableConfigurationProperties({JpaProperties.class, HibernateProperties.class})
@SpringBootPlugin
public class PluginJPAStarter implements BeanFactoryAware {
  static final Logger LOG = LoggerFactory.getLogger(PluginJPAStarter.class);

  private final DataSource dataSource;
  private final JpaProperties properties;
  private final HibernateProperties hibernateProperties;
  private final List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers;

  private BeanFactory beanFactory;

  public PluginJPAStarter(
      DataSource dataSource,
      JpaProperties properties,
      HibernateProperties hibernateProperties,
      List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers) {
    this.dataSource = dataSource;
    this.properties = properties;
    this.hibernateProperties = hibernateProperties;
    this.hibernatePropertiesCustomizers = hibernatePropertiesCustomizers;
  }

  public DataSource getDataSource() {
    return dataSource;
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
  @ConditionalOnMissingBean(EntityManagerFactory.class)
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(
      EntityManagerFactoryBuilder factoryBuilder) {

    Map<String, Object> vendorProperties = getVendorProperties();
    String[] packagesToScan = getPackagesToScan();

    LOG.info("[PF4BOOT-JPA] create plugin EntityManagerFactory, packages={}", (Object) packagesToScan);

    return factoryBuilder
        .dataSource(this.dataSource)
        .mappingResources(getMappingResources())
        .properties(vendorProperties)
        .packages(packagesToScan)
        .build();
  }

  @Bean
  @ConditionalOnMissingBean(TransactionManager.class)
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
}
