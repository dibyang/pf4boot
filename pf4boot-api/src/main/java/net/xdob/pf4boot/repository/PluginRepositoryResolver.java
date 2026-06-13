package net.xdob.pf4boot.repository;

/**
 * 插件仓库解析 SPI。
 */
public interface PluginRepositoryResolver {

  /**
   * 读取仓库索引。
   */
  PluginRepositoryIndex loadIndex();

  /**
   * 按请求解析 release 并校验包。
   */
  PluginRepositoryResolution resolve(PluginReleaseRequest request);
}
