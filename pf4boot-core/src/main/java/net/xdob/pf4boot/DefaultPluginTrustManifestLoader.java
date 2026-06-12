package net.xdob.pf4boot;

import net.xdob.pf4boot.capability.PluginCapability;
import net.xdob.pf4boot.capability.PluginCapabilityRequirement;
import net.xdob.pf4boot.trust.PluginSignatureMetadata;
import net.xdob.pf4boot.trust.PluginTrustManifest;
import net.xdob.pf4boot.trust.PluginTrustManifestLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认 trust manifest 加载器。
 *
 * <p>约定 sidecar 清单命名规则为 {@code pluginPath + .pf4boot-trust.json}，同时支持 zip
 * 包和测试/开发场景下的 exploded 插件目录。本解析器只覆盖框架 manifest 需要的有限 JSON
 * 子集，避免给 Java 8 核心模块新增 JSON 依赖。</p>
 */
public class DefaultPluginTrustManifestLoader implements PluginTrustManifestLoader {

  private static final Pattern STRING_VALUE = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"]*)*)\"");
  private static final Pattern BOOLEAN_VALUE = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(true|false)");

  @Override
  public PluginTrustManifest load(Path pluginPath, String sidecarExtension) {
    if (pluginPath == null) {
      throw new IllegalArgumentException("pluginPath must not be null");
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
    parseCapabilities(normalized, manifest);
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

  private void parseCapabilities(String json, PluginTrustManifest manifest) {
    String block = extractObject(json, "capabilities");
    if (block == null) {
      return;
    }
    manifest.setProvidedCapabilities(parseProvides(extractArray(block, "provides")));
    manifest.setRequiredCapabilities(parseRequires(extractArray(block, "requires")));
  }

  private List<PluginCapability> parseProvides(String array) {
    List<PluginCapability> capabilities = new ArrayList<>();
    for (String item : splitObjects(array)) {
      PluginCapability capability = new PluginCapability();
      capability.setName(stringField(item, "name"));
      capability.setVersion(stringField(item, "version"));
      capability.setScope(stringField(item, "scope"));
      capability.setAttributes(parseAttributes(item));
      capabilities.add(capability);
    }
    return capabilities;
  }

  private List<PluginCapabilityRequirement> parseRequires(String array) {
    List<PluginCapabilityRequirement> requirements = new ArrayList<>();
    for (String item : splitObjects(array)) {
      PluginCapabilityRequirement requirement = new PluginCapabilityRequirement();
      requirement.setName(stringField(item, "name"));
      requirement.setVersionRange(stringField(item, "versionRange"));
      Boolean required = booleanField(item, "required");
      requirement.setRequired(required == null || required);
      requirement.setAttributes(parseAttributes(item));
      requirements.add(requirement);
    }
    return requirements;
  }

  private Map<String, String> parseAttributes(String json) {
    String block = extractObject(json, "attributes");
    Map<String, String> attributes = new LinkedHashMap<>();
    if (block == null) {
      return attributes;
    }
    Matcher matcher = STRING_VALUE.matcher(block);
    while (matcher.find()) {
      attributes.put(matcher.group(1), unescape(matcher.group(2)));
    }
    return attributes;
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

  private Boolean booleanField(String json, String fieldName) {
    Matcher matcher = BOOLEAN_VALUE.matcher(json);
    while (matcher.find()) {
      if (fieldName.equals(matcher.group(1))) {
        return Boolean.valueOf(matcher.group(2));
      }
    }
    return null;
  }

  private String extractObject(String json, String fieldName) {
    int braceStart = valueStart(json, fieldName, '{');
    if (braceStart < 0) {
      return null;
    }
    int end = matchingEnd(json, braceStart, '{', '}');
    return end < 0 ? null : json.substring(braceStart, end + 1);
  }

  private String extractArray(String json, String fieldName) {
    int arrayStart = valueStart(json, fieldName, '[');
    if (arrayStart < 0) {
      return null;
    }
    int end = matchingEnd(json, arrayStart, '[', ']');
    return end < 0 ? null : json.substring(arrayStart, end + 1);
  }

  private int valueStart(String json, String fieldName, char expectedStart) {
    String marker = "\"" + fieldName + "\"";
    int start = json.indexOf(marker);
    if (start < 0) {
      return -1;
    }
    int colon = json.indexOf(':', start + marker.length());
    if (colon < 0) {
      return -1;
    }
    for (int i = colon + 1; i < json.length(); i++) {
      if (!Character.isWhitespace(json.charAt(i))) {
        return json.charAt(i) == expectedStart ? i : -1;
      }
    }
    return -1;
  }

  private int matchingEnd(String json, int start, char open, char close) {
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = start; i < json.length(); i++) {
      char c = json.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private List<String> splitObjects(String array) {
    List<String> objects = new ArrayList<>();
    if (array == null || array.length() < 2) {
      return objects;
    }
    int index = 1;
    while (index < array.length() - 1) {
      char c = array.charAt(index);
      if (c == '{') {
        int end = matchingEnd(array, index, '{', '}');
        if (end < 0) {
          throw new IllegalArgumentException("Capability array contains unclosed object");
        }
        objects.add(array.substring(index, end + 1));
        index = end + 1;
      } else {
        index++;
      }
    }
    return objects;
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
