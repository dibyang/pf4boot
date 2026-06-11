package org.springframework.context.support;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginWrapper;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Paths;

import static org.junit.Assert.assertSame;

public class Pf4bootAnnotationConfigApplicationContextTest {

  @Test
  public void exposesInjectedPlugin() throws Exception {
    Pf4bootPlugin plugin = createTestPlugin();

    Pf4bootAnnotationConfigApplicationContext context =
        new Pf4bootAnnotationConfigApplicationContext(new DefaultListableBeanFactory(), plugin);

    assertSame(plugin, context.getPlugin());
    context.close();
  }

  @Test
  public void canSetParentContextAndClose() throws Exception {
    Pf4bootPlugin plugin = createTestPlugin();
    Pf4bootAnnotationConfigApplicationContext pluginContext =
        new Pf4bootAnnotationConfigApplicationContext(new DefaultListableBeanFactory(), plugin);
    AnnotationConfigApplicationContext parentContext = new AnnotationConfigApplicationContext();
    parentContext.refresh();

    pluginContext.setParent(parentContext);

    assertSame(parentContext, pluginContext.getParent());
    pluginContext.close();
    parentContext.close();
  }

  private static Pf4bootPlugin createTestPlugin() {
    DefaultPluginManager pluginManager = new DefaultPluginManager();
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        "annotation-config-test-plugin",
        "annotation-config-test-plugin",
        "dummy.Class",
        "1.0.0",
        "",
        "test",
        "Apache-2.0");
    Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(
        pluginManager,
        descriptor,
        Paths.get("target/annotation-config-plugin"),
        Pf4bootAnnotationConfigApplicationContextTest.class.getClassLoader());
    return new Pf4bootPlugin(wrapper) {};
  }
}

