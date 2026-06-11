package net.xdob.pf4boot.deployment;

import java.util.List;

/**
 * 插件停止后的资源清理验证扩展点。
 *
 * <p>部署服务在插件 stop 后、unload 前调用该扩展点，用于检查 Web mapping、共享 Bean、
 * 定时任务或其他模块级资源是否已经摘除。</p>
 */
public interface PluginCleanupVerifier {

  /**
   * 验证单个插件停止后的资源清理状态。
   *
   * @param pluginId 插件 ID
   * @param pluginClassLoader 插件类加载器
   * @return 预检风格的验证结果，ERROR 会阻断部署并触发回滚
   */
  List<DeploymentCheckResult> verifyStoppedPlugin(String pluginId, ClassLoader pluginClassLoader);
}
