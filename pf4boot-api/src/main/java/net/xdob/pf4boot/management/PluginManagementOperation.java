package net.xdob.pf4boot.management;

/**
 * 管理接口可见操作.
 */
public enum PluginManagementOperation {
  PLUGIN_READ("pf4boot:plugin:read"),
  PLUGIN_START("pf4boot:plugin:lifecycle"),
  PLUGIN_STOP("pf4boot:plugin:lifecycle"),
  PLUGIN_RESTART("pf4boot:plugin:lifecycle"),
  PLUGIN_ENABLE("pf4boot:plugin:lifecycle"),
  PLUGIN_DISABLE("pf4boot:plugin:lifecycle"),
  PLUGIN_RELOAD("pf4boot:plugin:reload"),
  DEPLOYMENT_PLAN("pf4boot:deployment:plan"),
  DEPLOYMENT_REPLACE("pf4boot:deployment:replace"),
  DEPLOYMENT_CONFIRM("pf4boot:deployment:confirm"),
  DEPLOYMENT_ROLLBACK("pf4boot:deployment:rollback"),
  DEPLOYMENT_QUERY("pf4boot:deployment:query"),
  JPA_RELOAD_PLAN("pf4boot:jpa-reload:plan"),
  JPA_RELOAD_EXECUTE("pf4boot:jpa-reload:execute"),
  JPA_RELOAD_QUERY("pf4boot:jpa-reload:query");

  private final String permission;

  PluginManagementOperation(String permission) {
    this.permission = permission;
  }

  public String getPermission() {
    return permission;
  }
}
