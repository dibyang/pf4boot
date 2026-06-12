package net.xdob.pf4boot;

import net.xdob.pf4boot.trust.PluginTrustManifest;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DefaultPluginTrustManifestLoaderTest {

  @Test
  public void loadsSidecarManifest() throws Exception {
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".pf4boot-trust.json"),
        manifestJson("sample", "1.0.0", "abc").getBytes("UTF-8"));

    PluginTrustManifest manifest = new DefaultPluginTrustManifestLoader()
        .load(pluginPath, ".pf4boot-trust.json");

    assertNotNull(manifest);
    assertEquals("sample", manifest.getPluginId());
    assertEquals("1.0.0", manifest.getPluginVersion());
    assertEquals("abc", manifest.getPackageSha256());
    assertNotNull(manifest.getSignature());
    assertEquals("SHA256withRSA", manifest.getSignature().getAlgorithm());
    assertEquals("local-dev-key", manifest.getSignature().getKeyId());
  }

  @Test
  public void returnsNullWhenSidecarManifestMissing() throws Exception {
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");

    PluginTrustManifest manifest = new DefaultPluginTrustManifestLoader()
        .load(pluginPath, ".pf4boot-trust.json");

    assertNull(manifest);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsInvalidJson() throws Exception {
    Path pluginPath = Files.createTempFile("pf4boot-plugin", ".zip");
    Files.write(pluginPath.resolveSibling(pluginPath.getFileName().toString() + ".pf4boot-trust.json"),
        "not json".getBytes("UTF-8"));

    new DefaultPluginTrustManifestLoader().load(pluginPath, ".pf4boot-trust.json");
  }

  private String manifestJson(String pluginId, String version, String sha256) {
    return "{"
        + "\"pluginId\":\"" + pluginId + "\","
        + "\"pluginVersion\":\"" + version + "\","
        + "\"packageSha256\":\"" + sha256 + "\","
        + "\"signature\":{"
        + "\"algorithm\":\"SHA256withRSA\","
        + "\"keyId\":\"local-dev-key\","
        + "\"value\":\"signature-value\""
        + "}"
        + "}";
  }
}
