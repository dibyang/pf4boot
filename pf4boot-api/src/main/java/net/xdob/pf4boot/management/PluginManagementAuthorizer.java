package net.xdob.pf4boot.management;

/**
 * 管理接口鉴权 SPI.
 */
public interface PluginManagementAuthorizer {

  /**
   * 对管理请求进行认证，返回主体信息.
   */
  PluginManagementPrincipal authenticate(PluginManagementRequest request);

  /**
   * 按操作检查授权.
   */
  void authorize(PluginManagementPrincipal principal, PluginManagementOperation operation);
}
