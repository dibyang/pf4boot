package net.xdob.pf4boot;

import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.PluginDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 默认插件包校验器。
 *
 * <p>当前实现支持插件包旁路 SHA-256 文件，例如 {@code plugin.zip.sha256}。
 * 为保持兼容，目录插件和关闭模式不会被校验；WARN 模式仅记录告警，ENFORCE 模式交由
 * 插件管理器阻断加载。</p>
 */
public class DefaultPluginPackageVerifier implements PluginPackageVerifier {

  private static final int BUFFER_SIZE = 8192;

  private final Pf4bootProperties properties;

  public DefaultPluginPackageVerifier(Pf4bootProperties properties) {
    this.properties = properties;
  }

  @Override
  public PluginPackageVerificationResult verify(Path pluginPath, PluginDescriptor pluginDescriptor) {
    PluginPackageVerificationMode mode = properties.getPluginPackageVerificationMode();
    if (mode == null || PluginPackageVerificationMode.DISABLED.equals(mode) || Files.isDirectory(pluginPath)) {
      return PluginPackageVerificationResult.ok();
    }

    Path checksumPath = checksumPath(pluginPath);
    if (!Files.exists(checksumPath)) {
      return result(mode, "Plugin package checksum file not found: " + checksumPath);
    }

    try {
      String expected = readExpectedChecksum(checksumPath);
      String actual = sha256(pluginPath);
      if (!expected.equalsIgnoreCase(actual)) {
        return result(mode, "Plugin package checksum mismatch: " + pluginPath);
      }
      return PluginPackageVerificationResult.ok();
    } catch (Exception e) {
      return result(mode, "Plugin package checksum verification failed: " + e.getMessage());
    }
  }

  private Path checksumPath(Path pluginPath) {
    return pluginPath.resolveSibling(pluginPath.getFileName().toString()
        + properties.getPluginPackageChecksumExtension());
  }

  private PluginPackageVerificationResult result(PluginPackageVerificationMode mode, String message) {
    if (PluginPackageVerificationMode.ENFORCE.equals(mode)) {
      return PluginPackageVerificationResult.fail(message);
    }
    return PluginPackageVerificationResult.warn(message);
  }

  private String readExpectedChecksum(Path checksumPath) throws IOException {
    String content = new String(Files.readAllBytes(checksumPath), "UTF-8").trim();
    int whitespaceIndex = firstWhitespaceIndex(content);
    if (whitespaceIndex >= 0) {
      content = content.substring(0, whitespaceIndex);
    }
    return content.toLowerCase(Locale.ENGLISH);
  }

  private int firstWhitespaceIndex(String content) {
    for (int i = 0; i < content.length(); i++) {
      if (Character.isWhitespace(content.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  private String sha256(Path pluginPath) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream inputStream = Files.newInputStream(pluginPath)) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        if (read > 0) {
          digest.update(buffer, 0, read);
        }
      }
    }
    return toHex(digest.digest());
  }

  private String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      String hex = Integer.toHexString(value & 0xff);
      if (hex.length() == 1) {
        builder.append('0');
      }
      builder.append(hex);
    }
    return builder.toString();
  }
}
