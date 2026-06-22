package net.xdob.pf4boot;

import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultPluginPackageTrustVerifierTest {

  @Test
  public void disabledModeIgnoresMissingManifest() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertTrue(result.isValid());
    assertFalse(result.isWarning());
  }

  @Test
  public void warnModeRecordsMissingManifest() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageTrustMode(PluginPackageVerificationMode.WARN);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertTrue(result.isValid());
    assertTrue(result.isWarning());
  }

  @Test
  public void enforceModeRejectsMissingManifest() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageTrustMode(PluginPackageVerificationMode.ENFORCE);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertFalse(result.isValid());
  }

  @Test
  public void enforceModeAcceptsMatchingManifestChecksum() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageTrustMode(PluginPackageVerificationMode.ENFORCE);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    byte[] bytes = "plugin".getBytes("UTF-8");
    Files.write(pluginPath, bytes);
    writeManifest(pluginPath, "sample", "1.0.0", sha256(bytes));

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertTrue(result.isValid());
    assertFalse(result.isWarning());
  }

  @Test
  public void enforceModeRejectsManifestChecksumMismatch() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageTrustMode(PluginPackageVerificationMode.ENFORCE);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    Files.write(pluginPath, "plugin".getBytes("UTF-8"));
    writeManifest(pluginPath, "sample", "1.0.0", "0000");

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertFalse(result.isValid());
  }

  @Test
  public void warnModeAllowsDescriptorMismatchWithWarning() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageTrustMode(PluginPackageVerificationMode.WARN);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    byte[] bytes = "plugin".getBytes("UTF-8");
    Files.write(pluginPath, bytes);
    writeManifest(pluginPath, "other", "1.0.0", sha256(bytes));

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertTrue(result.isValid());
    assertTrue(result.isWarning());
  }

  @Test
  public void warnModeRecordsMissingTrustRootForSignatureMetadata() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginPackageTrustMode(PluginPackageVerificationMode.WARN);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    byte[] bytes = "plugin".getBytes("UTF-8");
    Files.write(pluginPath, bytes);
    writeSignedManifest(pluginPath, "sample", "1.0.0", sha256(bytes));

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertTrue(result.isValid());
    assertTrue(result.isWarning());
  }

  @Test
  public void productionProfileRejectsUnsignedManifest() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setProductionProfileEnabled(true);
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    byte[] bytes = "plugin".getBytes("UTF-8");
    Files.write(pluginPath, bytes);
    writeManifest(pluginPath, "sample", "1.0.0", sha256(bytes));

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertFalse(result.isValid());
  }

  @Test
  public void productionProfileAcceptsConfiguredTrustRoot() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setProductionProfileEnabled(true);
    properties.setPluginPackageTrustRoots(new String[]{"release-key"});
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    byte[] bytes = "plugin".getBytes("UTF-8");
    Files.write(pluginPath, bytes);
    writeSignedManifest(pluginPath, "sample", "1.0.0", sha256(bytes), "release-key");

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertTrue(result.isValid());
    assertFalse(result.isWarning());
  }

  private DefaultPluginDescriptor descriptor(String pluginId, String version) {
    return new DefaultPluginDescriptor(pluginId, pluginId, "net.xdob.SamplePlugin",
        version, "", "test", "Apache-2.0");
  }

  private void writeManifest(Path pluginPath, String pluginId, String version, String sha256)
      throws Exception {
    String json = "{"
        + "\"pluginId\":\"" + pluginId + "\","
        + "\"pluginVersion\":\"" + version + "\","
        + "\"packageSha256\":\"" + sha256 + "\""
        + "}";
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".pf4boot-trust.json"),
        json.getBytes("UTF-8"));
  }

  private void writeSignedManifest(Path pluginPath, String pluginId, String version, String sha256)
      throws Exception {
    writeSignedManifest(pluginPath, pluginId, version, sha256, "missing-key");
  }

  private void writeSignedManifest(Path pluginPath, String pluginId, String version, String sha256, String keyId)
      throws Exception {
    String json = "{"
        + "\"pluginId\":\"" + pluginId + "\","
        + "\"pluginVersion\":\"" + version + "\","
        + "\"packageSha256\":\"" + sha256 + "\","
        + "\"signature\":{"
        + "\"algorithm\":\"SHA256withRSA\","
        + "\"keyId\":\"" + keyId + "\","
        + "\"value\":\"signature-value\""
        + "}"
        + "}";
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".pf4boot-trust.json"),
        json.getBytes("UTF-8"));
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
