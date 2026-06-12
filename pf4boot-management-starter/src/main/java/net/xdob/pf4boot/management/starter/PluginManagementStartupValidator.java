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
    if (!properties.isEnabled()) {
      return;
    }
    if (properties.getMode() == PluginManagementMode.DISABLED) {
      throw new PluginManagementException(
          net.xdob.pf4boot.management.PluginManagementErrorCode.INVALID_REQUEST,
          "Management mode can not be DISABLED when management is enabled",
          400);
    }
    if (properties.getMode() == PluginManagementMode.LOCAL_TOKEN && !StringUtils.hasText(properties.getToken())) {
      if (!hasCustomAuthorizer()) {
        throw new PluginManagementException(
            net.xdob.pf4boot.management.PluginManagementErrorCode.INVALID_REQUEST,
            "Local token mode requires spring.pf4boot.management.http.token",
            400);
      }
      return;
    }
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
