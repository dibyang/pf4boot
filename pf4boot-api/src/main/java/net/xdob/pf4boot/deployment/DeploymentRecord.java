package net.xdob.pf4boot.deployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 插件部署记录。
 *
 * <p>记录用于管理 API、CLI 和后续持久化层追踪一次部署编排的状态。</p>
 */
public class DeploymentRecord {

  private final String deploymentId;
  private final String targetPluginId;
  private final DeploymentState state;
  private final long createdAt;
  private final long updatedAt;
  private final String message;
  private final DeploymentPlan plan;
  private final List<DeploymentState> stateHistory;
  private final long durationMillis;
  private final String errorCode;

  public DeploymentRecord(
      String deploymentId,
      String targetPluginId,
      DeploymentState state,
      long createdAt,
      long updatedAt,
      String message,
      DeploymentPlan plan) {
    this(deploymentId, targetPluginId, state, createdAt, updatedAt, message, plan,
        Collections.singletonList(state), -1, null);
  }

  public DeploymentRecord(
      String deploymentId,
      String targetPluginId,
      DeploymentState state,
      long createdAt,
      long updatedAt,
      String message,
      DeploymentPlan plan,
      List<DeploymentState> stateHistory) {
    this(deploymentId, targetPluginId, state, createdAt, updatedAt, message, plan,
        stateHistory, updatedAt >= createdAt ? updatedAt - createdAt : -1, null);
  }

  public DeploymentRecord(
      String deploymentId,
      String targetPluginId,
      DeploymentState state,
      long createdAt,
      long updatedAt,
      String message,
      DeploymentPlan plan,
      List<DeploymentState> stateHistory,
      long durationMillis,
      String errorCode) {
    this.deploymentId = deploymentId;
    this.targetPluginId = targetPluginId;
    this.state = state;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.message = message;
    this.plan = plan;
    this.stateHistory = unmodifiableHistory(stateHistory, state);
    this.durationMillis = durationMillis;
    this.errorCode = errorCode;
  }

  public static List<DeploymentState> history(DeploymentState... states) {
    if (states == null || states.length == 0) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(Arrays.asList(states));
  }

  private static List<DeploymentState> unmodifiableHistory(
      List<DeploymentState> source,
      DeploymentState fallbackState) {
    if (source == null || source.isEmpty()) {
      return Collections.singletonList(fallbackState);
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }

  public String getDeploymentId() {
    return deploymentId;
  }

  public String getTargetPluginId() {
    return targetPluginId;
  }

  public DeploymentState getState() {
    return state;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public long getUpdatedAt() {
    return updatedAt;
  }

  public String getMessage() {
    return message;
  }

  public DeploymentPlan getPlan() {
    return plan;
  }

  public List<DeploymentState> getStateHistory() {
    return stateHistory;
  }

  public long getDurationMillis() {
    return durationMillis;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
