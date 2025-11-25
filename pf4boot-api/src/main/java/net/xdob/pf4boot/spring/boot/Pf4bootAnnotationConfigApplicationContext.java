package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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

  public Pf4bootPlugin getPlugin() {
    return plugin;
  }

  @Override
  public void setParent(ApplicationContext parent) {
    super.setParent(parent);
  }

  protected void initApplicationEventMulticaster(){
    super.initApplicationEventMulticaster();
  }

  @Override
  public void close() {

    super.close();
  }

}
