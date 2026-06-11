package net.xdob.pf4boot.actuate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.deployment.PluginDeploymentMetricsProvider;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * PF4Boot 插件 Micrometer 指标。
 *
 * <p>指标只读取插件管理器当前状态，不触发插件生命周期变更。</p>
 */
public class Pf4bootMetrics implements MeterBinder {

  private final Pf4bootPluginManager pluginManager;
  private final PluginDeploymentMetricsProvider deploymentMetricsProvider;

  public Pf4bootMetrics(Pf4bootPluginManager pluginManager) {
    this(pluginManager, null);
  }

  public Pf4bootMetrics(
      Pf4bootPluginManager pluginManager,
      PluginDeploymentMetricsProvider deploymentMetricsProvider) {
    this.pluginManager = pluginManager;
    this.deploymentMetricsProvider = deploymentMetricsProvider;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("pf4boot.plugins", pluginManager, manager -> manager.getPlugins().size())
        .description("PF4Boot loaded plugin count")
        .register(registry);
    Gauge.builder("pf4boot.plugins.started", pluginManager, manager -> countByState(manager, PluginState.STARTED))
        .description("PF4Boot started plugin count")
        .register(registry);
    Gauge.builder("pf4boot.plugins.failed", pluginManager, manager -> countByState(manager, PluginState.FAILED))
        .description("PF4Boot failed plugin count")
        .register(registry);
    if (deploymentMetricsProvider != null) {
      Gauge.builder("pf4boot.deployment.total", deploymentMetricsProvider,
              provider -> provider.snapshot().getDeploymentTotal())
          .description("PF4Boot plugin deployment total")
          .register(registry);
      Gauge.builder("pf4boot.deployment.rollback.total", deploymentMetricsProvider,
              provider -> provider.snapshot().getRollbackTotal())
          .description("PF4Boot plugin deployment rollback total")
          .register(registry);
      Gauge.builder("pf4boot.deployment.failed.total", deploymentMetricsProvider,
              provider -> provider.snapshot().getFailedTotal())
          .description("PF4Boot plugin deployment failed total")
          .register(registry);
      Gauge.builder("pf4boot.deployment.last.duration.millis", deploymentMetricsProvider,
              provider -> provider.snapshot().getLastDurationMillis())
          .description("PF4Boot last plugin deployment duration in milliseconds")
          .register(registry);
    }
  }

  private static long countByState(Pf4bootPluginManager pluginManager, PluginState state) {
    long count = 0;
    for (PluginWrapper pluginWrapper : pluginManager.getPlugins()) {
      if (state.equals(pluginWrapper.getPluginState())) {
        count++;
      }
    }
    return count;
  }
}
