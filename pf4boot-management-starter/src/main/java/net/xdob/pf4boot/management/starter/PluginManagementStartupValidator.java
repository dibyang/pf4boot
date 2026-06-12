package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementMode;
import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

public class PluginManagementStartupValidator {

  private final Pf4bootManagementProperties properties;
  private final ObjectProvider<PluginManagementAuthorizer> pluginManagementAuthorizers;

  public PluginManagementStartupValidator(
      Pf4bootManagementProperties properties,
      ObjectProvider<PluginManagementAuthorizer> pluginManagementAuthorizers) {
    this.properties = properties;
    this.pluginManagementAuthorizers = pluginManagementAuthorizers;
  }

  public void validate() {
    // 管理接口未开启时直接跳过，避免非管理模式受到额外限制。
    if (!properties.isEnabled()) {
      return;
    }
    // 启用管理接口时，不允许配置为 DISABLED 模式：这会形成「明明打开管理入口，但又声明禁用」的无效组合。
    if (properties.getMode() == PluginManagementMode.DISABLED) {
      throw new PluginManagementException(
          net.xdob.pf4boot.management.PluginManagementErrorCode.INVALID_REQUEST,
          "Management mode can not be DISABLED when management is enabled",
          400);
    }
    // LOCAL_TOKEN 模式要求配置 token；若希望用 SPI 覆盖认证行为，可不配 token，但必须提供自定义 authorizer。
    if (properties.getMode() == PluginManagementMode.LOCAL_TOKEN && !StringUtils.hasText(properties.getToken())) {
      if (!hasCustomAuthorizer()) {
        throw new PluginManagementException(
            net.xdob.pf4boot.management.PluginManagementErrorCode.INVALID_REQUEST,
            "Local token mode requires spring.pf4boot.management.http.token",
            400);
      }
      return;
    }
    // REMOTE_DELEGATED 模式必须至少有一个可用的自定义 authorizer。
    if (properties.getMode() == PluginManagementMode.REMOTE_DELEGATED) {
      if (!hasCustomAuthorizer()) {
        throw new PluginManagementException(
            net.xdob.pf4boot.management.PluginManagementErrorCode.INVALID_REQUEST,
            "Remote delegated mode requires at least one PluginManagementAuthorizer bean",
            400);
      }
    }
  }

  private boolean hasCustomAuthorizer() {
    return pluginManagementAuthorizers.orderedStream()
        .anyMatch(authorizer -> authorizer != null && !(authorizer instanceof DelegatingPluginManagementAuthorizer));
  }
}
