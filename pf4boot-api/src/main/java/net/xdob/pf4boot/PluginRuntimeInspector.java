package net.xdob.pf4boot;

import net.xdob.pf4boot.modal.PluginRuntimeSnapshot;

import java.util.List;

/**
 * 插件运行时只读观测接口。
 *
 * <p>该接口只暴露状态快照，不承载插件启停、重载或删除等管理操作。</p>
 */
public interface PluginRuntimeInspector {

  /**
   * 返回当前已加载插件的运行时快照。
   */
  List<PluginRuntimeSnapshot> snapshots();
}
