package net.xdob.pf4boot.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 管理调用者身份描述.
 */
public class PluginManagementPrincipal {

  private String principalId;
  private String principalName;
  private List<String> permissions = new ArrayList<>();

  public PluginManagementPrincipal() {
  }

  public PluginManagementPrincipal(String principalId) {
    this.principalId = principalId;
  }

  public String getPrincipalId() {
    return principalId;
  }

  public void setPrincipalId(String principalId) {
    this.principalId = principalId;
  }

  public String getPrincipalName() {
    return principalName;
  }

  public void setPrincipalName(String principalName) {
    this.principalName = principalName;
  }

  public List<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(List<String> permissions) {
    this.permissions = permissions == null ? Collections.<String>emptyList() : permissions;
  }
}
