package net.xdob.pf4boot;

import net.xdob.pf4boot.modal.SharingScope;

import java.util.List;

public interface AutoExportMgr {
  void addAutoExportClass(Class<?> clazz, SharingScope scope);
  void addAutoExportClass(Class<?> clazz);
  void removeAutoExportClass(Class<?> clazz);
  List<AutoExport> getAutoExportClasses();
}
