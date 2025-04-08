package net.xdob.pf4boot.jpa;

import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;
import org.springframework.orm.jpa.persistenceunit.SmartPersistenceUnitInfo;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceUnitInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Pf4bootJpaPersistenceProvider extends HibernatePersistenceProvider {
  public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map properties) {
    final List<String> mergedClassesAndPackages = new ArrayList<>(info.getManagedClassNames());
    if (info instanceof SmartPersistenceUnitInfo) {
      mergedClassesAndPackages.addAll(((SmartPersistenceUnitInfo) info).getManagedPackages());
    }
    return new EntityManagerFactoryBuilderImpl(
        new PersistenceUnitInfoDescriptor(info) {
          @Override
          public List<String> getManagedClassNames() {
            return mergedClassesAndPackages;
          }
        }, properties).build();
  }

  @Override
  public EntityManagerFactory createEntityManagerFactory(String persistenceUnitName, Map properties) {
    return super.createEntityManagerFactory(persistenceUnitName, properties);
  }

}
