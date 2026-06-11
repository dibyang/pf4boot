package net.xdob.pf4boot;

import net.xdob.pf4boot.modal.SharingScope;
import net.xdob.pf4boot.annotation.PluginStarter;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class DefaultAutoExportMgrTest {

  private DefaultAutoExportMgr autoExportMgr;

  @Before
  public void setUp() {
    autoExportMgr = new DefaultAutoExportMgr();
  }

  @Test
  public void addsAutoExportWithDifferentScopes() {
    autoExportMgr.addAutoExportClass(String.class);
    autoExportMgr.addAutoExportClass(Integer.class, SharingScope.ROOT);
    autoExportMgr.addAutoExportClass(
        Double.class, SharingScope.PLATFORM, "custom-group");

    List<AutoExport> exports = autoExportMgr.getAutoExportClasses();

    assertEquals(3, exports.size());
    assertExport(exports.get(0), String.class, SharingScope.PLATFORM, PluginStarter.DEFAULT);
    assertExport(exports.get(1), Integer.class, SharingScope.ROOT, PluginStarter.DEFAULT);
    assertExport(exports.get(2), Double.class, SharingScope.PLATFORM, "custom-group");
  }

  @Test
  public void removeAutoExportByClass() {
    autoExportMgr.addAutoExportClass(String.class, SharingScope.ROOT, "g1");
    autoExportMgr.addAutoExportClass(String.class, SharingScope.PLATFORM, PluginStarter.DEFAULT);
    autoExportMgr.addAutoExportClass(Integer.class, SharingScope.APPLICATION);

    autoExportMgr.removeAutoExportClass(String.class);

    List<AutoExport> exports = autoExportMgr.getAutoExportClasses();
    assertEquals(1, exports.size());
    assertExport(exports.get(0), Integer.class, SharingScope.APPLICATION, PluginStarter.DEFAULT);
  }

  @Test
  public void removeAutoExportByClassAndScopeAndGroupRespectsGroup() {
    autoExportMgr.addAutoExportClass(String.class, SharingScope.ROOT, "g1");
    autoExportMgr.addAutoExportClass(String.class, SharingScope.ROOT, "g2");
    autoExportMgr.addAutoExportClass(String.class, SharingScope.APPLICATION, PluginStarter.DEFAULT);

    autoExportMgr.removeAutoExportClass(String.class, SharingScope.ROOT, "g2");

    assertEquals(2, autoExportMgr.getAutoExportClasses().size());
  }

  @Test
  public void removeAutoExportByClassAndScopeWithDefaultGroupHandlesNull() {
    autoExportMgr.addAutoExportClass(String.class, SharingScope.ROOT, "custom");
    autoExportMgr.addAutoExportClass(String.class, SharingScope.ROOT, PluginStarter.DEFAULT);

    autoExportMgr.removeAutoExportClass(String.class, SharingScope.ROOT, null);

    assertEquals(1, autoExportMgr.getAutoExportClasses().size());
    assertEquals("custom", autoExportMgr.getAutoExportClasses().get(0).getGroup());
  }

  @Test
  public void getAutoExportClassesReturnsUnmodifiableSnapshot() {
    autoExportMgr.addAutoExportClass(Long.class);

    List<AutoExport> exports = autoExportMgr.getAutoExportClasses();
    assertNotNull(exports);
    assertFalse(exports.isEmpty());

    try {
      exports.add(new AutoExport(String.class));
      fail("Auto export snapshot should be immutable");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }

  private void assertExport(AutoExport autoExport, Class<?> type, SharingScope scope, String group) {
    assertSame(type, autoExport.getClazz());
    assertEquals(scope, autoExport.getScope());
    assertEquals(group, autoExport.getGroup());
  }
}
