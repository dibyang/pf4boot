package net.xdob.pf4boot.deployment;

/**
 * 插件健康检查扩展点。
 *
 * <p>插件可以在自身 Spring 上下文中暴露该类型 Bean。部署服务会在替换启动完成后调用，
 * 任一 ERROR 结果都会触发自动回滚。</p>
 */
public interface PluginHealthProbe {

  /**
   * 执行健康检查。
   *
   * @param context 当前部署和插件状态
   * @return 健康检查结果，不能返回 null
   */
  DeploymentCheckResult check(PluginHealthContext context);
}
