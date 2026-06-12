package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;

import java.util.regex.Pattern;

final class PluginManagementResponseSanitizer {

  private static final int MAX_SAFE_MESSAGE_LENGTH = 512;
  private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
      "(?i)(token|password|passwd|secret|private[-_ ]?key)\\s*[:=]\\s*[^\\s,;]+");
  private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile(
      "([A-Za-z]:\\\\)(?:[^\\\\\\s]+\\\\)*[^\\\\\\s]*");
  private static final Pattern UNIX_ABSOLUTE_PATH = Pattern.compile(
      "(?<![A-Za-z0-9_.-])/(?:[^/\\s]+/)+[^\\s,;]*");
  private static final Pattern STACK_TRACE_LINE = Pattern.compile(
      "(?m)^\\s*at\\s+[^\\r\\n]+");

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

  static String safeText(String message) {
    if (message == null) {
      return null;
    }
    String safe = SECRET_ASSIGNMENT.matcher(message).replaceAll("$1=<redacted>");
    safe = WINDOWS_ABSOLUTE_PATH.matcher(safe).replaceAll("$1<redacted-path>");
    safe = UNIX_ABSOLUTE_PATH.matcher(safe).replaceAll("/<redacted-path>");
    safe = STACK_TRACE_LINE.matcher(safe).replaceAll("    at <redacted-stack>");
    safe = safe.replace('\r', ' ').replace('\n', ' ');
    if (safe.length() > MAX_SAFE_MESSAGE_LENGTH) {
      return safe.substring(0, MAX_SAFE_MESSAGE_LENGTH) + "...";
    }
    return safe;
  }
}
