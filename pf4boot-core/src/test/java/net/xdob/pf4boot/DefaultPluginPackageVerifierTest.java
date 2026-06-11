package net.xdob.pf4boot;

import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultPluginPackageVerifierTest {

  @Test
  public void disabledModeSkipsChecksumVerification() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");

    PluginPackageVerificationResult result = new DefaultPluginPackageVerifier(properties)
        .verify(pluginPath, descriptor());

    assertTrue(result.isValid());
    assertFalse(result.isWarning());
  }

  @Test
  public void warnModeAllowsMissingChecksumWithWarning() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageVerificationMode(PluginPackageVerificationMode.WARN);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");

    PluginPackageVerificationResult result = new DefaultPluginPackageVerifier(properties)
        .verify(pluginPath, descriptor());

    assertTrue(result.isValid());
    assertTrue(result.isWarning());
  }

  @Test
  public void enforceModeRejectsChecksumMismatch() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageVerificationMode(PluginPackageVerificationMode.ENFORCE);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    Files.write(pluginPath, "plugin".getBytes("UTF-8"));
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".sha256"),
        "0000".getBytes("UTF-8"));

    PluginPackageVerificationResult result = new DefaultPluginPackageVerifier(properties)
        .verify(pluginPath, descriptor());

    assertFalse(result.isValid());
  }

  @Test
  public void enforceModeAcceptsMatchingChecksum() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageVerificationMode(PluginPackageVerificationMode.ENFORCE);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    byte[] bytes = "plugin".getBytes("UTF-8");
    Files.write(pluginPath, bytes);
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".sha256"),
        sha256(bytes).getBytes("UTF-8"));

    PluginPackageVerificationResult result = new DefaultPluginPackageVerifier(properties)
        .verify(pluginPath, descriptor());

    assertTrue(result.isValid());
    assertFalse(result.isWarning());
  }

  private DefaultPluginDescriptor descriptor() {
    return new DefaultPluginDescriptor("sample", "sample", "net.xdob.SamplePlugin",
        "1.0.0", "", "test", "Apache-2.0");
  }

  private static String sha256(byte[] bytes) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(bytes);
    StringBuilder builder = new StringBuilder(hash.length * 2);
    for (byte value : hash) {
      String hex = Integer.toHexString(value & 0xff);
      if (hex.length() == 1) {
        builder.append('0');
      }
      builder.append(hex);
    }
    return builder.toString();
  }
}
