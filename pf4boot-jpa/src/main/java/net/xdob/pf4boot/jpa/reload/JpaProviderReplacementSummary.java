package net.xdob.pf4boot.jpa.reload;

/**
 * JPA provider 包替换摘要。
 *
 * <p>该对象写入 JPA reload 记录，用于在不暴露完整部署内部模型的前提下追踪 staged provider
 * 替换结果。</p>
 */
public class JpaProviderReplacementSummary {

  private final String deploymentId;
  private final String targetPluginId;
  private final String stagedPluginPath;
  private final String currentVersion;
  private final String stagedVersion;
  private final String state;
  private final String errorCode;
  private final String rollbackStatus;
  private final String message;

  public JpaProviderReplacementSummary(
      String deploymentId,
      String targetPluginId,
      String stagedPluginPath,
      String currentVersion,
      String stagedVersion,
      String state,
      String rollbackStatus,
      String message) {
    this(
        deploymentId,
        targetPluginId,
        stagedPluginPath,
        currentVersion,
        stagedVersion,
        state,
        null,
        rollbackStatus,
        message);
  }

  public JpaProviderReplacementSummary(
      String deploymentId,
      String targetPluginId,
      String stagedPluginPath,
      String currentVersion,
      String stagedVersion,
      String state,
      String errorCode,
      String rollbackStatus,
      String message) {
    this.deploymentId = deploymentId;
    this.targetPluginId = targetPluginId;
    this.stagedPluginPath = stagedPluginPath;
    this.currentVersion = currentVersion;
    this.stagedVersion = stagedVersion;
    this.state = state;
    this.errorCode = errorCode;
    this.rollbackStatus = rollbackStatus;
    this.message = trim(message);
  }

  public String getDeploymentId() {
    return deploymentId;
  }

  public String getTargetPluginId() {
    return targetPluginId;
  }

  public String getStagedPluginPath() {
    return stagedPluginPath;
  }

  public String getCurrentVersion() {
    return currentVersion;
  }

  public String getStagedVersion() {
    return stagedVersion;
  }

  public String getState() {
    return state;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getRollbackStatus() {
    return rollbackStatus;
  }

  public String getMessage() {
    return message;
  }

  private static String trim(String message) {
    if (message == null || message.length() <= 512) {
      return message;
    }
    return message.substring(0, 512);
  }
}
