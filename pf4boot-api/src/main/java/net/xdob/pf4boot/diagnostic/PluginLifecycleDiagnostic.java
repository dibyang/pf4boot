package net.xdob.pf4boot.diagnostic;

/**
 * 插件生命周期诊断扩展点。
 *
 * <p>该扩展点面向测试、管理接口和只读观测，不直接执行 start、stop、reload 或清理动作。
 * 实现类应只读取框架当前持有的资源计数、锁策略和残留摘要。</p>
 */
public interface PluginLifecycleDiagnostic {

  /**
   * 检查指定插件停止后的资源清理状态。
   *
   * @param pluginId 插件 ID
   * @param pluginClassLoader 插件停止或卸载前保留的 ClassLoader 引用，可为空
   * @return 清理诊断报告
   */
  PluginCleanupReport inspectAfterStop(String pluginId, ClassLoader pluginClassLoader);

  /**
   * 返回生命周期并发控制策略摘要。
   *
   * @return 并发控制诊断报告
   */
  PluginConcurrencyReport inspectLifecycleLocks();
}
