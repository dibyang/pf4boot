package net.xdob.pf4boot.trust;

import net.xdob.pf4boot.capability.PluginCapability;
import net.xdob.pf4boot.capability.PluginCapabilityRequirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件旁路信任清单模型。
 *
 * <p>第一阶段该清单同时承载包摘要、签名元数据和能力声明，避免为 capability 再引入
 * 第二个旁路文件。</p>
 */
public class PluginTrustManifest {

  private String pluginId;
  private String pluginVersion;
  private String packageSha256;
  private String pf4bootVersionRange;
  private String springBootVersionRange;
  private PluginSignatureMetadata signature;
  private List<PluginCapability> providedCapabilities = new ArrayList<>();
  private List<PluginCapabilityRequirement> requiredCapabilities = new ArrayList<>();

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

  public String getPf4bootVersionRange() {
    return pf4bootVersionRange;
  }

  public void setPf4bootVersionRange(String pf4bootVersionRange) {
    this.pf4bootVersionRange = pf4bootVersionRange;
  }

  public String getSpringBootVersionRange() {
    return springBootVersionRange;
  }

  public void setSpringBootVersionRange(String springBootVersionRange) {
    this.springBootVersionRange = springBootVersionRange;
  }

  public PluginSignatureMetadata getSignature() {
    return signature;
  }

  public void setSignature(PluginSignatureMetadata signature) {
    this.signature = signature;
  }

  public List<PluginCapability> getProvidedCapabilities() {
    return Collections.unmodifiableList(providedCapabilities);
  }

  public void setProvidedCapabilities(List<PluginCapability> providedCapabilities) {
    this.providedCapabilities = providedCapabilities == null
        ? new ArrayList<PluginCapability>()
        : new ArrayList<>(providedCapabilities);
  }

  public List<PluginCapabilityRequirement> getRequiredCapabilities() {
    return Collections.unmodifiableList(requiredCapabilities);
  }

  public void setRequiredCapabilities(List<PluginCapabilityRequirement> requiredCapabilities) {
    this.requiredCapabilities = requiredCapabilities == null
        ? new ArrayList<PluginCapabilityRequirement>()
        : new ArrayList<>(requiredCapabilities);
  }
}
