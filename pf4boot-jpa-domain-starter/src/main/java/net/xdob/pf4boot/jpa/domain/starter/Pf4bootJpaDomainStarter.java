package net.xdob.pf4boot.jpa.domain.starter;

import net.xdob.pf4boot.annotation.SpringBootPlugin;
import net.xdob.pf4boot.jpa.Pf4bootJpaPersistenceProvider;
import org.hibernate.engine.spi.SessionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 领域 JPA 能力插件 starter。
 *
 * <p>该 starter 面向“数据源能力插件”使用：一个领域插件创建一个共享事务域，并把
 * DataSource、EntityManagerFactory 和 TransactionManager 导出给依赖它的业务插件。
 * 业务插件通过 `pf4boot-jpa-starter` 的 SHARED 模式绑定这些平台 Bean。</p>
 */
@ConditionalOnClass({
    LocalContainerEntityManagerFactoryBean.class,
    EntityManager.class,
    SessionImplementor.class
})
@ConditionalOnProperty(
    prefix = "pf4boot.plugin.jpa.domain",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties({
    JpaProperties.class,
    HibernateProperties.class,
    Pf4bootJpaDomainProperties.class
})
@SpringBootPlugin(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
public class Pf4bootJpaDomainStarter implements ResourceLoaderAware {

  static final Logger LOG = LoggerFactory.getLogger(Pf4bootJpaDomainStarter.class);

  private final JpaProperties jpaProperties;

  private final HibernateProperties hibernateProperties;

  private final List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers;

  private final Pf4bootJpaDomainProperties domainProperties;

  private ResourceLoader resourceLoader = new DefaultResourceLoader();

  public Pf4bootJpaDomainStarter(
      JpaProperties jpaProperties,
      HibernateProperties hibernateProperties,
      List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers,
      Pf4bootJpaDomainProperties domainProperties) {
    this.jpaProperties = jpaProperties;
    this.hibernateProperties = hibernateProperties;
    this.hibernatePropertiesCustomizers = hibernatePropertiesCustomizers;
    this.domainProperties = domainProperties;
  }

  @Bean
  public DataSource domainDataSource() {
    String domainId = this.domainProperties.requireDomainId();
    DataSourceProperties datasource = this.domainProperties.getDatasource();
    try {
      String url = datasource.determineUrl();
      if (!StringUtils.hasText(url)) {
        throw new IllegalStateException("datasource url is empty");
      }
      DataSource dataSource = datasource.initializeDataSourceBuilder().build();
      LOG.info("[PF4BOOT-JPA] create domain {} DataSource, url={}", domainId, url);
      return dataSource;
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "[PJF-004] Domain JPA provider '" + domainId +
              "' has invalid datasource configuration.", e);
    }
  }

  @Bean
  public LocalContainerEntityManagerFactoryBean domainEntityManagerFactory(
      @Qualifier("domainDataSource") DataSource dataSource) {
    String domainId = this.domainProperties.requireDomainId();
    String[] packagesToScan = this.domainProperties.resolveEntityPackages();
    new JpaDomainEntityPackageValidator(this.resourceLoader).validate(domainId, packagesToScan);

    LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
    factoryBean.setDataSource(dataSource);
    factoryBean.setPackagesToScan(packagesToScan);
    factoryBean.setPersistenceUnitName("pf4boot-domain-" + domainId);
    factoryBean.setPersistenceProviderClass(Pf4bootJpaPersistenceProvider.class);
    factoryBean.setJpaVendorAdapter(jpaVendorAdapter());
    factoryBean.setJpaPropertyMap(getVendorProperties());

    LOG.info("[PF4BOOT-JPA] create domain {} EntityManagerFactory, packages={}",
        domainId, (Object) packagesToScan);
    return factoryBean;
  }

  @Override
  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader != null ? resourceLoader : new DefaultResourceLoader();
  }

  @Bean
  public PlatformTransactionManager domainTransactionManager(
      @Qualifier("domainEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }

  @Bean
  public DomainJpaPlatformExporter domainJpaPlatformExporter(
      @Qualifier("domainDataSource") DataSource dataSource,
      @Qualifier("domainEntityManagerFactory") EntityManagerFactory entityManagerFactory,
      @Qualifier("domainTransactionManager") PlatformTransactionManager transactionManager) {
    return new DomainJpaPlatformExporter(
        this.domainProperties, dataSource, entityManagerFactory, transactionManager);
  }

  protected Map<String, Object> getVendorProperties() {
    Supplier<String> ddlAuto = this.domainProperties::resolveDdlAuto;
    return new LinkedHashMap<>(this.hibernateProperties.determineHibernateProperties(
        this.jpaProperties.getProperties(),
        new HibernateSettings()
            .ddlAuto(ddlAuto)
            .hibernatePropertiesCustomizers(this.hibernatePropertiesCustomizers)
    ));
  }

  private HibernateJpaVendorAdapter jpaVendorAdapter() {
    HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
    adapter.setShowSql(this.jpaProperties.isShowSql());
    if (StringUtils.hasText(this.jpaProperties.getDatabasePlatform())) {
      adapter.setDatabasePlatform(this.jpaProperties.getDatabasePlatform());
    }
    return adapter;
  }
}
