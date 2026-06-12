package net.xdob.pf4boot.actuate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.Pf4bootPluginWrapper;
import net.xdob.pf4boot.deployment.DeploymentRecord;
import net.xdob.pf4boot.deployment.DeploymentState;
import net.xdob.pf4boot.deployment.DefaultPluginDeploymentRecorder;
import net.xdob.pf4boot.management.PluginManagementMetricsSnapshot;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Pf4bootMetricsTest {

  @Test
  public void countsLoadedStartedAndFailedPlugins() throws Exception {
    Pf4bootPluginManager manager = proxyManager(
        wrapper("started", PluginState.STARTED),
        wrapper("stopped", PluginState.STOPPED),
        wrapper("failed", PluginState.FAILED));

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    new Pf4bootMetrics(manager).bindTo(registry);

    Gauge loadedGauge = registry.find("pf4boot.plugins").gauge();
    Gauge startedGauge = registry.find("pf4boot.plugins.started").gauge();
    Gauge failedGauge = registry.find("pf4boot.plugins.failed").gauge();

    assertEquals(3.0, loadedGauge.value(), 0.0);
    assertEquals(1.0, startedGauge.value(), 0.0);
    assertEquals(1.0, failedGauge.value(), 0.0);
  }

  @Test
  public void exposesDeploymentMetricsWhenProviderIsAvailable() throws Exception {
    Pf4bootPluginManager manager = proxyManager(wrapper("started", PluginState.STARTED));
    DefaultPluginDeploymentRecorder recorder = new DefaultPluginDeploymentRecorder();
    recorder.record(new DeploymentRecord(
        "deployment-1",
        "started",
        DeploymentState.FAILED,
        100,
        150,
        "rolled back",
        null,
        DeploymentRecord.history(DeploymentState.APPLYING, DeploymentState.ROLLING_BACK, DeploymentState.FAILED),
        50,
        "HEALTH_CHECK_FAILED"));

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    new Pf4bootMetrics(manager, recorder).bindTo(registry);

    assertEquals(1.0, registry.find("pf4boot.deployment.total").gauge().value(), 0.0);
    assertEquals(1.0, registry.find("pf4boot.deployment.rollback.total").gauge().value(), 0.0);
    assertEquals(1.0, registry.find("pf4boot.deployment.failed.total").gauge().value(), 0.0);
    assertEquals(50.0, registry.find("pf4boot.deployment.last.duration.millis").gauge().value(), 0.0);
  }

  @Test
  public void exposesManagementMetricsWhenProviderIsAvailable() throws Exception {
    Pf4bootPluginManager manager = proxyManager(wrapper("started", PluginState.STARTED));

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    new Pf4bootMetrics(manager, null, () -> new PluginManagementMetricsSnapshot(5, 2, 1))
        .bindTo(registry);

    assertEquals(5.0, registry.find("pf4boot.management.request.total").gauge().value(), 0.0);
    assertEquals(2.0, registry.find("pf4boot.management.rejected.total").gauge().value(), 0.0);
    assertEquals(1.0, registry.find("pf4boot.management.idempotency.hit.total").gauge().value(), 0.0);
  }

  private Pf4bootPluginManager proxyManager(PluginWrapper... wrappers) {
    List<PluginWrapper> plugins = Arrays.asList(wrappers);
    return (Pf4bootPluginManager) Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class[]{Pf4bootPluginManager.class},
        (proxy, method, args) -> {
          if ("getPlugins".equals(method.getName())) {
            return plugins;
          }
          if (Object.class.equals(method.getDeclaringClass())) {
            switch (method.getName()) {
              case "toString":
                return "test-plugin-manager";
              case "hashCode":
                return System.identityHashCode(proxy);
              case "equals":
                return proxy == args[0];
            }
          }
          if (method.getReturnType().isPrimitive()) {
            if (method.getReturnType().equals(boolean.class)) {
              return false;
            }
            if (method.getReturnType().equals(int.class)) {
              return 0;
            }
            if (method.getReturnType().equals(long.class)) {
              return 0L;
            }
          }
          if (method.getReturnType().equals(Object.class)) {
            return new Object();
          }
          return null;
        });
  }

  private PluginWrapper wrapper(String id, PluginState state) {
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
        id, id, "net.xdob.pf4boot.FakePlugin", "1.0.0", "", "test", "Apache-2.0");
    Pf4bootPluginWrapper wrapper = new Pf4bootPluginWrapper(
        new DefaultPluginManager(), descriptor, fakePath(id), getClass().getClassLoader());
    wrapper.setPluginState(state);
    return wrapper;
  }

  private Path fakePath(String id) {
    return new java.io.File("target", id).toPath().toAbsolutePath();
  }
}
