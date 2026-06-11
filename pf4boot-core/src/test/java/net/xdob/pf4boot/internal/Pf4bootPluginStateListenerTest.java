package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPluginWrapper;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginStateEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.PayloadApplicationEvent;

import java.nio.file.Paths;

import static org.junit.Assert.assertSame;

public class Pf4bootPluginStateListenerTest {

  @Test
  public void publishesPluginStateEventToApplicationContext() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    PluginStateEventHolder holder = new PluginStateEventHolder();
    context.addApplicationListener(holder);
    context.refresh();

    PluginStateEvent event = new PluginStateEvent(
        new DefaultPluginManager(),
        createStartedPluginWrapper(),
        PluginState.STARTED);

    new Pf4bootPluginStateListener(context).pluginStateChanged(event);

    assertSame(event, holder.event);
    context.close();
  }

  private static net.xdob.pf4boot.Pf4bootPluginWrapper createStartedPluginWrapper() {
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        "state-listener-plugin",
        "state-listener-plugin",
        "dummy.Class",
        "1.0.0",
        "",
        "test",
        "Apache-2.0");
    return new Pf4bootPluginWrapper(
        new DefaultPluginManager(),
        descriptor,
        Paths.get("target/state-listener-plugin"),
        Pf4bootPluginStateListenerTest.class.getClassLoader());
  }

  private static class PluginStateEventHolder implements ApplicationListener {
    PluginStateEvent event;

    @Override
    public void onApplicationEvent(org.springframework.context.ApplicationEvent event) {
      if (event instanceof PayloadApplicationEvent) {
        Object payload = ((PayloadApplicationEvent) event).getPayload();
        if (payload instanceof PluginStateEvent) {
          this.event = (PluginStateEvent) payload;
        }
      }
    }
  }
}
