package net.xdob.pf4boot.deployment;

import java.util.List;

/**
 * 框架模块级插件健康验证扩展点。
 *
 * <p>区别于插件本地 `PluginHealthProbe`，该扩展点由 core、web、jpa 等框架模块提供，
 * 用于把模块运行状态纳入默认部署健康检查。</p>
 */
public interface PluginHealthVerifier {

  /**
   * 验证已启动插件的模块健康状态。
   *
   * @param context 健康检查上下文
   * @param pluginClassLoader 插件类加载器
   * @return 健康检查结果，ERROR 会触发自动回滚
   */
  List<DeploymentCheckResult> verifyStartedPlugin(
      PluginHealthContext context,
      ClassLoader pluginClassLoader);
}
