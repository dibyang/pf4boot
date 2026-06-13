package net.xdob.pf4boot.repository;

import net.xdob.pf4boot.PluginPackageVerificationMode;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import net.xdob.pf4boot.version.DefaultVersionRangeMatcher;
import net.xdob.pf4boot.version.VersionMatchResult;
import net.xdob.pf4boot.version.VersionRangeMatcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于本地离线索引的插件仓库解析器。
 *
 * <p>解析器只读取配置目录下的 {@code repository-index.json} 和相对包路径，不执行网络
 * 下载。所有 release 路径都会 normalize 并验证仍位于仓库根目录内。</p>
 */
public class OfflineIndexPluginRepositoryResolver implements PluginRepositoryResolver {

  private static final String INDEX_FILE = "repository-index.json";
  private static final Pattern STRING_VALUE = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"]*)*)\"");
  private static final Pattern NUMBER_VALUE = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([0-9]+)");
  private static final Pattern BOOLEAN_VALUE = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(true|false)");

  private final Pf4bootProperties properties;
  private final VersionRangeMatcher versionRangeMatcher;

  public OfflineIndexPluginRepositoryResolver(Pf4bootProperties properties) {
    this(properties, new DefaultVersionRangeMatcher());
  }

  public OfflineIndexPluginRepositoryResolver(Pf4bootProperties properties, VersionRangeMatcher versionRangeMatcher) {
    this.properties = properties == null ? new Pf4bootProperties() : properties;
    this.versionRangeMatcher = versionRangeMatcher == null
        ? new DefaultVersionRangeMatcher()
        : versionRangeMatcher;
  }

  @Override
  public PluginRepositoryIndex loadIndex() {
    ensureEnabled();
    Path root = repositoryRoot();
    Path indexPath = root.resolve(INDEX_FILE).normalize();
    ensureInside(root, indexPath, "Repository index path escapes root");
    if (!Files.exists(indexPath)) {
      throw new IllegalStateException("Repository index not found: " + INDEX_FILE);
    }
    try {
      return parseIndex(new String(Files.readAllBytes(indexPath), StandardCharsets.UTF_8));
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Read repository index failed", e);
    }
  }

  @Override
  public PluginRepositoryResolution resolve(PluginReleaseRequest request) {
    ensureEnabled();
    if (request == null || isBlank(request.getPluginId())) {
      throw new IllegalArgumentException("pluginId is required for repository release");
    }
    PluginRepositoryIndex index = loadIndex();
    PluginReleaseRecord record = selectRelease(index, request);
    Path root = repositoryRoot();
    Path packagePath = resolveInside(root, record.getPackagePath(), "Release package path escapes root");
    if (!Files.exists(packagePath)) {
      throw new IllegalStateException("Release package not found: " + record.getPackagePath());
    }
    verifySha256(packagePath, record);
    Path trustPath = isBlank(record.getTrustManifestPath())
        ? null
        : resolveInside(root, record.getTrustManifestPath(), "Release trust manifest path escapes root");
    if (trustPath != null && !Files.exists(trustPath)) {
      throw new IllegalStateException("Release trust manifest not found: " + record.getTrustManifestPath());
    }
    List<String> warnings = new ArrayList<String>();
    if (isBlank(index.getSignature())
        && !PluginPackageVerificationMode.DISABLED.equals(properties.getPluginRepositoryTrustMode())) {
      warnings.add("repository index signature is missing");
    }
    return new PluginRepositoryResolution(
        PluginRepositoryStatus.PACKAGE_VERIFIED, record, packagePath, trustPath, warnings);
  }

  private PluginReleaseRecord selectRelease(PluginRepositoryIndex index, PluginReleaseRequest request) {
    PluginReleaseRecord best = null;
    for (PluginReleaseRecord release : index.getReleases()) {
      if (!request.getPluginId().equals(release.getPluginId())) {
        continue;
      }
      if (request.isRollback() && !release.isRollbackCandidate()) {
        continue;
      }
      if (!isBlank(request.getVersion()) && !request.getVersion().equals(release.getVersion())) {
        continue;
      }
      if (!isBlank(request.getVersionRange())) {
        VersionMatchResult match = versionRangeMatcher.matches(release.getVersion(), request.getVersionRange());
        if (!match.isValid()) {
          throw new IllegalArgumentException("Repository versionRange invalid: " + match.getMessage());
        }
        if (!match.isMatched()) {
          continue;
        }
      }
      if (best == null || compareVersion(release.getVersion(), best.getVersion()) > 0) {
        best = release;
      }
    }
    if (best == null) {
      throw new IllegalStateException("Repository release not found: " + request.getPluginId());
    }
    return best;
  }

  private PluginRepositoryIndex parseIndex(String json) {
    if (isBlank(json) || !json.trim().startsWith("{") || !json.trim().endsWith("}")) {
      throw new IllegalArgumentException("Repository index must be json object");
    }
    PluginRepositoryIndex index = new PluginRepositoryIndex();
    index.setSchemaVersion(intField(json, "schemaVersion"));
    if (index.getSchemaVersion() != 1) {
      throw new IllegalArgumentException("Unsupported repository index schemaVersion: " + index.getSchemaVersion());
    }
    index.setRepositoryId(stringField(json, "repositoryId"));
    index.setGeneratedAt(longField(json, "generatedAt"));
    index.setSignature(stringField(json, "signature"));
    List<PluginReleaseRecord> releases = new ArrayList<PluginReleaseRecord>();
    for (String item : splitObjects(extractArray(json, "releases"))) {
      PluginReleaseRecord record = new PluginReleaseRecord();
      record.setRepositoryId(index.getRepositoryId());
      record.setPluginId(stringField(item, "pluginId"));
      record.setVersion(stringField(item, "version"));
      record.setPackagePath(stringField(item, "packagePath"));
      record.setPackageSha256(stringField(item, "packageSha256"));
      record.setTrustManifestPath(stringField(item, "trustManifestPath"));
      record.setRolloutPolicy(stringField(item, "rolloutPolicy"));
      Boolean rollback = booleanField(item, "rollbackCandidate");
      record.setRollbackCandidate(rollback != null && rollback);
      record.setAttributes(parseAttributes(item));
      releases.add(record);
    }
    index.setReleases(releases);
    return index;
  }

  private Map<String, String> parseAttributes(String json) {
    String block = extractObject(json, "attributes");
    Map<String, String> attributes = new LinkedHashMap<String, String>();
    if (block == null) {
      return attributes;
    }
    Matcher matcher = STRING_VALUE.matcher(block);
    while (matcher.find()) {
      attributes.put(matcher.group(1), unescape(matcher.group(2)));
    }
    return attributes;
  }

  private Path repositoryRoot() {
    if (isBlank(properties.getPluginRepositoryLocation())) {
      throw new IllegalStateException("Plugin repository location is not configured");
    }
    return Paths.get(properties.getPluginRepositoryLocation()).toAbsolutePath().normalize();
  }

  private void ensureEnabled() {
    if (!properties.isPluginRepositoryEnabled()) {
      throw new IllegalStateException("Plugin repository is disabled");
    }
    if (!"offline-index".equalsIgnoreCase(properties.getPluginRepositoryType())) {
      throw new IllegalStateException("Unsupported plugin repository type: " + properties.getPluginRepositoryType());
    }
  }

  private Path resolveInside(Path root, String relativePath, String message) {
    if (isBlank(relativePath)) {
      throw new IllegalArgumentException("Repository relative path is empty");
    }
    Path relative = Paths.get(relativePath);
    if (relative.isAbsolute()) {
      throw new IllegalArgumentException(message);
    }
    Path resolved = root.resolve(relative).toAbsolutePath().normalize();
    ensureInside(root, resolved, message);
    return resolved;
  }

  private void ensureInside(Path root, Path candidate, String message) {
    if (!candidate.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
      throw new IllegalArgumentException(message);
    }
  }

  private void verifySha256(Path packagePath, PluginReleaseRecord record) {
    if (isBlank(record.getPackageSha256())) {
      throw new IllegalArgumentException("Release packageSha256 is required");
    }
    String actual = sha256(packagePath);
    if (!record.getPackageSha256().equalsIgnoreCase(actual)) {
      throw new IllegalStateException("Release package sha256 mismatch: " + record.getPluginId());
    }
  }

  private String sha256(Path path) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(Files.readAllBytes(path));
      StringBuilder builder = new StringBuilder();
      for (byte b : bytes) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (Exception e) {
      throw new IllegalStateException("Calculate package sha256 failed", e);
    }
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

  private int intField(String json, String fieldName) {
    return (int) longField(json, fieldName);
  }

  private long longField(String json, String fieldName) {
    Matcher matcher = NUMBER_VALUE.matcher(json);
    while (matcher.find()) {
      if (fieldName.equals(matcher.group(1))) {
        return Long.parseLong(matcher.group(2));
      }
    }
    return 0;
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
    int start = valueStart(json, fieldName, '{');
    if (start < 0) {
      return null;
    }
    int end = matchingEnd(json, start, '{', '}');
    return end < 0 ? null : json.substring(start, end + 1);
  }

  private String extractArray(String json, String fieldName) {
    int start = valueStart(json, fieldName, '[');
    if (start < 0) {
      return null;
    }
    int end = matchingEnd(json, start, '[', ']');
    return end < 0 ? null : json.substring(start, end + 1);
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
    List<String> objects = new ArrayList<String>();
    if (array == null || array.length() < 2) {
      return objects;
    }
    int index = 1;
    while (index < array.length() - 1) {
      if (array.charAt(index) == '{') {
        int end = matchingEnd(array, index, '{', '}');
        if (end < 0) {
          throw new IllegalArgumentException("Repository release array contains unclosed object");
        }
        objects.add(array.substring(index, end + 1));
        index = end + 1;
      } else {
        index++;
      }
    }
    return objects;
  }

  private int compareVersion(String left, String right) {
    String[] leftParts = left == null ? new String[0] : left.split("[\\.\\-_+]");
    String[] rightParts = right == null ? new String[0] : right.split("[\\.\\-_+]");
    int max = Math.max(leftParts.length, rightParts.length);
    for (int i = 0; i < max; i++) {
      int l = i < leftParts.length && isNumber(leftParts[i]) ? Integer.parseInt(leftParts[i]) : 0;
      int r = i < rightParts.length && isNumber(rightParts[i]) ? Integer.parseInt(rightParts[i]) : 0;
      if (l != r) {
        return l - r;
      }
    }
    return 0;
  }

  private boolean isNumber(String value) {
    if (isBlank(value)) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        return false;
      }
    }
    return true;
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

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
