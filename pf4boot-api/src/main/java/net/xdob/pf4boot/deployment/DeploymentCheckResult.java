package net.xdob.pf4boot.deployment;

/**
 * 单项部署预检结果。
 *
 * <p>预检结果用于解释部署计划是否可执行，以及哪些条件需要运维人员关注。</p>
 */
public class DeploymentCheckResult {

  private final String code;
  private final DeploymentCheckSeverity severity;
  private final String message;

  public DeploymentCheckResult(String code, DeploymentCheckSeverity severity, String message) {
    this.code = code;
    this.severity = severity == null ? DeploymentCheckSeverity.INFO : severity;
    this.message = message;
  }

  /**
   * 创建普通信息结果。
   */
  public static DeploymentCheckResult info(String code, String message) {
    return new DeploymentCheckResult(code, DeploymentCheckSeverity.INFO, message);
  }

  /**
   * 创建告警结果。
   */
  public static DeploymentCheckResult warn(String code, String message) {
    return new DeploymentCheckResult(code, DeploymentCheckSeverity.WARN, message);
  }

  /**
   * 创建阻断结果。
   */
  public static DeploymentCheckResult error(String code, String message) {
    return new DeploymentCheckResult(code, DeploymentCheckSeverity.ERROR, message);
  }

  public String getCode() {
    return code;
  }

  public DeploymentCheckSeverity getSeverity() {
    return severity;
  }

  public String getMessage() {
    return message;
  }

  public boolean isError() {
    return DeploymentCheckSeverity.ERROR.equals(severity);
  }
}
