package net.xdob.pf4boot.repository;

import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfflineIndexPluginRepositoryResolverTest {

  @Test(expected = IllegalStateException.class)
  public void disabledRepositoryDoesNotLoadIndex() {
    new OfflineIndexPluginRepositoryResolver(new Pf4bootProperties()).loadIndex();
  }

  @Test
  public void loadsValidIndex() throws Exception {
    Path root = Files.createTempDirectory("pf4boot-repo");
    Path plugin = writePackage(root, "plugins/sample.zip", "plugin");
    writeIndex(root, release("sample", "1.0.0", "plugins/sample.zip", sha256(plugin), false));

    PluginRepositoryIndex index = resolver(root).loadIndex();

    assertEquals("sample-repo", index.getRepositoryId());
    assertEquals(1, index.getReleases().size());
    assertEquals("sample", index.getReleases().get(0).getPluginId());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsPathTraversal() throws Exception {
    Path root = Files.createTempDirectory("pf4boot-repo");
    writeIndex(root, release("sample", "1.0.0", "../sample.zip", "abc", false));

    resolver(root).resolve(request("sample", "1.0.0"));
  }

  @Test(expected = IllegalStateException.class)
  public void rejectsPackageChecksumMismatch() throws Exception {
    Path root = Files.createTempDirectory("pf4boot-repo");
    writePackage(root, "plugins/sample.zip", "plugin");
    writeIndex(root, release("sample", "1.0.0", "plugins/sample.zip", "bad", false));

    resolver(root).resolve(request("sample", "1.0.0"));
  }

  @Test
  public void selectsExactVersion() throws Exception {
    Path root = Files.createTempDirectory("pf4boot-repo");
    Path plugin = writePackage(root, "plugins/sample.zip", "plugin");
    writeIndex(root, release("sample", "1.0.0", "plugins/sample.zip", sha256(plugin), false));

    PluginRepositoryResolution resolution = resolver(root).resolve(request("sample", "1.0.0"));

    assertEquals(PluginRepositoryStatus.PACKAGE_VERIFIED, resolution.getStatus());
    assertEquals("1.0.0", resolution.getReleaseRecord().getVersion());
    assertTrue(resolution.getPackagePath().endsWith("sample.zip"));
  }

  @Test
  public void selectsRollbackCandidate() throws Exception {
    Path root = Files.createTempDirectory("pf4boot-repo");
    Path oldPlugin = writePackage(root, "plugins/sample-old.zip", "old");
    Path newPlugin = writePackage(root, "plugins/sample-new.zip", "new");
    writeIndex(root,
        release("sample", "1.0.0", "plugins/sample-old.zip", sha256(oldPlugin), true)
            + ","
            + release("sample", "2.0.0", "plugins/sample-new.zip", sha256(newPlugin), false));
    PluginReleaseRequest request = request("sample", null);
    request.setRollback(true);

    PluginRepositoryResolution resolution = resolver(root).resolve(request);

    assertEquals("1.0.0", resolution.getReleaseRecord().getVersion());
  }

  private OfflineIndexPluginRepositoryResolver resolver(Path root) {
    Pf4bootProperties properties = new Pf4bootProperties();
    properties.setPluginRepositoryEnabled(true);
    properties.setPluginRepositoryLocation(root.toString());
    return new OfflineIndexPluginRepositoryResolver(properties);
  }

  private PluginReleaseRequest request(String pluginId, String version) {
    PluginReleaseRequest request = new PluginReleaseRequest();
    request.setPluginId(pluginId);
    request.setVersion(version);
    return request;
  }

  private Path writePackage(Path root, String relativePath, String content) throws Exception {
    Path path = root.resolve(relativePath);
    Files.createDirectories(path.getParent());
    Files.write(path, content.getBytes("UTF-8"));
    return path;
  }

  private void writeIndex(Path root, String releases) throws Exception {
    String json = "{"
        + "\"schemaVersion\":1,"
        + "\"repositoryId\":\"sample-repo\","
        + "\"generatedAt\":1,"
        + "\"releases\":[" + releases + "]"
        + "}";
    Files.write(root.resolve("repository-index.json"), json.getBytes("UTF-8"));
  }

  private String release(
      String pluginId,
      String version,
      String packagePath,
      String sha256,
      boolean rollback) {
    return "{"
        + "\"pluginId\":\"" + pluginId + "\","
        + "\"version\":\"" + version + "\","
        + "\"packagePath\":\"" + packagePath + "\","
        + "\"packageSha256\":\"" + sha256 + "\","
        + "\"rolloutPolicy\":\"manual\","
        + "\"rollbackCandidate\":" + rollback
        + "}";
  }

  private String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] bytes = digest.digest(Files.readAllBytes(path));
    StringBuilder builder = new StringBuilder();
    for (byte b : bytes) {
      builder.append(String.format("%02x", b));
    }
    return builder.toString();
  }
}
