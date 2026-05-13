package net.xdob.pf4boot;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.modal.SharingScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultAutoExportMgr implements AutoExportMgr{
  static Logger logger = LoggerFactory.getLogger(DefaultAutoExportMgr.class);
  private final List<AutoExport> autoExports = new CopyOnWriteArrayList<>();
  @Override
  public void addAutoExportClass(Class<?> clazz, SharingScope scope) {
    addAutoExportClass(clazz, scope, PluginStarter.DEFAULT);
  }

  @Override
  public void addAutoExportClass(Class<?> clazz, SharingScope scope, String group) {
    AutoExport autoExport = new AutoExport(clazz)
        .setScope(scope)
        .setGroup(group);
    autoExports.add(autoExport);
    logger.info("addAutoExport: {}", autoExport);
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
  public void removeAutoExportClass(Class<?> clazz, SharingScope scope, String group) {
    String normalizedGroup = group == null ? PluginStarter.DEFAULT : group;
    autoExports.removeIf(autoExport
        -> autoExport.getClazz().equals(clazz)
        && autoExport.getScope() == scope
        && Objects.equals(autoExport.getGroup(), normalizedGroup));
  }

  @Override
  public List<AutoExport> getAutoExportClasses() {
    return Collections.unmodifiableList(new ArrayList<>(autoExports));
  }
}
