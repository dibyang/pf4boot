package net.xdob.pf4boot;

import net.xdob.pf4boot.modal.SharingScope;

import java.util.List;

public interface AutoExportMgr {
  void addAutoExportClass(Class<?> clazz, SharingScope scope);
  default void addAutoExportClass(Class<?> clazz, SharingScope scope, String group) {
    addAutoExportClass(clazz, scope);
  }
  void addAutoExportClass(Class<?> clazz);
  void removeAutoExportClass(Class<?> clazz);
  default void removeAutoExportClass(Class<?> clazz, SharingScope scope, String group) {
    removeAutoExportClass(clazz);
  }
  List<AutoExport> getAutoExportClasses();
}
