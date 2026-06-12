package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementPrincipal;
import net.xdob.pf4boot.management.PluginManagementRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * 本地回环 + Token 鉴权实现。
 */
public class LocalTokenPluginManagementAuthorizer implements PluginManagementAuthorizer {

  private final Pf4bootManagementProperties properties;

  public LocalTokenPluginManagementAuthorizer(Pf4bootManagementProperties properties) {
    this.properties = properties;
  }

  @Override
  public PluginManagementPrincipal authenticate(PluginManagementRequest request) {
    if (!isSameToken(request.getToken())) {
      throw new PluginManagementException(
          PluginManagementErrorCode.UNAUTHENTICATED,
          "Management token is missing or invalid",
          401);
    }
    if (properties.isAllowLoopbackOnly() && !isLoopbackAddress(request.getRemoteAddress())) {
      throw new PluginManagementException(
          PluginManagementErrorCode.UNAUTHENTICATED,
          "Local token mode requires loopback request",
          401);
    }
    PluginManagementPrincipal principal = new PluginManagementPrincipal();
    principal.setPrincipalId(request.getRemoteAddress());
    principal.setPrincipalName("local-admin");
    principal.setPermissions(Arrays.asList(
        "pf4boot:plugin:read",
        "pf4boot:plugin:lifecycle",
        "pf4boot:plugin:reload",
        "pf4boot:deployment:query",
        "pf4boot:deployment:plan",
        "pf4boot:deployment:replace",
        "pf4boot:deployment:rollback",
        "pf4boot:admin:all"));
    return principal;
  }

  @Override
  public void authorize(PluginManagementPrincipal principal, PluginManagementOperation operation) {
    String permission = operation.getPermission();
    List<String> permissions = principal.getPermissions();
    if (permissions.contains("pf4boot:admin:all") || permissions.contains(permission)) {
      return;
    }
    throw new PluginManagementException(
        PluginManagementErrorCode.FORBIDDEN,
        "Operation not permitted: " + operation,
        403);
  }

  private boolean isSameToken(String token) {
    if (properties.getToken() == null || properties.getToken().isEmpty()) {
      return false;
    }
    if (token == null || token.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        properties.getToken().getBytes(StandardCharsets.UTF_8),
        token.getBytes(StandardCharsets.UTF_8));
  }

  private boolean isLoopbackAddress(String remoteAddress) {
    try {
      InetAddress address = InetAddress.getByName(remoteAddress);
      return address.isAnyLocalAddress() || address.isLoopbackAddress();
    } catch (UnknownHostException ignore) {
      return false;
    }
  }
}
