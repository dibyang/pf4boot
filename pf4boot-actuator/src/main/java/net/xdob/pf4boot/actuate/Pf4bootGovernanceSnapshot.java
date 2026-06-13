package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.deployment.PluginDeploymentMetricsSnapshot;
import net.xdob.pf4boot.diagnostic.PluginCleanupReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PF4Boot 治理只读摘要。
 *
 * <p>该 DTO 面向 actuator 输出，字段全部来自只读快照、配置和诊断结果，不包含可执行命令
 * 或敏感请求信息。</p>
 */
public class Pf4bootGovernanceSnapshot {

  private final int pluginCount;
  private final int startedPluginCount;
  private final int failedPluginCount;
  private final String trustMode;
  private final String capabilityPrecheckMode;
  private final String compatibilityPrecheckMode;
  private final String trustManifestExtension;
  private final boolean repositoryEnabled;
  private final String repositoryType;
  private final boolean repositoryLocationConfigured;
  private final boolean repositoryReplaceEnabled;
  private final boolean repositoryCacheConfigured;
  private final PluginDeploymentMetricsSnapshot deploymentSummary;
  private final List<PluginCleanupReport> cleanupReports;
  private final List<String> warnings;

  public Pf4bootGovernanceSnapshot(
      int pluginCount,
      int startedPluginCount,
      int failedPluginCount,
      String trustMode,
      String capabilityPrecheckMode,
      String compatibilityPrecheckMode,
      String trustManifestExtension,
      boolean repositoryEnabled,
      String repositoryType,
      boolean repositoryLocationConfigured,
      boolean repositoryReplaceEnabled,
      boolean repositoryCacheConfigured,
      PluginDeploymentMetricsSnapshot deploymentSummary,
      List<PluginCleanupReport> cleanupReports,
      List<String> warnings) {
    this.pluginCount = pluginCount;
    this.startedPluginCount = startedPluginCount;
    this.failedPluginCount = failedPluginCount;
    this.trustMode = trustMode;
    this.capabilityPrecheckMode = capabilityPrecheckMode;
    this.compatibilityPrecheckMode = compatibilityPrecheckMode;
    this.trustManifestExtension = trustManifestExtension;
    this.repositoryEnabled = repositoryEnabled;
    this.repositoryType = repositoryType;
    this.repositoryLocationConfigured = repositoryLocationConfigured;
    this.repositoryReplaceEnabled = repositoryReplaceEnabled;
    this.repositoryCacheConfigured = repositoryCacheConfigured;
    this.deploymentSummary = deploymentSummary;
    this.cleanupReports = immutableList(cleanupReports);
    this.warnings = immutableList(warnings);
  }

  public int getPluginCount() {
    return pluginCount;
  }

  public int getStartedPluginCount() {
    return startedPluginCount;
  }

  public int getFailedPluginCount() {
    return failedPluginCount;
  }

  public String getTrustMode() {
    return trustMode;
  }

  public String getCapabilityPrecheckMode() {
    return capabilityPrecheckMode;
  }

  public String getCompatibilityPrecheckMode() {
    return compatibilityPrecheckMode;
  }

  public String getTrustManifestExtension() {
    return trustManifestExtension;
  }

  public boolean isRepositoryEnabled() {
    return repositoryEnabled;
  }

  public String getRepositoryType() {
    return repositoryType;
  }

  public boolean isRepositoryLocationConfigured() {
    return repositoryLocationConfigured;
  }

  public boolean isRepositoryReplaceEnabled() {
    return repositoryReplaceEnabled;
  }

  public boolean isRepositoryCacheConfigured() {
    return repositoryCacheConfigured;
  }

  public PluginDeploymentMetricsSnapshot getDeploymentSummary() {
    return deploymentSummary;
  }

  public List<PluginCleanupReport> getCleanupReports() {
    return cleanupReports;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  private <T> List<T> immutableList(List<T> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<T>(source));
  }
}
