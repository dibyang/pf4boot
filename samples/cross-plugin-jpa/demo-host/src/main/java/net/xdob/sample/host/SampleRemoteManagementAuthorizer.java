package net.xdob.sample.host;

import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementPrincipal;
import net.xdob.pf4boot.management.PluginManagementRequest;
import net.xdob.pf4boot.management.starter.PluginManagementException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Remote delegated security example for management API.
 *
 * <p>Enable this authorizer with Spring profile {@code management-remote-sample}
 * and switch host configuration to REMOTE_DELEGATED mode.
 */
@Component
@Profile("management-remote-sample")
public class SampleRemoteManagementAuthorizer implements PluginManagementAuthorizer {

  @Override
  public PluginManagementPrincipal authenticate(PluginManagementRequest request) {
    String token = request == null ? null : request.getToken();
    if (!StringUtils.hasText(token)) {
      throw new PluginManagementException(
          PluginManagementErrorCode.UNAUTHENTICATED,
          "Management token header is missing",
          401);
    }

    PluginManagementPrincipal principal = new PluginManagementPrincipal();
    principal.setPrincipalId(token);
    principal.setPrincipalName("remote-user-" + token);

    if ("ops-token".equals(token)) {
      principal.setPermissions(Arrays.asList("pf4boot:admin:all"));
    } else if ("reader-token".equals(token)) {
      principal.setPermissions(Arrays.asList("pf4boot:plugin:read"));
    } else {
      throw new PluginManagementException(
          PluginManagementErrorCode.UNAUTHENTICATED,
          "Unknown management token",
          401);
    }
    return principal;
  }

  @Override
  public void authorize(PluginManagementPrincipal principal, PluginManagementOperation operation) {
    if (principal == null) {
      throw new PluginManagementException(
          PluginManagementErrorCode.UNAUTHENTICATED,
          "Missing management principal",
          401);
    }
    if (principal.getPermissions() == null) {
      throw new PluginManagementException(
          PluginManagementErrorCode.FORBIDDEN,
          "No management permission",
          403);
    }
    if (principal.getPermissions().contains("pf4boot:admin:all")) {
      return;
    }
    String required = operation == null ? null : operation.getPermission();
    if (required != null && principal.getPermissions().contains(required)) {
      return;
    }
    throw new PluginManagementException(
        PluginManagementErrorCode.FORBIDDEN,
        "Operation not permitted: " + operation,
        403);
  }
}