package com.ls.pf4boot.spring.boot;

import com.ls.pf4boot.Pf4bootPlugin;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.spi.PersistenceUnitInfo;
import java.util.Collection;
import java.util.List;

/**
 * Pf4bootAnnotationConfigApplicationContext
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootAnnotationConfigApplicationContext extends AnnotationConfigApplicationContext {

  private final Pf4bootPlugin plugin;

  public Pf4bootAnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory, Pf4bootPlugin plugin) {
    super(beanFactory);
    this.plugin = plugin;
  }

  @Override
  public void setParent(ApplicationContext parent) {
    super.setParent(parent);
  }

  protected void initApplicationEventMulticaster(){
    super.initApplicationEventMulticaster();
  }


  @Override
  protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = beanFactory.getBean(LocalContainerEntityManagerFactoryBean.class);
    if(entityManagerFactoryBean!=null){
      String[] packagesToScan = this.getPackagesToScan(this);
      System.out.println("packagesToScan = " + packagesToScan);

    }
    super.finishBeanFactoryInitialization(beanFactory);


  }

  protected String[] getPackagesToScan(BeanFactory beanFactory) {
    List<String> packages = EntityScanPackages.get(beanFactory).getPackageNames();
    if (packages.isEmpty() && AutoConfigurationPackages.has(beanFactory)) {
      packages = AutoConfigurationPackages.get(beanFactory);
    }
    return StringUtils.toStringArray(packages);
  }

  @Override
  public void close() {

    super.close();
  }
}
