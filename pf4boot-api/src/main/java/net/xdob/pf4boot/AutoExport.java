package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.modal.SharingScope;

public class AutoExport {
  private SharingScope scope = SharingScope.PLATFORM;
  private String group = PluginStarter.DEFAULT;
  private final Class<?> clazz;

  public AutoExport(Class<?> clazz) {
    this.clazz = clazz;
  }

  public SharingScope getScope() {
    return scope;
  }

  public AutoExport setScope(SharingScope scope) {
    this.scope = scope;
    return this;
  }

  public String getGroup() {
    return group;
  }

  public AutoExport setGroup(String group) {
    this.group = group;
    return this;
  }
  public Class<?> getClazz() {
    return clazz;
  }
}
