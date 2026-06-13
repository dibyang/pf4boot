package net.xdob.pf4boot.repository;

/**
 * 插件仓库解析状态。
 */
public enum PluginRepositoryStatus {
  DISABLED,
  INDEX_READY,
  INDEX_FAILED,
  RELEASE_RESOLVED,
  PACKAGE_VERIFIED,
  STAGED
}
