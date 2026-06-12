package net.xdob.pf4boot;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class Pf4bootStarterCompatibilityTest {

  @Test
  public void noManagementAutoConfigurationClassOnNonManagementStarterClasspath() {
    assertFalse("management starter class should not be on pf4boot-starter test classpath",
        isClassPresent("net.xdob.pf4boot.management.starter.Pf4bootManagementAutoConfiguration"));
  }

  @Test
  public void nonWebStarterDoesNotExposeServletApi() {
    assertFalse("servlet API should not be a transitive API of pf4boot-starter",
        isClassPresent("javax.servlet.Servlet"));
  }

  private boolean isClassPresent(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
