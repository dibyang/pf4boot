package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.starter.PluginManagementException;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PluginManagementPathValidatorTest {

  @Test
  public void resolveStagedPathResolvesRelativePathInsideRoot() throws Exception {
    Path stagingRoot = Files.createTempDirectory("pf4boot-management-staged");
    try {
      PluginManagementPathValidator validator = new PluginManagementPathValidator();
      Path resolved = validator.resolveStagedPath(stagingRoot.toString(), "foo/bar.jar");
      assertTrue(resolved.startsWith(stagingRoot.toAbsolutePath()));
      assertEquals(stagingRoot.toAbsolutePath().resolve("foo").resolve("bar.jar"), resolved);
    } finally {
      // no-op cleanup handled by caller
    }
  }

  @Test
  public void resolveStagedPathRejectsOutsideRoot() throws Exception {
    Path stagingRoot = Files.createTempDirectory("pf4boot-management-staged");
    try {
      PluginManagementPathValidator validator = new PluginManagementPathValidator();
      validator.resolveStagedPath(stagingRoot.toString(), "../outside.jar");
      fail("Expected precheck exception");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.PRECHECK_FAILED, e.getCode());
      assertEquals(422, e.getStatusCode());
    } finally {
      // no-op cleanup handled by caller
    }
  }
}
