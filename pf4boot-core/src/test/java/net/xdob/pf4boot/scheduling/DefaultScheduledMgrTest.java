package net.xdob.pf4boot.scheduling;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginWrapper;
import org.junit.After;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultScheduledMgrTest {

  private final List<ConfigurableApplicationContext> pluginContexts = new ArrayList<>();

  @After
  public void tearDown() {
    for (ConfigurableApplicationContext context : pluginContexts) {
      context.close();
    }
  }

  @Test
  public void registerAndUnregisterScheduledTasksForSinglePlugin() throws Exception {
    DefaultScheduledMgr scheduledMgr = new DefaultScheduledMgr();
    Pf4bootPlugin plugin = createPlugin("plugin-a");

    scheduledMgr.registerScheduledTasks(plugin);
    assertEquals(1, scheduledMgr.getScheduledTaskCount("plugin-a"));

    scheduledMgr.unregisterScheduledTasks(plugin);
    assertEquals(0, scheduledMgr.getScheduledTaskCount("plugin-a"));
  }

  @Test
  public void destroyUnregistersAllScheduledTasks() throws Exception {
    DefaultScheduledMgr scheduledMgr = new DefaultScheduledMgr();
    Pf4bootPlugin first = createPlugin("plugin-a");
    Pf4bootPlugin second = createPlugin("plugin-b");

    scheduledMgr.registerScheduledTasks(first);
    scheduledMgr.registerScheduledTasks(second);
    assertEquals(1, scheduledMgr.getScheduledTaskCount("plugin-a"));
    assertEquals(1, scheduledMgr.getScheduledTaskCount("plugin-b"));

    scheduledMgr.destroy();

    assertEquals(0, scheduledMgr.getScheduledTaskCount("plugin-a"));
    assertEquals(0, scheduledMgr.getScheduledTaskCount("plugin-b"));
  }

  @Test
  public void drainWaitsForRunningScheduledTask() throws Exception {
    BlockingScheduledService.entered = new CountDownLatch(1);
    BlockingScheduledService.release = new CountDownLatch(1);
    DefaultScheduledMgr scheduledMgr = new DefaultScheduledMgr();
    Pf4bootPlugin plugin = createPlugin("plugin-drain", BlockingScheduledConfig.class);

    scheduledMgr.registerScheduledTasks(plugin);
    assertTrue(BlockingScheduledService.entered.await(2, TimeUnit.SECONDS));
    scheduledMgr.beginDrain(Collections.singletonList("plugin-drain"));

    assertFalse(scheduledMgr.awaitDrain(Collections.singletonList("plugin-drain"), 30));

    BlockingScheduledService.release.countDown();

    assertTrue(scheduledMgr.awaitDrain(Collections.singletonList("plugin-drain"), 1000));
    scheduledMgr.unregisterScheduledTasks(plugin);
  }

  private Pf4bootPlugin createPlugin(String pluginId) throws Exception {
    return createPlugin(pluginId, ScheduledService.class);
  }

  private Pf4bootPlugin createPlugin(String pluginId, Class<?> configClass) throws Exception {
    Path pluginPath = Files.createTempDirectory("pf4boot-scheduled-plugin-" + pluginId);
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        pluginId,
        pluginId,
        TestPlugin.class.getName(),
        "1.0.0",
        "",
        "test",
        "Apache-2.0");
    PluginWrapper wrapper = new Pf4bootPluginWrapper(
        new DefaultPluginManager(),
        descriptor,
        pluginPath,
        TestPlugin.class.getClassLoader());

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(configClass);
    context.refresh();
    pluginContexts.add(context);

    return new TestPlugin(wrapper, context);
  }

  private static class TestPlugin extends Pf4bootPlugin {
    private final ConfigurableApplicationContext pluginContext;

    private TestPlugin(PluginWrapper wrapper, ConfigurableApplicationContext pluginContext) {
      super(wrapper);
      this.pluginContext = pluginContext;
    }

    @Override
    public ConfigurableApplicationContext getPluginContext() {
      return pluginContext;
    }
  }

  @Component
  public static class ScheduledService {

    @Scheduled(fixedDelay = 1000000)
    public void tick() {
    }
  }

  @Component
  public static class BlockingScheduledConfig extends BlockingScheduledService {
  }

  public static class BlockingScheduledService {
    static CountDownLatch entered = new CountDownLatch(1);
    static CountDownLatch release = new CountDownLatch(1);

    @Scheduled(fixedDelay = 1)
    public void tick() throws InterruptedException {
      entered.countDown();
      release.await(2, TimeUnit.SECONDS);
    }
  }
}
