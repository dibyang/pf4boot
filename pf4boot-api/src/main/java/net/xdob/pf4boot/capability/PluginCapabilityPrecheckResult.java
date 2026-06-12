package net.xdob.pf4boot.capability;

/**
 * 能力预检结果。
 */
public class PluginCapabilityPrecheckResult {

  private final boolean valid;
  private final boolean warning;
  private final String code;
  private final String message;

  private PluginCapabilityPrecheckResult(boolean valid, boolean warning, String code, String message) {
    this.valid = valid;
    this.warning = warning;
    this.code = code;
    this.message = message;
  }

  public static PluginCapabilityPrecheckResult ok(String message) {
    return new PluginCapabilityPrecheckResult(true, false, "OK", message);
  }

  public static PluginCapabilityPrecheckResult warn(String code, String message) {
    return new PluginCapabilityPrecheckResult(true, true, code, message);
  }

  public static PluginCapabilityPrecheckResult fail(String code, String message) {
    return new PluginCapabilityPrecheckResult(false, false, code, message);
  }

  public boolean isValid() {
    return valid;
  }

  public boolean isWarning() {
    return warning;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
