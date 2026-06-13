package net.xdob.pf4boot.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 离线插件仓库索引。
 */
public class PluginRepositoryIndex {

  private int schemaVersion;
  private String repositoryId;
  private long generatedAt;
  private List<PluginReleaseRecord> releases = new ArrayList<>();
  private String signature;

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public String getRepositoryId() {
    return repositoryId;
  }

  public void setRepositoryId(String repositoryId) {
    this.repositoryId = repositoryId;
  }

  public long getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(long generatedAt) {
    this.generatedAt = generatedAt;
  }

  public List<PluginReleaseRecord> getReleases() {
    return Collections.unmodifiableList(releases);
  }

  public void setReleases(List<PluginReleaseRecord> releases) {
    this.releases = releases == null
        ? new ArrayList<PluginReleaseRecord>()
        : new ArrayList<PluginReleaseRecord>(releases);
  }

  public String getSignature() {
    return signature;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }
}
