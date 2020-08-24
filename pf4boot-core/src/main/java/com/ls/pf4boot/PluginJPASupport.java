package com.ls.pf4boot;

import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * PluginJPASupport
 *
 * @author yangzj
 * @version 1.0
 */

@ConditionalOnClass({ LocalContainerEntityManagerFactoryBean.class, EntityManager.class, SessionImplementor.class })
@EnableConfigurationProperties({JpaProperties.class,HibernateProperties.class})
@AutoConfigureAfter({ DataSourceAutoConfiguration.class })
public class PluginJPASupport {
  @Autowired
  private EntityManagerFactoryBuilder entityManagerFactoryBuilder;

  @Autowired
  private DataSource dataSource;

  @Autowired
  private JpaProperties properties;

  @Autowired
  private HibernateProperties hibernateProperties;

  @Autowired
  private BeanFactory beanFactory;


  protected Map<String, Object> getVendorProperties() {
    Supplier<String> defaultDdlMode = () -> "update";
    return new LinkedHashMap<>(this.hibernateProperties
        .determineHibernateProperties(properties.getProperties(), new HibernateSettings()
            .ddlAuto(defaultDdlMode)));
  }

  @Bean
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder factoryBuilder) {
    return factoryBuilder.dataSource(this.dataSource).mappingResources(getMappingResources())
        .properties(getVendorProperties())
        .packages("com.ls.demo","com.ls.plugin1.dao").build();
  }

  @Bean
  public PlatformTransactionManager transactionManager() {
    return new JpaTransactionManager();
  }


  protected String[] getPackagesToScan() {
    List<String> packages = EntityScanPackages.get(this.beanFactory).getPackageNames();
    if (packages.isEmpty() && AutoConfigurationPackages.has(this.beanFactory)) {
      packages = AutoConfigurationPackages.get(this.beanFactory);
    }
    return StringUtils.toStringArray(packages);
  }

  private String[] getMappingResources() {
    List<String> mappingResources = this.properties.getMappingResources();
    return (!ObjectUtils.isEmpty(mappingResources) ? StringUtils.toStringArray(mappingResources) : null);
  }
}
