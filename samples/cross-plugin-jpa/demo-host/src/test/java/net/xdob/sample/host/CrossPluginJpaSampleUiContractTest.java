package net.xdob.sample.host;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class CrossPluginJpaSampleUiContractTest {

  @Test
  public void uiReferencesBusinessAndManagementConsoleEndpoints() throws Exception {
    String html = new String(Files.readAllBytes(Paths.get(
        "src/main/resources/static/index.html")), StandardCharsets.UTF_8);

    assertTrue(html.contains("/api/sample/workflow/place"));
    assertTrue(html.contains("/api/sample/workflow/summary"));
    assertTrue(html.contains("/api/sample/workflow/audit"));
    assertTrue(html.contains("/api/sample/unrelated/health"));
    assertTrue(html.contains("/pf4boot/admin/plugins"));
    assertTrue(html.contains("/pf4boot/admin/deployments/' + action"));
    assertTrue(html.contains("deploymentPlan"));
    assertTrue(html.contains("deploymentReplace"));
    assertTrue(html.contains("/pf4boot/admin/jpa/domains/"));
    assertTrue(html.contains("/actuator/pf4bootgovernance"));
    assertTrue(html.contains("/actuator/pf4bootjpareload"));
    assertTrue(html.contains("startFailedPlugins"));
    assertTrue(html.contains("disableSelected"));
    assertTrue(html.contains("X-PF4Boot-Admin-Token"));
    assertTrue(html.contains("X-Idempotency-Key"));
  }
}
