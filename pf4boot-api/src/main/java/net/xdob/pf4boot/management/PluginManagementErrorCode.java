package net.xdob.pf4boot.management;

/**
 * 管理接口错误码.
 */
public enum PluginManagementErrorCode {
  INVALID_REQUEST("PFM-001"),
  UNAUTHENTICATED("PFM-002"),
  FORBIDDEN("PFM-003"),
  NOT_FOUND("PFM-004"),
  CONFLICT("PFM-005"),
  RATE_LIMITED("PFM-006"),
  PRECHECK_FAILED("PFM-007"),
  OPERATION_FAILED("PFM-008"),
  UNAVAILABLE("PFM-009");

  private final String code;

  PluginManagementErrorCode(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
