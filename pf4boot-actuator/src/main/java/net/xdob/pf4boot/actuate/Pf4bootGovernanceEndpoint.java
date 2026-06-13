package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.PluginRuntimeInspector;
import net.xdob.pf4boot.deployment.PluginDeploymentMetricsProvider;
import net.xdob.pf4boot.deployment.PluginDeploymentMetricsSnapshot;
import net.xdob.pf4boot.diagnostic.PluginCleanupReport;
import net.xdob.pf4boot.diagnostic.PluginLifecycleDiagnostic;
import net.xdob.pf4boot.modal.PluginRuntimeSnapshot;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.PluginState;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PF4Boot 治理只读摘要端点。
 *
 * <p>端点聚合插件状态、信任/能力预检配置、部署指标和资源清理诊断摘要。实现只读取快照
 * 和诊断接口，不调用任何插件启停、重载、替换或删除方法。</p>
 */
@Endpoint(id = "pf4bootgovernance")
public class Pf4bootGovernanceEndpoint {

  private final PluginRuntimeInspector pluginRuntimeInspector;
  private final PluginDeploymentMetricsProvider deploymentMetricsProvider;
  private final PluginLifecycleDiagnostic lifecycleDiagnostic;
  private final Pf4bootProperties properties;

  public Pf4bootGovernanceEndpoint(
      PluginRuntimeInspector pluginRuntimeInspector,
      PluginDeploymentMetricsProvider deploymentMetricsProvider,
      PluginLifecycleDiagnostic lifecycleDiagnostic,
      Pf4bootProperties properties) {
    this.pluginRuntimeInspector = pluginRuntimeInspector;
    this.deploymentMetricsProvider = deploymentMetricsProvider;
    this.lifecycleDiagnostic = lifecycleDiagnostic;
    this.properties = properties;
  }

  /**
   * 读取当前插件治理摘要。
   *
   * @return 治理摘要快照
   */
  @ReadOperation
  public Pf4bootGovernanceSnapshot summary() {
    List<String> warnings = new ArrayList<>();
    List<PluginRuntimeSnapshot> snapshots = safeSnapshots(warnings);
    List<PluginCleanupReport> cleanupReports = cleanupReports(snapshots, warnings);
    return new Pf4bootGovernanceSnapshot(
        snapshots.size(),
        countStarted(snapshots),
        countFailed(snapshots),
        properties == null ? "UNKNOWN" : String.valueOf(properties.getPluginPackageTrustMode()),
        properties == null ? "UNKNOWN" : String.valueOf(properties.getPluginCapabilityPrecheckMode()),
        properties == null ? "UNKNOWN" : String.valueOf(properties.getPluginCompatibilityPrecheckMode()),
        properties == null ? "" : properties.getPluginPackageTrustManifestExtension(),
        properties != null && properties.isPluginRepositoryEnabled(),
        properties == null ? "" : properties.getPluginRepositoryType(),
        properties != null && hasText(properties.getPluginRepositoryLocation()),
        properties != null && properties.isPluginRepositoryReplaceEnabled(),
        properties != null && hasText(properties.getPluginRepositoryCacheDirectory()),
        deploymentSummary(),
        cleanupReports,
        warnings);
  }

  private List<PluginRuntimeSnapshot> safeSnapshots(List<String> warnings) {
    try {
      List<PluginRuntimeSnapshot> snapshots = pluginRuntimeInspector.snapshots();
      return snapshots == null ? Collections.<PluginRuntimeSnapshot>emptyList() : snapshots;
    } catch (RuntimeException e) {
      warnings.add("runtime snapshot unavailable");
      return Collections.emptyList();
    }
  }

  private PluginDeploymentMetricsSnapshot deploymentSummary() {
    if (deploymentMetricsProvider == null) {
      return null;
    }
    return deploymentMetricsProvider.snapshot();
  }

  private List<PluginCleanupReport> cleanupReports(
      List<PluginRuntimeSnapshot> snapshots,
      List<String> warnings) {
    if (lifecycleDiagnostic == null) {
      warnings.add("cleanup diagnostic unavailable");
      return Collections.emptyList();
    }
    List<PluginCleanupReport> reports = new ArrayList<>();
    for (PluginRuntimeSnapshot snapshot : snapshots) {
      if (snapshot == null || snapshot.getPluginId() == null) {
        continue;
      }
      try {
        reports.add(lifecycleDiagnostic.inspectAfterStop(snapshot.getPluginId(), null));
      } catch (RuntimeException e) {
        warnings.add("cleanup diagnostic failed: " + snapshot.getPluginId());
      }
    }
    return reports;
  }

  private int countStarted(List<PluginRuntimeSnapshot> snapshots) {
    int count = 0;
    for (PluginRuntimeSnapshot snapshot : snapshots) {
      if (snapshot != null && PluginState.STARTED.equals(snapshot.getState())) {
        count++;
      }
    }
    return count;
  }

  private int countFailed(List<PluginRuntimeSnapshot> snapshots) {
    int count = 0;
    for (PluginRuntimeSnapshot snapshot : snapshots) {
      if (snapshot != null
          && (PluginState.FAILED.equals(snapshot.getState()) || hasText(snapshot.getErrorMessage()))) {
        count++;
      }
    }
    return count;
  }

  private boolean hasText(String text) {
    return text != null && text.trim().length() > 0;
  }
}
