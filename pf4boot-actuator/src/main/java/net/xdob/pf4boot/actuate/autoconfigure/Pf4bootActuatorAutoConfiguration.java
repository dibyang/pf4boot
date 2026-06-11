package net.xdob.pf4boot.actuate.autoconfigure;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.PluginRuntimeInspector;
import net.xdob.pf4boot.actuate.DefaultPluginRuntimeInspector;
import net.xdob.pf4boot.actuate.Pf4bootMetrics;
import net.xdob.pf4boot.actuate.Pf4bootPluginsEndpoint;
import net.xdob.pf4boot.deployment.PluginDeploymentMetricsProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PF4Boot Actuator 自动配置。
 *
 * <p>该模块独立于默认 starter，只有显式引入 `pf4boot-actuator` 时才注册只读观测能力。</p>
 */
@Configuration
@ConditionalOnClass(Endpoint.class)
@ConditionalOnBean(Pf4bootPluginManager.class)
public class Pf4bootActuatorAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public PluginRuntimeInspector pluginRuntimeInspector(Pf4bootPluginManager pluginManager) {
    return new DefaultPluginRuntimeInspector(pluginManager);
  }

  @Bean
  @ConditionalOnMissingBean
  public Pf4bootPluginsEndpoint pf4bootPluginsEndpoint(PluginRuntimeInspector pluginRuntimeInspector) {
    return new Pf4bootPluginsEndpoint(pluginRuntimeInspector);
  }

  @Bean
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnMissingBean
  public Pf4bootMetrics pf4bootMetrics(
      Pf4bootPluginManager pluginManager,
      ObjectProvider<PluginDeploymentMetricsProvider> deploymentMetricsProvider) {
    return new Pf4bootMetrics(pluginManager, deploymentMetricsProvider.getIfAvailable());
  }
}
