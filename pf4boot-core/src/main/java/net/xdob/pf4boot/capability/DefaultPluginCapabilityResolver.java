package net.xdob.pf4boot.capability;

import net.xdob.pf4boot.DefaultPluginTrustManifestLoader;
import net.xdob.pf4boot.trust.PluginTrustManifest;
import net.xdob.pf4boot.trust.PluginTrustManifestLoader;
import org.pf4j.PluginDescriptor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 默认插件能力声明解析器。
 *
 * <p>第一阶段只从 trust sidecar manifest 的 {@code capabilities} 字段读取声明。缺失
 * manifest 或缺失 capabilities 时返回空能力描述符，保持历史插件兼容。</p>
 */
public class DefaultPluginCapabilityResolver implements PluginCapabilityResolver {

  private final PluginTrustManifestLoader manifestLoader;
  private final String sidecarExtension;

  public DefaultPluginCapabilityResolver() {
    this(new DefaultPluginTrustManifestLoader(), ".pf4boot-trust.json");
  }

  public DefaultPluginCapabilityResolver(
      PluginTrustManifestLoader manifestLoader,
      String sidecarExtension) {
    this.manifestLoader = manifestLoader == null ? new DefaultPluginTrustManifestLoader() : manifestLoader;
    this.sidecarExtension = sidecarExtension == null || sidecarExtension.trim().isEmpty()
        ? ".pf4boot-trust.json"
        : sidecarExtension;
  }

  @Override
  public PluginCapabilityDescriptor resolve(Path pluginPath, PluginDescriptor pluginDescriptor) {
    String pluginId = pluginDescriptor == null ? null : pluginDescriptor.getPluginId();
    PluginTrustManifest manifest = manifestLoader.load(pluginPath, sidecarExtension);
    if (manifest == null) {
      return new DefaultPluginCapabilityDescriptor(pluginId, null, null);
    }
    if (pluginId == null) {
      pluginId = manifest.getPluginId();
    }
    return new DefaultPluginCapabilityDescriptor(
        pluginId,
        manifest.getProvidedCapabilities(),
        manifest.getRequiredCapabilities());
  }

  @Override
  public List<PluginCapability> providedCapabilities(Collection<PluginCapabilityDescriptor> descriptors) {
    List<PluginCapability> capabilities = new ArrayList<>();
    if (descriptors == null) {
      return capabilities;
    }
    for (PluginCapabilityDescriptor descriptor : descriptors) {
      if (descriptor != null && descriptor.provides() != null) {
        capabilities.addAll(descriptor.provides());
      }
    }
    return capabilities;
  }
}
