package net.xdob.pf4boot.deployment;

import net.xdob.pf4boot.repository.PluginReleaseRequest;

import java.nio.file.Path;

/**
 * 插件部署编排服务入口。
 *
 * <p>第一阶段只提供只读预检和计划生成能力；后续阶段再扩展真正的替换、回滚和健康检查。</p>
 */
public interface PluginDeploymentService {

  /**
   * 为 staged 插件包生成替换计划。
   *
   * @param targetPluginId 目标插件 ID
   * @param stagedPluginPath 待部署插件包路径
   * @return 部署记录，包含计划、影响范围和预检结果
   */
  DeploymentRecord planReplacement(String targetPluginId, Path stagedPluginPath);

  /**
   * 从插件仓库 release 解析结果生成替换计划。
   *
   * @param request 仓库 release 请求
   * @return 部署记录，包含计划、影响范围和预检结果
   */
  default DeploymentRecord planReplacement(PluginReleaseRequest request) {
    throw new UnsupportedOperationException("Repository release deployment planning is not supported");
  }

  /**
   * 从插件仓库 release 执行真实替换。
   *
   * @param request 仓库 release 请求
   * @return 部署记录，包含最终状态和计划
   */
  default DeploymentRecord replace(PluginReleaseRequest request) {
    throw new UnsupportedOperationException("Repository release replacement is not supported");
  }

  /**
   * 执行短暂停机式插件替换。
   *
   * @param targetPluginId 目标插件 ID
   * @param stagedPluginPath 待部署插件包路径
   * @return 部署记录，包含最终状态和计划
   */
  DeploymentRecord replace(String targetPluginId, Path stagedPluginPath);
}
