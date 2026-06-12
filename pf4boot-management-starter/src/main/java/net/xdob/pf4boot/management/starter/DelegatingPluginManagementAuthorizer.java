package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementPrincipal;
import net.xdob.pf4boot.management.PluginManagementRequest;

import java.util.List;

/**
 * 远程鉴权委托器，按顺序尝试所有 SPI 实现。
 */
public class DelegatingPluginManagementAuthorizer implements PluginManagementAuthorizer {

  private final List<PluginManagementAuthorizer> delegators;

  public DelegatingPluginManagementAuthorizer(List<PluginManagementAuthorizer> delegators) {
    this.delegators = delegators;
  }

  @Override
  public PluginManagementPrincipal authenticate(PluginManagementRequest request) {
    if (request == null) {
      return null;
    }
    for (PluginManagementAuthorizer authorizer : delegators) {
      PluginManagementPrincipal principal = authorizer.authenticate(request);
      if (principal != null) {
        return principal;
      }
    }
    throw new PluginManagementException(
        PluginManagementErrorCode.UNAUTHENTICATED,
        "No authorized management principal",
        401);
  }

  @Override
  public void authorize(PluginManagementPrincipal principal, PluginManagementOperation operation) {
    if (principal == null) {
      throw new PluginManagementException(
          PluginManagementErrorCode.UNAUTHENTICATED,
          "No principal returned from delegate authorizer",
          401);
    }
    for (PluginManagementAuthorizer authorizer : delegators) {
      try {
        authorizer.authorize(principal, operation);
        return;
      } catch (PluginManagementException e) {
        if (PluginManagementErrorCode.FORBIDDEN.equals(e.getCode())) {
          continue;
        }
        throw e;
      }
    }
    throw new PluginManagementException(
        PluginManagementErrorCode.FORBIDDEN,
        "No authorized delegate for management operation",
        403);
  }
}
