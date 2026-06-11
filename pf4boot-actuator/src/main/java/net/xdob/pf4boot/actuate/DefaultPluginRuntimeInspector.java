package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.Pf4bootPluginWrapper;
import net.xdob.pf4boot.PluginRuntimeInspector;
import net.xdob.pf4boot.modal.PluginError;
import net.xdob.pf4boot.modal.PluginRuntimeSnapshot;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 `Pf4bootPluginManager` 的默认运行时快照实现。
 *
 * <p>该实现只读取 PF4J 已有状态，不触发插件生命周期变更。</p>
 */
public class DefaultPluginRuntimeInspector implements PluginRuntimeInspector {

  private final Pf4bootPluginManager pluginManager;

  public DefaultPluginRuntimeInspector(Pf4bootPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @Override
  public List<PluginRuntimeSnapshot> snapshots() {
    List<PluginRuntimeSnapshot> snapshots = new ArrayList<>();
    for (PluginWrapper pluginWrapper : pluginManager.getPlugins()) {
      snapshots.add(snapshotOf(pluginWrapper, pluginManager.getPluginErrors(pluginWrapper.getPluginId())));
    }
    return snapshots;
  }

  PluginRuntimeSnapshot snapshotOf(PluginWrapper pluginWrapper, PluginError pluginError) {
    PluginDescriptor descriptor = pluginWrapper.getDescriptor();
    PluginRuntimeSnapshot snapshot = new PluginRuntimeSnapshot();
    snapshot.setPluginId(descriptor.getPluginId());
    snapshot.setVersion(descriptor.getVersion());
    snapshot.setState(pluginWrapper.getPluginState());
    snapshot.setPluginPath(pluginWrapper.getPluginPath() == null ? null : pluginWrapper.getPluginPath().toString());
    if (pluginWrapper instanceof Pf4bootPluginWrapper) {
      snapshot.setLastStartDurationMillis(
          ((Pf4bootPluginWrapper) pluginWrapper).getLastStartDurationMillis().get());
    }
    snapshot.setDependencies(dependencies(descriptor));
    if (pluginError != null) {
      snapshot.setErrorMessage(pluginError.getErrorMessage());
      snapshot.setErrorDetail(pluginError.getErrorDetail());
    }
    return snapshot;
  }

  private List<String> dependencies(PluginDescriptor descriptor) {
    List<String> dependencies = new ArrayList<>();
    for (PluginDependency dependency : descriptor.getDependencies()) {
      dependencies.add(dependency.getPluginId());
    }
    return dependencies;
  }
}
