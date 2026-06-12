package net.xdob.pf4boot.trust;

/**
 * 插件包可信链路结果。
 */
public class PluginPackageTrustResult {

  private final PluginPackageTrustStatus status;
  private final String message;
  private final PluginTrustManifest manifest;

  public PluginPackageTrustResult(
      PluginPackageTrustStatus status,
      String message,
      PluginTrustManifest manifest) {
    this.status = status;
    this.message = message;
    this.manifest = manifest;
  }

  public static PluginPackageTrustResult ok(String message, PluginTrustManifest manifest) {
    return new PluginPackageTrustResult(PluginPackageTrustStatus.OK, message, manifest);
  }

  public static PluginPackageTrustResult warn(String message, PluginTrustManifest manifest) {
    return new PluginPackageTrustResult(PluginPackageTrustStatus.WARN, message, manifest);
  }

  public static PluginPackageTrustResult fail(String message, PluginTrustManifest manifest) {
    return new PluginPackageTrustResult(PluginPackageTrustStatus.FAILED, message, manifest);
  }

  public PluginPackageTrustStatus getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public PluginTrustManifest getManifest() {
    return manifest;
  }

  public boolean isOk() {
    return PluginPackageTrustStatus.OK.equals(status);
  }

  public boolean isWarn() {
    return PluginPackageTrustStatus.WARN.equals(status);
  }

  public boolean isFailed() {
    return PluginPackageTrustStatus.FAILED.equals(status);
  }
}
