package net.xdob.pf4boot;

import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;
import org.pf4j.DefaultPluginDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.Collections;

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
  public void productionProfileRejectsTrustRootWithoutPublicKey() throws Exception {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setProductionProfileEnabled(true);
    properties.setPluginPackageTrustRoots(new String[]{"release-key"});
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    byte[] bytes = "plugin".getBytes("UTF-8");
    Files.write(pluginPath, bytes);
    writeSignedManifest(pluginPath, "sample", "1.0.0", sha256(bytes), "release-key");

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertFalse(result.isValid());
  }

  @Test
  public void productionProfileAcceptsValidCryptographicSignature() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setProductionProfileEnabled(true);
    properties.setPluginPackageTrustRootPublicKeys(Collections.singletonMap(
        "release-key",
        pemPublicKey(keyPair)));
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    byte[] bytes = "plugin".getBytes("UTF-8");
    Files.write(pluginPath, bytes);
    writeSignedManifest(pluginPath, "sample", "1.0.0", sha256(bytes), "release-key", keyPair);

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.0"));

    assertTrue(result.isValid());
    assertFalse(result.isWarning());
  }

  @Test
  public void productionProfileRejectsInvalidCryptographicSignature() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setProductionProfileEnabled(true);
    properties.setPluginPackageTrustRootPublicKeys(Collections.singletonMap(
        "release-key",
        pemPublicKey(keyPair)));
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    byte[] bytes = "plugin".getBytes("UTF-8");
    Files.write(pluginPath, bytes);
    writeSignedManifest(pluginPath, "sample", "1.0.0", sha256(bytes), "release-key", keyPair);
    String manifestName = pluginPath.getFileName().toString() + ".pf4boot-trust.json";
    Path manifestPath = pluginPath.resolveSibling(manifestName);
    String tampered = new String(Files.readAllBytes(manifestPath), "UTF-8")
        .replace("\"pluginVersion\":\"1.0.0\"", "\"pluginVersion\":\"1.0.1\"");
    Files.write(manifestPath, tampered.getBytes("UTF-8"));

    PluginPackageVerificationResult result = new DefaultPluginPackageTrustVerifier(properties)
        .verify(pluginPath, descriptor("sample", "1.0.1"));

    assertFalse(result.isValid());
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
    writeSignedManifest(pluginPath, pluginId, version, sha256, keyId, null);
  }

  private void writeSignedManifest(
      Path pluginPath,
      String pluginId,
      String version,
      String sha256,
      String keyId,
      KeyPair keyPair)
      throws Exception {
    String payload = "{"
        + "\"pluginId\":\"" + pluginId + "\","
        + "\"pluginVersion\":\"" + version + "\","
        + "\"packageSha256\":\"" + sha256 + "\""
        + "}";
    String signatureValue = keyPair == null ? "signature-value" : sign(payload, keyPair);
    String json = payload.substring(0, payload.length() - 1)
        + ","
        + "\"signature\":{"
        + "\"algorithm\":\"SHA256withRSA\","
        + "\"keyId\":\"" + keyId + "\","
        + "\"value\":\"" + signatureValue + "\""
        + "}"
        + "}";
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".pf4boot-trust.json"),
        json.getBytes("UTF-8"));
  }

  private KeyPair rsaKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private String sign(String payload, KeyPair keyPair) throws Exception {
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(keyPair.getPrivate());
    signature.update(payload.getBytes("UTF-8"));
    return Base64.getEncoder().encodeToString(signature.sign());
  }

  private String pemPublicKey(KeyPair keyPair) {
    String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPublic().getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
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
