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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
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
import java.util.*;
import java.util.function.Supplier;

/**
 * PluginJPASupport
 *
 * @author yangzj
 * @version 1.0
 */
@ConditionalOnClass({ LocalContainerEntityManagerFactoryBean.class, EntityManager.class, SessionImplementor.class })
@EnableConfigurationProperties({JpaProperties.class, HibernateProperties.class})
//@AutoConfigureAfter({ DataSourceAutoConfiguration.class })
@SpringBootPlugin
public class PluginJPAStarter implements BeanFactoryAware {
  static final Logger LOG = LoggerFactory.getLogger(PluginJPAStarter.class);



  private final DataSource dataSource;


  private final JpaProperties properties;


  private final HibernateProperties hibernateProperties;


  private BeanFactory beanFactory;

  private final List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers;


  public DataSource getDataSource() {
    return dataSource;
  }

  public PluginJPAStarter(DataSource dataSource, JpaProperties properties, HibernateProperties hibernateProperties, List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers) {
    this.dataSource = dataSource;
    this.properties = properties;
    this.hibernateProperties = hibernateProperties;
    this.hibernatePropertiesCustomizers = hibernatePropertiesCustomizers;
  }



  protected Map<String, Object> getVendorProperties() {
    Supplier<String> defaultDdlMode = () -> "update";
    return new LinkedHashMap<>(this.hibernateProperties.determineHibernateProperties(
        properties.getProperties(), new HibernateSettings().ddlAuto(defaultDdlMode)
            .hibernatePropertiesCustomizers(this.hibernatePropertiesCustomizers)));
  }


  @Bean
  @ConditionalOnMissingBean(EntityManagerFactory.class)
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder factoryBuilder) {
    Map<String, Object> vendorProperties = getVendorProperties();
    //LOG.info("vendorProperties={}",vendorProperties);
    return factoryBuilder.dataSource(this.dataSource).mappingResources(getMappingResources())
        .properties(vendorProperties)
        .packages(getPackagesToScan()).build();
  }

  @Bean
  @ConditionalOnMissingBean(TransactionManager.class)
  public PlatformTransactionManager transactionManager() {
    return new JpaTransactionManager();
  }


  protected String[] getPackagesToScan() {
    Set<String> packages = Sets.newHashSet();
    List<String> pkgs = EntityScanPackages.get(this.beanFactory).getPackageNames();
    if(!pkgs.isEmpty()){
      packages.addAll(pkgs);
    }
    if (AutoConfigurationPackages.has(this.beanFactory)) {
      pkgs = AutoConfigurationPackages.get(this.beanFactory);
      if(!pkgs.isEmpty()){
        packages.addAll(pkgs);
      }
    }

    Plugin plugin = getPlugin(this.beanFactory);

    //String pkg = ((PluginHandler)plugin).getPlugin().getClass().getPackage().getName();
    String pkg =plugin.getClass().getPackage().getName();

    packages.add(pkg);
    LOG.info("packages={}",packages);
    return StringUtils.toStringArray(packages);
  }

  private String[] getMappingResources() {
    List<String> mappingResources = this.properties.getMappingResources();
    return (!ObjectUtils.isEmpty(mappingResources) ? StringUtils.toStringArray(mappingResources) : null);
  }

  public static Plugin getPlugin(BeanFactory beanFactory){
    return beanFactory.getBean(PluginApplication.BEAN_PLUGIN, Plugin.class);
  }

  @Autowired
  @Override
  public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
    this.beanFactory = beanFactory;
  }
}
