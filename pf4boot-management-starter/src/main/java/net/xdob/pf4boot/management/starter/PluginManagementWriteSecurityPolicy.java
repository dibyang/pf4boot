package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementRequest;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * Shared security policy for write operations on management endpoints.
 */
public class PluginManagementWriteSecurityPolicy {

  private final Pf4bootManagementProperties properties;
  private final PluginManagementRateLimiter rateLimiter;

  public PluginManagementWriteSecurityPolicy(
      Pf4bootManagementProperties properties,
      PluginManagementRateLimiter rateLimiter) {
    this.properties = properties;
    this.rateLimiter = rateLimiter;
  }

  public void validateWriteRequest(HttpServletRequest request, PluginManagementRequest mgmtRequest) {
    String subject = mgmtRequest == null ? null : mgmtRequest.getRemoteAddress();
    if (subject == null) {
      subject = request == null ? "anonymous" : request.getRemoteAddr();
    }
    rateLimiter.validateWrite(subject);
    enforceOrigin(request, mgmtRequest);
  }

  private void enforceOrigin(HttpServletRequest request, PluginManagementRequest mgmtRequest) {
    String mode = properties.getCsrf() == null ? "auto" : properties.getCsrf().getEnabled();
    boolean csrfEnabled = shouldCheckCsrf(request, mode);
    if (!csrfEnabled) {
      return;
    }
    String origin = request == null ? null : request.getHeader("Origin");
    if (!StringUtils.hasText(origin)) {
      throw new PluginManagementException(
          PluginManagementErrorCode.FORBIDDEN,
          "CSRF/origin check failed: missing Origin header",
          403);
    }
    if (!isSameOrigin(request, origin)) {
      throw new PluginManagementException(
          PluginManagementErrorCode.FORBIDDEN,
          "CSRF/origin check failed: mismatch origin",
          403);
    }
    if (mgmtRequest != null && mgmtRequest.getOrigin() != null && !origin.equals(mgmtRequest.getOrigin())) {
      // keep source-of-truth in request context while avoiding subtle request parsing differences
      throw new PluginManagementException(
          PluginManagementErrorCode.FORBIDDEN,
          "CSRF/origin check failed: request origin changed",
          403);
    }
  }

  private boolean shouldCheckCsrf(HttpServletRequest request, String rawMode) {
    if (!StringUtils.hasText(rawMode)) {
      return false;
    }
    if ("true".equalsIgnoreCase(rawMode)) {
      return true;
    }
    if ("false".equalsIgnoreCase(rawMode)) {
      return false;
    }
    // auto: only check browser-originated writes (simplified signal: Origin present)
    return request != null && StringUtils.hasText(request.getHeader("Origin"));
  }

  private boolean isSameOrigin(HttpServletRequest request, String originHeader) {
    URI origin;
    try {
      origin = URI.create(originHeader);
    } catch (IllegalArgumentException e) {
      throw new PluginManagementException(
          PluginManagementErrorCode.FORBIDDEN,
          "CSRF/origin check failed: malformed Origin header",
          403);
    }

    String requestScheme = request == null ? "http" : trim(request.getHeader("X-Forwarded-Proto"));
    if (!StringUtils.hasText(requestScheme)) {
      requestScheme = request == null ? "http" : request.getScheme();
    }
    String requestHost = request == null ? null : request.getHeader("Host");
    if (!StringUtils.hasText(requestHost)) {
      requestHost = request == null ? null : request.getServerName();
      if (request != null && request.getServerPort() > 0) {
        requestHost = requestHost + ":" + request.getServerPort();
      }
    }
    if (!StringUtils.hasText(requestHost)) {
      return false;
    }

    String originHost = extractHost(origin.getHost());
    String requestHostName = extractHost(requestHost);
    String originScheme = trim(origin.getScheme());
    if (!StringUtils.hasText(originScheme) || !originScheme.equalsIgnoreCase(requestScheme)) {
      return false;
    }
    return requestHostName != null
        && requestHostName.equalsIgnoreCase(originHost)
        && arePortsCompatible(requestHost, request.getServerPort(), origin.getPort());
  }

  private String extractHost(String hostOrOrigin) {
    if (!StringUtils.hasText(hostOrOrigin)) {
      return null;
    }
    int idx = hostOrOrigin.lastIndexOf('/');
    String hostPort = hostOrOrigin.substring(idx + 1);
    if (hostPort.startsWith("[")) {
      int close = hostPort.indexOf(']');
      return close > 0 ? hostPort.substring(0, close + 1) : hostPort;
    }
    int portIndex = hostPort.lastIndexOf(':');
    if (portIndex <= 0) {
      return hostPort;
    }
    return hostPort.substring(0, portIndex);
  }

  private boolean arePortsCompatible(
      String requestHost,
      int requestPort,
      int originPort) {
    int requestHostPort = extractPort(requestHost, requestPort);
    if (requestHostPort < 0 || originPort < 0) {
      return true;
    }
    return requestHostPort == originPort;
  }

  private int extractPort(String host, int fallbackPort) {
    if (!StringUtils.hasText(host)) {
      return fallbackPort;
    }
    int idx = host.lastIndexOf(':');
    if (idx <= 0) {
      return fallbackPort;
    }
    try {
      return Integer.parseInt(host.substring(idx + 1));
    } catch (NumberFormatException e) {
      return fallbackPort;
    }
  }

  private String trim(String value) {
    return value == null ? "" : value.trim();
  }
}
