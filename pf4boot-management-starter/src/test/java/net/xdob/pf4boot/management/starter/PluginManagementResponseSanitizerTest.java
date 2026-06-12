package net.xdob.pf4boot.management.starter;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginManagementResponseSanitizerTest {

  @Test
  public void safeTextRedactsSecretsPathsAndStackFrames() {
    String unsafe = "failed at D:\\secret\\plugin.zip token=sample-token "
        + "privateKey=/etc/pf4boot/private.pem\n"
        + "    at com.example.Secret.run(Secret.java:1)";

    String safe = PluginManagementResponseSanitizer.safeText(unsafe);

    assertFalse(safe.contains("sample-token"));
    assertFalse(safe.contains("D:\\secret\\plugin.zip"));
    assertFalse(safe.contains("/etc/pf4boot/private.pem"));
    assertFalse(safe.contains("com.example.Secret.run"));
    assertTrue(safe.contains("<redacted>"));
    assertTrue(safe.contains("<redacted-path>"));
    assertTrue(safe.contains("<redacted-stack>"));
  }
}
