package net.xdob.pf4boot.deployment;

import org.pf4j.PluginState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件热替换部署计划。
 *
 * <p>计划只描述替换动作、影响范围和预检结果，不代表已经执行 stop/load/start。</p>
 */
public class DeploymentPlan {

  private final String deploymentId;
  private final String targetPluginId;
  private final String stagedPluginPath;
  private final String currentPluginPath;
  private final String currentVersion;
  private final PluginState currentState;
  private final String stagedVersion;
  private final String stagedRequires;
  private final List<String> affectedPluginIds;
  private final List<String> stopOrder;
  private final List<String> startOrder;
  private final List<DeploymentCheckResult> checkResults;
  private final RollbackSnapshot rollbackSnapshot;

  public DeploymentPlan(
      String deploymentId,
      String targetPluginId,
      String stagedPluginPath,
      String currentPluginPath,
      String currentVersion,
      PluginState currentState,
      String stagedVersion,
      String stagedRequires,
      List<String> affectedPluginIds,
      List<String> stopOrder,
      List<String> startOrder,
      List<DeploymentCheckResult> checkResults,
      RollbackSnapshot rollbackSnapshot) {
    this.deploymentId = deploymentId;
    this.targetPluginId = targetPluginId;
    this.stagedPluginPath = stagedPluginPath;
    this.currentPluginPath = currentPluginPath;
    this.currentVersion = currentVersion;
    this.currentState = currentState;
    this.stagedVersion = stagedVersion;
    this.stagedRequires = stagedRequires;
    this.affectedPluginIds = unmodifiableCopy(affectedPluginIds);
    this.stopOrder = unmodifiableCopy(stopOrder);
    this.startOrder = unmodifiableCopy(startOrder);
    this.checkResults = unmodifiableChecks(checkResults);
    this.rollbackSnapshot = rollbackSnapshot;
  }

  private static List<String> unmodifiableCopy(List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }

  private static List<DeploymentCheckResult> unmodifiableChecks(List<DeploymentCheckResult> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }

  public String getDeploymentId() {
    return deploymentId;
  }

  public String getTargetPluginId() {
    return targetPluginId;
  }

  public String getStagedPluginPath() {
    return stagedPluginPath;
  }

  public String getCurrentPluginPath() {
    return currentPluginPath;
  }

  public String getCurrentVersion() {
    return currentVersion;
  }

  public PluginState getCurrentState() {
    return currentState;
  }

  public String getStagedVersion() {
    return stagedVersion;
  }

  public String getStagedRequires() {
    return stagedRequires;
  }

  public List<String> getAffectedPluginIds() {
    return affectedPluginIds;
  }

  public List<String> getStopOrder() {
    return stopOrder;
  }

  public List<String> getStartOrder() {
    return startOrder;
  }

  public List<DeploymentCheckResult> getCheckResults() {
    return checkResults;
  }

  public RollbackSnapshot getRollbackSnapshot() {
    return rollbackSnapshot;
  }

  public boolean isExecutable() {
    for (DeploymentCheckResult result : checkResults) {
      if (result.isError()) {
        return false;
      }
    }
    return true;
  }
}
