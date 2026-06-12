package net.xdob.pf4boot.trust;

/**
 * 插件可信校验扩展点。
 */
public interface PluginPackageTrustVerifier {

  PluginPackageTrustResult verify(PluginPackageTrustRequest request);
}
