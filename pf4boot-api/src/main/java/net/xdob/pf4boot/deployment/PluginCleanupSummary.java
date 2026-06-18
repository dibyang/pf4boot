package net.xdob.pf4boot.deployment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件资源清理摘要。
 *
 * <p>该模型用于把 core、web、JPA 等模块的清理检查结果汇总到部署记录和刷新记录中。
 * 它只暴露稳定摘要和检查结果，不暴露内部可变集合。</p>
 */
public class PluginCleanupSummary {

  private final boolean passed;
  private final List<String> pluginIds;
  private final List<DeploymentCheckResult> checkResults;
  private final int infoCount;
  private final int warnCount;
  private final int errorCount;

  public PluginCleanupSummary(
      boolean passed,
      List<String> pluginIds,
      List<DeploymentCheckResult> checkResults) {
    this.pluginIds = copyStrings(pluginIds);
    this.checkResults = copyChecks(checkResults);
    int infos = 0;
    int warns = 0;
    int errors = 0;
    for (DeploymentCheckResult result : this.checkResults) {
      if (result == null || result.getSeverity() == null) {
        infos++;
      } else if (DeploymentCheckSeverity.ERROR.equals(result.getSeverity())) {
        errors++;
      } else if (DeploymentCheckSeverity.WARN.equals(result.getSeverity())) {
        warns++;
      } else {
        infos++;
      }
    }
    this.infoCount = infos;
    this.warnCount = warns;
    this.errorCount = errors;
    this.passed = passed && errors == 0;
  }

  public static PluginCleanupSummary notChecked(List<String> pluginIds, String reason) {
    return new PluginCleanupSummary(
        true,
        pluginIds,
        Collections.singletonList(DeploymentCheckResult.info("CLEANUP_NOT_CHECKED", reason)));
  }

  public static PluginCleanupSummary passed(List<String> pluginIds, List<DeploymentCheckResult> checkResults) {
    return new PluginCleanupSummary(true, pluginIds, checkResults);
  }

  public static PluginCleanupSummary failed(List<String> pluginIds, List<DeploymentCheckResult> checkResults) {
    return new PluginCleanupSummary(false, pluginIds, checkResults);
  }

  public boolean isPassed() {
    return passed;
  }

  public List<String> getPluginIds() {
    return pluginIds;
  }

  public List<DeploymentCheckResult> getCheckResults() {
    return checkResults;
  }

  public int getInfoCount() {
    return infoCount;
  }

  public int getWarnCount() {
    return warnCount;
  }

  public int getErrorCount() {
    return errorCount;
  }

  private static List<String> copyStrings(List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }

  private static List<DeploymentCheckResult> copyChecks(List<DeploymentCheckResult> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }
}
