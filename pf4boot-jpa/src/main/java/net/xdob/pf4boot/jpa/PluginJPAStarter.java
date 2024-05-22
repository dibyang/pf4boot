package net.xdob.pf4boot.jpa;

import com.google.common.collect.Sets;
import net.xdob.pf4boot.PluginApplication;
import net.xdob.pf4boot.PluginHandler;
import org.hibernate.engine.spi.SessionImplementor;
import org.pf4j.Plugin;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.boot.jdbc.SchemaManagementProvider;
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
import java.util.Set;
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
public class PluginJPAStarter {
  private final HibernateDefaultDdlAutoProvider defaultDdlAutoProvider;

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

  public DataSource getDataSource() {
    return dataSource;
  }

  public PluginJPAStarter(ObjectProvider<SchemaManagementProvider> providers) {
    this.defaultDdlAutoProvider = new HibernateDefaultDdlAutoProvider(providers);
  }

  protected Map<String, Object> getVendorProperties() {
    Supplier<String> defaultDdlMode = () -> this.defaultDdlAutoProvider.getDefaultDdlAuto(getDataSource());
    return new LinkedHashMap<>(this.hibernateProperties
        .determineHibernateProperties(properties.getProperties(), new HibernateSettings()
            .ddlAuto(defaultDdlMode)));
  }

  @Bean
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder factoryBuilder) {
    return factoryBuilder.dataSource(this.dataSource).mappingResources(getMappingResources())
        .properties(getVendorProperties())
        .packages(getPackagesToScan()).build();
  }

  @Bean
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

    String pkg = ((PluginHandler)plugin).getPlugin().getClass().getPackage().getName();
    //String pkg =plugin.getClass().getPackage().getName();

    packages.add(pkg);
    return StringUtils.toStringArray(packages);
  }

  private String[] getMappingResources() {
    List<String> mappingResources = this.properties.getMappingResources();
    return (!ObjectUtils.isEmpty(mappingResources) ? StringUtils.toStringArray(mappingResources) : null);
  }

  public static Plugin getPlugin(BeanFactory beanFactory){
    return beanFactory.getBean(PluginApplication.BEAN_PLUGIN, Plugin.class);
  }
}
