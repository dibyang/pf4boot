package net.xdob.pf4boot;

/**
 * 插件包校验模式。
 *
 * <p>用于控制加载插件包前的校验结果如何影响运行时：默认关闭以保持兼容，
 * WARN 用于灰度观测，ENFORCE 用于强制阻断不可信插件包。</p>
 */
public enum PluginPackageVerificationMode {
  /**
   * 不执行默认插件包校验。
   */
  DISABLED,
  /**
   * 执行校验并记录告警，不阻断插件加载。
   */
  WARN,
  /**
   * 执行校验并在失败时阻断插件加载。
   */
  ENFORCE
}
