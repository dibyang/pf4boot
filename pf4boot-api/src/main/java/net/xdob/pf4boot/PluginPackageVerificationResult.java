package net.xdob.pf4boot;

/**
 * 插件包校验结果。
 *
 * <p>校验器通过该对象表达加载前检查是否通过、是否需要告警以及失败原因。
 * 该类型不持有文件句柄或运行时状态，可安全跨模块传递。</p>
 */
public class PluginPackageVerificationResult {

  private final boolean valid;
  private final boolean warning;
  private final String message;

  private PluginPackageVerificationResult(boolean valid, boolean warning, String message) {
    this.valid = valid;
    this.warning = warning;
    this.message = message;
  }

  /**
   * 返回通过结果。
   */
  public static PluginPackageVerificationResult ok() {
    return new PluginPackageVerificationResult(true, false, "ok");
  }

  /**
   * 返回告警结果，运行时可继续加载插件。
   */
  public static PluginPackageVerificationResult warn(String message) {
    return new PluginPackageVerificationResult(true, true, message);
  }

  /**
   * 返回失败结果，强制模式下运行时应阻断插件加载。
   */
  public static PluginPackageVerificationResult fail(String message) {
    return new PluginPackageVerificationResult(false, false, message);
  }

  public boolean isValid() {
    return valid;
  }

  public boolean isWarning() {
    return warning;
  }

  public String getMessage() {
    return message;
  }
}
