package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.jpa.domain.JpaDomainDescriptor;
import net.xdob.pf4boot.jpa.reload.JpaDomainConsumerDetection;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadFailureCode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadMode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlan;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultJpaDomainReloadPlanServiceTest {

  @Test
  public void disabledModeReturnsDescriptorAndReloadDisabledBlocker() {
    Fixture fixture = new Fixture();
    try {
      fixture.addPlugin("provider-demo", PluginState.STARTED);
      fixture.registerDescriptor("demo", "provider-demo", true);

      JpaDomainReloadPlan plan = fixture.service(JpaDomainReloadMode.DISABLED).plan(request("demo"));

      assertEquals("provider-demo", plan.getProviderPluginId());
      assertFalse(plan.isExecutable());
      assertTrue(hasBlocker(plan, JpaDomainReloadFailureCode.RELOAD_DISABLED));
    } finally {
      fixture.close();
    }
  }

  @Test
  public void planOnlyIdentifiesExactConsumerInferredConsumerAndUnaffectedPlugin() {
    Fixture fixture = new Fixture();
    try {
      fixture.addPlugin("provider-demo", PluginState.STARTED);
      fixture.addPlugin("sample-user-book-service", PluginState.STARTED, "provider-demo");
      fixture.addPlugin("sample-workflow", PluginState.STARTED, "sample-user-book-service");
      fixture.addPlugin("sample-unrelated-service", PluginState.STARTED);
      fixture.registerDescriptor("demo", "provider-demo", true);
      fixture.registry.register(binding("sample-user-book-service", "demo"));

      JpaDomainReloadPlan plan = fixture.service(JpaDomainReloadMode.PLAN_ONLY).plan(request("demo"));

      assertEquals(1, plan.getConsumers().size());
      assertEquals(JpaDomainConsumerDetection.EXACT_BINDING, plan.getConsumers().get(0).getDetection());
      assertEquals(1, plan.getInferredConsumers().size());
      assertEquals("sample-workflow", plan.getInferredConsumers().get(0).getPluginId());
      assertEquals(Collections.singletonList("sample-user-book-service"), plan.getStopOrder());
      assertEquals(Collections.singletonList("sample-user-book-service"), plan.getStartOrder());
      assertEquals(Collections.singletonList("sample-unrelated-service"), plan.getUnaffectedPlugins());
      assertTrue(hasBlocker(plan, JpaDomainReloadFailureCode.PLAN_ONLY_MODE));
      assertTrue(hasBlocker(plan, JpaDomainReloadFailureCode.INFERRED_CONSUMER_PRESENT));
    } finally {
      fixture.close();
    }
  }

  @Test
  public void missingDescriptorReturnsDomainNotFoundWithoutMutationPlan() {
    Fixture fixture = new Fixture();
    try {
      fixture.addPlugin("sample-unrelated-service", PluginState.STARTED);

      JpaDomainReloadPlan plan = fixture.service(JpaDomainReloadMode.PLAN_ONLY).plan(request("missing"));

      assertFalse(plan.isExecutable());
      assertTrue(hasBlocker(plan, JpaDomainReloadFailureCode.DOMAIN_NOT_FOUND));
      assertEquals(Collections.singletonList("sample-unrelated-service"), plan.getUnaffectedPlugins());
    } finally {
      fixture.close();
    }
  }

  private static JpaDomainReloadRequest request(String domainId) {
    JpaDomainReloadRequest request = new JpaDomainReloadRequest();
    request.setDomainId(domainId);
    return request;
  }

  private static boolean hasBlocker(JpaDomainReloadPlan plan, JpaDomainReloadFailureCode code) {
    return plan.getBlockers().stream().anyMatch(blocker -> code == blocker.getCode());
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
    private final DefaultJpaPluginBindingRegistry registry = new DefaultJpaPluginBindingRegistry();

    Fixture() {
      platform.refresh();
    }

    DefaultJpaDomainReloadPlanService service(JpaDomainReloadMode mode) {
      Pf4bootJpaProperties properties = new Pf4bootJpaProperties();
      properties.getDomainReload().setMode(mode);
      return new DefaultJpaDomainReloadPlanService(manager(), registry, properties);
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
          DefaultJpaDomainReloadPlanServiceTest.class.getClassLoader());
      wrapper.setPluginState(state);
      plugins.put(pluginId, wrapper);
    }

    void registerDescriptor(String domainId, String providerPluginId, boolean ready) {
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

    Pf4bootPluginManager manager() {
      return (Pf4bootPluginManager) Proxy.newProxyInstance(
          DefaultJpaDomainReloadPlanServiceTest.class.getClassLoader(),
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
