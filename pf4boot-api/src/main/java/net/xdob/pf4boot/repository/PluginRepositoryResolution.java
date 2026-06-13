package net.xdob.pf4boot.repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件仓库 release 解析结果。
 */
public class PluginRepositoryResolution {

  private final PluginRepositoryStatus status;
  private final PluginReleaseRecord releaseRecord;
  private final Path packagePath;
  private final Path trustManifestPath;
  private final List<String> warnings;

  public PluginRepositoryResolution(
      PluginRepositoryStatus status,
      PluginReleaseRecord releaseRecord,
      Path packagePath,
      Path trustManifestPath,
      List<String> warnings) {
    this.status = status;
    this.releaseRecord = releaseRecord;
    this.packagePath = packagePath;
    this.trustManifestPath = trustManifestPath;
    this.warnings = warnings == null
        ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<String>(warnings));
  }

  public PluginRepositoryStatus getStatus() {
    return status;
  }

  public PluginReleaseRecord getReleaseRecord() {
    return releaseRecord;
  }

  public Path getPackagePath() {
    return packagePath;
  }

  public Path getTrustManifestPath() {
    return trustManifestPath;
  }

  public List<String> getWarnings() {
    return warnings;
  }
}
