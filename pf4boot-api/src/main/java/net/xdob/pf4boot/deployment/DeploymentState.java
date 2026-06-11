package net.xdob.pf4boot.deployment;

/**
 * 插件部署编排状态。
 *
 * <p>该状态只描述安全热替换部署服务的编排进度，不改变 PF4J
 * 底层插件生命周期状态语义。</p>
 */
public enum DeploymentState {
  PLANNED,
  PRECHECKED,
  APPLYING,
  DRAINING,
  STOPPING,
  ACTIVATING,
  STARTING,
  VERIFYING,
  CLEANUP_VERIFYING,
  SUCCEEDED,
  FAILED,
  ROLLING_BACK,
  MANUAL_INTERVENTION
}
