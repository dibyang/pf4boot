package net.xdob.pf4boot.trust;

import java.nio.file.Path;

/**
 * 插件信任清单加载器。
 */
public interface PluginTrustManifestLoader {

  PluginTrustManifest load(Path pluginPath, String sidecarExtension);
}
