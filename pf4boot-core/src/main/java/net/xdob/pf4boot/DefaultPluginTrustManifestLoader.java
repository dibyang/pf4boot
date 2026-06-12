package net.xdob.pf4boot;

import net.xdob.pf4boot.trust.PluginSignatureMetadata;
import net.xdob.pf4boot.trust.PluginTrustManifest;
import net.xdob.pf4boot.trust.PluginTrustManifestLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认的 trust manifest 加载器。
 * 约定 sidecar 清单命名规则为：plugin.zip + 扩展名（如 `.pf4boot-trust.json`）。
 */
public class DefaultPluginTrustManifestLoader implements PluginTrustManifestLoader {

  private static final Pattern STRING_VALUE = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"]*)*)\"");

  @Override
  public PluginTrustManifest load(Path pluginPath, String sidecarExtension) {
    if (pluginPath == null) {
      throw new IllegalArgumentException("pluginPath must not be null");
    }
    if (Files.isDirectory(pluginPath)) {
      return null;
    }
    String extension = sidecarExtension == null || sidecarExtension.trim().isEmpty()
        ? ".pf4boot-trust.json"
        : sidecarExtension;
    Path manifestPath = pluginPath.resolveSibling(pluginPath.getFileName() + extension);
    if (!Files.exists(manifestPath)) {
      return null;
    }
    String content = readAllText(manifestPath);
    return parse(content);
  }

  private String readAllText(Path manifestPath) {
    try {
      return new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalArgumentException("Read trust manifest failed: " + manifestPath, e);
    }
  }

  private PluginTrustManifest parse(String json) {
    if (json == null) {
      throw new IllegalArgumentException("Trust manifest content is null");
    }
    String normalized = json.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("Trust manifest content is empty");
    }
    if (!normalized.startsWith("{") || !normalized.endsWith("}")) {
      throw new IllegalArgumentException("Trust manifest must be json object");
    }
    PluginTrustManifest manifest = new PluginTrustManifest();
    manifest.setPluginId(stringField(normalized, "pluginId"));
    manifest.setPluginVersion(stringField(normalized, "pluginVersion"));
    manifest.setPackageSha256(stringField(normalized, "packageSha256"));
    PluginSignatureMetadata signature = parseSignature(normalized);
    if (signature != null) {
      manifest.setSignature(signature);
    }
    return manifest;
  }

  private PluginSignatureMetadata parseSignature(String json) {
    String block = extractObject(json, "signature");
    if (block == null) {
      return null;
    }
    PluginSignatureMetadata signature = new PluginSignatureMetadata();
    signature.setAlgorithm(stringField(block, "algorithm"));
    signature.setKeyId(stringField(block, "keyId"));
    signature.setValue(stringField(block, "value"));
    signature.setCertificateChain(stringField(block, "certificateChain"));
    return signature;
  }

  private String stringField(String json, String fieldName) {
    Matcher matcher = STRING_VALUE.matcher(json);
    while (matcher.find()) {
      if (fieldName.equals(matcher.group(1))) {
        return unescape(matcher.group(2));
      }
    }
    return null;
  }

  private String extractObject(String json, String fieldName) {
    String marker = "\"" + fieldName + "\"";
    int start = json.indexOf(marker);
    if (start < 0) {
      return null;
    }
    int braceStart = json.indexOf("{", start);
    if (braceStart < 0) {
      return null;
    }
    int depth = 0;
    for (int i = braceStart; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return json.substring(braceStart, i + 1);
        }
      }
    }
    return null;
  }

  private String unescape(String source) {
    if (source == null) {
      return null;
    }
    return source.replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t");
  }
}
