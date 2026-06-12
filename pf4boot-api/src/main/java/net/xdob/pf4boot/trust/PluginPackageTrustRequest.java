package net.xdob.pf4boot.trust;

import org.pf4j.PluginDescriptor;

import java.nio.file.Path;

/**
 * 可信校验器的输入参数。
 */
public class PluginPackageTrustRequest {

  private final Path pluginPath;
  private final PluginDescriptor pluginDescriptor;

  public PluginPackageTrustRequest(Path pluginPath, PluginDescriptor pluginDescriptor) {
    this.pluginPath = pluginPath;
    this.pluginDescriptor = pluginDescriptor;
  }

  public Path getPluginPath() {
    return pluginPath;
  }

  public PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }
}
