package net.xdob.pf4boot.deployment;

/**
 * 插件部署记录观察接口。
 *
 * <p>实现类可以将部署结果写入本地内存、文件、数据库或指标系统。</p>
 */
public interface PluginDeploymentRecorder {

  /**
   * 记录一次部署结果。
   *
   * @param record 部署记录
   */
  void record(DeploymentRecord record);
}
