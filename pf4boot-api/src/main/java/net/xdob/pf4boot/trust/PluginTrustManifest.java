package net.xdob.pf4boot.trust;

/**
 * `.pf4boot-trust.json` 清单模型。
 */
public class PluginTrustManifest {

  private String pluginId;
  private String pluginVersion;
  private String packageSha256;
  private PluginSignatureMetadata signature;

  public String getPluginId() {
    return pluginId;
  }

  public void setPluginId(String pluginId) {
    this.pluginId = pluginId;
  }

  public String getPluginVersion() {
    return pluginVersion;
  }

  public void setPluginVersion(String pluginVersion) {
    this.pluginVersion = pluginVersion;
  }

  public String getPackageSha256() {
    return packageSha256;
  }

  public void setPackageSha256(String packageSha256) {
    this.packageSha256 = packageSha256;
  }

  public PluginSignatureMetadata getSignature() {
    return signature;
  }

  public void setSignature(PluginSignatureMetadata signature) {
    this.signature = signature;
  }
}
