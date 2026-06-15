package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.jpa.domain.JpaDomainDescriptor;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadMode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadState;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadFailureCode;
import net.xdob.pf4boot.jpa.starter.JpaPluginBinding;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginDependency;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class DefaultJpaDomainReloadServiceTest {

  @Test
  public void reloadStopsConsumersRestartsProviderAndStartsConsumers() {
    Fixture fixture = new Fixture();
    try {
      fixture.addPlugin("provider-demo", PluginState.STARTED);
      fixture.addPlugin("sample-user-book-service", PluginState.STARTED, "provider-demo");
      fixture.addPlugin("sample-workflow", PluginState.STARTED, "sample-user-book-service");
      fixture.registerDescriptor("demo", "provider-demo", true);
      fixture.registry.register(binding("sample-user-book-service", "demo"));
      fixture.registry.register(binding("sample-workflow", "demo"));

      JpaDomainReloadRecord record = fixture.service().reload(request("demo", "key-1"));

      assertEquals(JpaDomainReloadState.SUCCEEDED, record.getState());
      assertEquals(
          "stop:sample-workflow,stop:sample-user-book-service,stop:provider-demo,start:provider-demo,start:sample-user-book-service,start:sample-workflow",
          String.join(",", fixture.operations));
    } finally {
      fixture.close();
    }
  }

  @Test
  public void reloadReplaysSameIdempotencyKey() {
    Fixture fixture = new Fixture();
    try {
      fixture.addPlugin("provider-demo", PluginState.STARTED);
      fixture.addPlugin("sample-user-book-service", PluginState.STARTED, "provider-demo");
      fixture.registerDescriptor("demo", "provider-demo", true);
      fixture.registry.register(binding("sample-user-book-service", "demo"));

      DefaultJpaDomainReloadService service = fixture.service();
      JpaDomainReloadRecord first = service.reload(request("demo", "same-key"));
      JpaDomainReloadRecord second = service.reload(request("demo", "same-key"));

      assertSame(first, second);
      assertEquals(4, fixture.operations.size());
    } finally {
      fixture.close();
    }
  }

  @Test
  public void reloadDoesNotExecuteWhenConfiguredDisabledEvenIfRequestAsksExecuteMode() {
    Fixture fixture = new Fixture();
    try {
      fixture.addPlugin("provider-demo", PluginState.STARTED);
      fixture.addPlugin("sample-user-book-service", PluginState.STARTED, "provider-demo");
      fixture.registerDescriptor("demo", "provider-demo", true);
      fixture.registry.register(binding("sample-user-book-service", "demo"));

      JpaDomainReloadRequest request = request("demo", "disabled-key");
      request.setMode(JpaDomainReloadMode.STOP_CONSUMERS_AND_REBUILD);
      JpaDomainReloadRecord record = fixture.service(JpaDomainReloadMode.DISABLED).reload(request);

      assertEquals(JpaDomainReloadState.FAILED, record.getState());
      assertEquals(JpaDomainReloadFailureCode.RELOAD_DISABLED, record.getFailureCode());
      assertEquals(0, fixture.operations.size());
    } finally {
      fixture.close();
    }
  }

  @Test
  public void reloadRejectsProviderReplacementPathWithoutExecuting() {
    Fixture fixture = new Fixture();
    try {
      fixture.addPlugin("provider-demo", PluginState.STARTED);
      fixture.registerDescriptor("demo", "provider-demo", true);

      JpaDomainReloadRequest request = request("demo", "replace-path-key");
      request.setProviderReplacementPath("plugins/new-provider.zip");
      JpaDomainReloadRecord record = fixture.service().reload(request);

      assertEquals(JpaDomainReloadState.FAILED, record.getState());
      assertEquals(JpaDomainReloadFailureCode.UNSUPPORTED_REPLACEMENT_PATH, record.getFailureCode());
      assertEquals(0, fixture.operations.size());
    } finally {
      fixture.close();
    }
  }

  @Test
  public void reloadRejectsInvalidRequestsBeforeExecution() {
    Fixture fixture = new Fixture();
    try {
      expectIllegalArgument(fixture.service(), request("", "key"), "domainId");
      expectIllegalArgument(fixture.service(), request("demo", null), "idempotencyKey");
      JpaDomainReloadRequest longReason = request("demo", "long-reason-key");
      longReason.setReason(repeat("x", 513));
      expectIllegalArgument(fixture.service(), longReason, "reason");
      assertEquals(0, fixture.operations.size());
    } finally {
      fixture.close();
    }
  }

  @Test
  public void reloadRetriesProviderStartOnce() {
    Fixture fixture = new Fixture();
    try {
      fixture.addPlugin("provider-demo", PluginState.STARTED);
      fixture.addPlugin("sample-user-book-service", PluginState.STARTED, "provider-demo");
      fixture.registerDescriptor("demo", "provider-demo", true);
      fixture.registry.register(binding("sample-user-book-service", "demo"));
      fixture.startFailures.put("provider-demo", 1);

      JpaDomainReloadRecord record = fixture.service().reload(request("demo", "retry-key"));

      assertEquals(JpaDomainReloadState.SUCCEEDED, record.getState());
      assertEquals(
          "stop:sample-user-book-service,stop:provider-demo,start-failed:provider-demo,start:provider-demo,start:sample-user-book-service",
          String.join(",", fixture.operations));
    } finally {
      fixture.close();
    }
  }

  @Test
  public void reloadFailsWhenProviderExportsRemainAfterStop() {
    Fixture fixture = new Fixture();
    try {
      fixture.addPlugin("provider-demo", PluginState.STARTED);
      fixture.registerDescriptor("demo", "provider-demo", true);
      fixture.keepExportsAfterStop = true;

      JpaDomainReloadRecord record = fixture.service().reload(request("demo", "leaked-export-key"));

      assertEquals(JpaDomainReloadState.MANUAL_INTERVENTION_REQUIRED, record.getState());
      assertEquals(JpaDomainReloadFailureCode.PROVIDER_STOP_FAILED, record.getFailureCode());
      assertEquals("stop:provider-demo", String.join(",", fixture.operations));
    } finally {
      fixture.close();
    }
  }

  private static void expectIllegalArgument(
      DefaultJpaDomainReloadService service,
      JpaDomainReloadRequest request,
      String expectedMessagePart) {
    try {
      service.reload(request);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      if (e.getMessage() == null || !e.getMessage().contains(expectedMessagePart)) {
        throw e;
      }
    }
  }

  private static String repeat(String value, int count) {
    StringBuilder builder = new StringBuilder(value.length() * count);
    for (int i = 0; i < count; i++) {
      builder.append(value);
    }
    return builder.toString();
  }

  private static JpaDomainReloadRequest request(String domainId, String idempotencyKey) {
    JpaDomainReloadRequest request = new JpaDomainReloadRequest();
    request.setDomainId(domainId);
    request.setIdempotencyKey(idempotencyKey);
    return request;
  }

  private static JpaPluginBinding binding(String pluginId, String domainId) {
    return new JpaPluginBinding(
        pluginId,
        Pf4bootJpaProperties.Mode.SHARED,
        domainId,
        null,
        null,
        null,
        Collections.emptyList(),
        true);
  }

  private static class Fixture {
    private final AnnotationConfigApplicationContext platform = new AnnotationConfigApplicationContext();
    private final Map<String, PluginWrapper> plugins = new LinkedHashMap<>();
    private final List<String> operations = new ArrayList<>();
    private final DefaultJpaPluginBindingRegistry registry = new DefaultJpaPluginBindingRegistry();
    private final Map<String, Integer> startFailures = new LinkedHashMap<>();
    private boolean keepExportsAfterStop;

    Fixture() {
      platform.refresh();
    }

    DefaultJpaDomainReloadService service() {
      return service(JpaDomainReloadMode.STOP_CONSUMERS_AND_REBUILD);
    }

    DefaultJpaDomainReloadService service(JpaDomainReloadMode mode) {
      Pf4bootJpaProperties properties = new Pf4bootJpaProperties();
      properties.getDomainReload().setMode(mode);
      DefaultJpaDomainReloadPlanService planService =
          new DefaultJpaDomainReloadPlanService(manager(), registry, properties);
      return new DefaultJpaDomainReloadService(
          manager(),
          planService,
          new InMemoryJpaDomainReloadRecordRepository(100),
          properties);
    }

    void addPlugin(String pluginId, PluginState state, String... dependencies) {
      DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
          pluginId,
          pluginId,
          "net.xdob.TestPlugin",
          "1.0.0",
          "",
          "test",
          "Apache-2.0");
      for (String dependency : dependencies) {
        descriptor.addDependency(new PluginDependency(dependency));
      }
      PluginWrapper wrapper = new PluginWrapper(
          new DefaultPluginManager(Paths.get("build/test-plugins")),
          descriptor,
          Paths.get("build/test-plugins").resolve(pluginId),
          DefaultJpaDomainReloadServiceTest.class.getClassLoader());
      wrapper.setPluginState(state);
      plugins.put(pluginId, wrapper);
    }

    void registerDescriptor(String domainId, String providerPluginId, boolean ready) {
      registerExport("domain." + domainId + ".dataSource");
      registerExport("domain." + domainId + ".entityManagerFactory");
      registerExport("domain." + domainId + ".transactionManager");
      platform.getBeanFactory().registerSingleton(
          "domain." + domainId + ".descriptor",
          new JpaDomainDescriptor(
              domainId,
              providerPluginId,
              new String[]{"net.xdob.sample." + domainId},
              "domain." + domainId + ".dataSource",
              "domain." + domainId + ".entityManagerFactory",
              "domain." + domainId + ".transactionManager",
              ready,
              1L));
    }

    private void registerExport(String beanName) {
      if (!platform.getBeanFactory().containsSingleton(beanName)) {
        platform.getBeanFactory().registerSingleton(beanName, new Object());
      }
    }

    Pf4bootPluginManager manager() {
      return (Pf4bootPluginManager) Proxy.newProxyInstance(
          DefaultJpaDomainReloadServiceTest.class.getClassLoader(),
          new Class[]{Pf4bootPluginManager.class},
          (proxy, method, args) -> {
            if ("getPlugins".equals(method.getName())) {
              return new ArrayList<>(plugins.values());
            }
            if ("getPlugin".equals(method.getName())) {
              return plugins.get((String) args[0]);
            }
            if ("getPlatformContext".equals(method.getName())) {
              return platform;
            }
            if ("stopPlugin".equals(method.getName())) {
              PluginWrapper wrapper = plugins.get((String) args[0]);
              wrapper.setPluginState(PluginState.STOPPED);
              if ("provider-demo".equals(args[0])) {
                if (!keepExportsAfterStop) {
                  platform.getDefaultListableBeanFactory().destroySingleton("domain.demo.dataSource");
                  platform.getDefaultListableBeanFactory().destroySingleton("domain.demo.entityManagerFactory");
                  platform.getDefaultListableBeanFactory().destroySingleton("domain.demo.transactionManager");
                  platform.getDefaultListableBeanFactory().destroySingleton("domain.demo.descriptor");
                }
              }
              operations.add("stop:" + args[0]);
              return PluginState.STOPPED;
            }
            if ("startPlugin".equals(method.getName())) {
              Integer remainingFailures = startFailures.get((String) args[0]);
              if (remainingFailures != null && remainingFailures > 0) {
                startFailures.put((String) args[0], remainingFailures - 1);
                operations.add("start-failed:" + args[0]);
                return PluginState.FAILED;
              }
              PluginWrapper wrapper = plugins.get((String) args[0]);
              wrapper.setPluginState(PluginState.STARTED);
              if ("provider-demo".equals(args[0])
                  && !platform.getBeanFactory().containsSingleton("domain.demo.descriptor")) {
                registerDescriptor("demo", "provider-demo", true);
              }
              operations.add("start:" + args[0]);
              return PluginState.STARTED;
            }
            if ("toString".equals(method.getName())) {
              return "TestPf4bootPluginManager";
            }
            Class<?> returnType = method.getReturnType();
            if (Boolean.TYPE == returnType) {
              return false;
            }
            if (Integer.TYPE == returnType) {
              return 0;
            }
            return null;
          });
    }

    void close() {
      platform.close();
    }
  }
}
