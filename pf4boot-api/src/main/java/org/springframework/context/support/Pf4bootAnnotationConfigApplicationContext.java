package org.springframework.context.support;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.NativeDetector;

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
//
//	protected void finishRefresh() {
//		// Clear context-level resource caches (such as ASM metadata from scanning).
//		clearResourceCaches();
//
//		// Initialize lifecycle processor for this context.
//		initLifecycleProcessor();
//
//		// Propagate refresh to lifecycle processor first.
//		LifecycleProcessor lifecycleProcessor = this.getBeanFactory().getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
//		lifecycleProcessor.onRefresh();
//
//		// Publish the final event.
//		publishEvent(new ContextRefreshedEvent(this));
//
//
//
//	}


}
