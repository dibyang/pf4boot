package net.xdob.sample.console;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class PluginManagementConsoleContractTest {

  @Test
  public void consoleUsesManagementApiTokenAndIdempotencyHeaders() throws Exception {
    String html = new String(Files.readAllBytes(Paths.get(
        "src/main/resources/static/index.html")), StandardCharsets.UTF_8);

    assertTrue(html.contains("/pf4boot/admin/plugins"));
    assertTrue(html.contains("/pf4boot/admin/deployments/plan"));
    assertTrue(html.contains("/pf4boot/admin/deployments/replace"));
    assertTrue(html.contains("/confirm"));
    assertTrue(html.contains("/rollback"));
    assertTrue(html.contains("/pf4boot/admin/jpa/domains/"));
    assertTrue(html.contains("/reload/plan"));
    assertTrue(html.contains("/actuator/pf4bootjpareload"));
    assertTrue(html.contains("/actuator/pf4bootgovernance"));
    assertTrue(html.contains("X-PF4Boot-Admin-Token"));
    assertTrue(html.contains("X-Idempotency-Key"));
    assertTrue(html.contains("repositoryVersion"));
    assertTrue(html.contains("repositoryRollback"));
    assertTrue(html.contains("localStorage") == false);
  }
}
