package net.xdob.pf4boot.management;

/**
 * 插件管理接口指标快照提供者。
 *
 * <p>该 SPI 只暴露管理面请求、拒绝和幂等命中的只读计数，供 actuator 或宿主自定义
 * 观测组件读取。实现类不得在 {@link #snapshot()} 中触发插件生命周期或部署变更。</p>
 */
public interface PluginManagementMetricsProvider {

  /**
   * 返回当前管理接口指标快照。
   *
   * @return 管理接口指标快照，不能返回 {@code null}
   */
  PluginManagementMetricsSnapshot snapshot();
}
