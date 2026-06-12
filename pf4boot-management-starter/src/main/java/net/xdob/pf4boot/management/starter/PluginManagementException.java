package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;

public class PluginManagementException extends RuntimeException {

  private final PluginManagementErrorCode code;
  private final int statusCode;

  public PluginManagementException(PluginManagementErrorCode code, String message, int statusCode) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
  }

  public PluginManagementException(
      PluginManagementErrorCode code,
      String message,
      int statusCode,
      Throwable cause) {
    super(message, cause);
    this.code = code;
    this.statusCode = statusCode;
  }

  public PluginManagementErrorCode getCode() {
    return code;
  }

  public int getStatusCode() {
    return statusCode;
  }
}

