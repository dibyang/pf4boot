package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;

final class PluginManagementResponseSanitizer {

  private PluginManagementResponseSanitizer() {
  }

  static String safeMessage(PluginManagementErrorCode code) {
    if (code == null) {
      return "Management operation failed";
    }
    switch (code) {
      case INVALID_REQUEST:
        return "Invalid management request";
      case UNAUTHENTICATED:
        return "Management authentication failed";
      case FORBIDDEN:
        return "Management operation is not permitted";
      case NOT_FOUND:
        return "Requested management resource was not found";
      case CONFLICT:
        return "Management request conflicts with existing operation";
      case RATE_LIMITED:
        return "Management write rate limit exceeded";
      case PRECHECK_FAILED:
        return "Deployment precheck failed";
      case UNAVAILABLE:
        return "Management service is unavailable";
      case OPERATION_FAILED:
      default:
        return "Management operation failed";
    }
  }
}
