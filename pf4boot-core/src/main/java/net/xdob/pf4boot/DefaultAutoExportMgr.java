package net.xdob.pf4boot;

import net.xdob.pf4boot.modal.SharingScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultAutoExportMgr implements AutoExportMgr{
  private final List<AutoExport> autoExports = new ArrayList<>();
  @Override
  public void addAutoExportClass(Class<?> clazz, SharingScope scope) {
    AutoExport autoExport = new AutoExport(clazz)
        .setScope( scope);
    autoExports.add(autoExport);
  }

  @Override
  public void addAutoExportClass(Class<?> clazz) {
    addAutoExportClass(clazz, SharingScope.PLATFORM);
  }

  @Override
  public void removeAutoExportClass(Class<?> clazz) {
    autoExports.removeIf(autoExport
        -> autoExport.getClazz().equals(clazz));
  }

  @Override
  public List<AutoExport> getAutoExportClasses() {
    return Collections.unmodifiableList(autoExports);
  }
}
