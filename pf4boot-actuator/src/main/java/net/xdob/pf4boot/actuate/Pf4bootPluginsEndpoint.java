package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.PluginRuntimeInspector;
import net.xdob.pf4boot.modal.PluginRuntimeSnapshot;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.List;

/**
 * PF4Boot 插件只读观测端点。
 *
 * <p>端点只返回插件运行时快照，不提供插件启停、重载或删除操作。</p>
 */
@Endpoint(id = "pf4bootplugins")
public class Pf4bootPluginsEndpoint {

  private final PluginRuntimeInspector pluginRuntimeInspector;

  public Pf4bootPluginsEndpoint(PluginRuntimeInspector pluginRuntimeInspector) {
    this.pluginRuntimeInspector = pluginRuntimeInspector;
  }

  /**
   * 读取当前插件运行时快照。
   */
  @ReadOperation
  public List<PluginRuntimeSnapshot> plugins() {
    return pluginRuntimeInspector.snapshots();
  }
}
