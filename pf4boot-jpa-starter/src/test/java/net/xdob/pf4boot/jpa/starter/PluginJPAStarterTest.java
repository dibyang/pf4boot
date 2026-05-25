package net.xdob.pf4boot.jpa.starter;

import net.xdob.pf4boot.PluginApplication;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.persistence.EntityManagerFactory;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PluginJPAStarterTest {

  @Test
  public void jpaStarterDoesNotCreateEntityManagerFactoryWhenPropertyIsMissing() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(PluginJPAStarter.class);
      context.refresh();

      assertEquals(0, context.getBeanNamesForType(PluginJPAStarter.class).length);
      assertEquals(0, context.getBeanNamesForType(EntityManagerFactory.class).length);
    } finally {
      context.close();
    }
  }

  @Test
  public void pluginMainClassPackageIsUsedAsFallbackScanPackage() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.registerBean(PluginApplication.BEAN_PLUGIN, Plugin.class, TestPlugin::new);
      context.refresh();

      ExposedPluginJPAStarter starter = new ExposedPluginJPAStarter();
      starter.setBeanFactory(context.getBeanFactory());

      assertArrayEquals(
          new String[]{TestPlugin.class.getPackage().getName()},
          starter.exposedPackagesToScan());
    } finally {
      context.close();
    }
  }

  private static class ExposedPluginJPAStarter extends PluginJPAStarter {
    ExposedPluginJPAStarter() {
      super(null, new JpaProperties(), new HibernateProperties(), Collections.emptyList());
    }

    String[] exposedPackagesToScan() {
      return getPackagesToScan();
    }
  }

  public static class TestPlugin extends Plugin {
    public TestPlugin() {
      super(new PluginWrapper(
          new DefaultPluginManager(Paths.get("jpa-test-root")),
          new DefaultPluginDescriptor(
              "jpa-test", "jpa-test", TestPlugin.class.getName(), "1.0.0", "", "test", "Apache-2.0"),
          Paths.get("jpa-test"),
          TestPlugin.class.getClassLoader()));
    }
  }
}
