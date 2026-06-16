package net.xdob.pf4boot.jpa.reload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JPA domain 刷新执行记录。
 */
public class JpaDomainReloadRecord {

  private final String reloadId;
  private final String planId;
  private final String domainId;
  private final JpaDomainReloadState state;
  private final long startedAt;
  private final long finishedAt;
  private final JpaDomainReloadRequest request;
  private final JpaDomainReloadPlan plan;
  private final List<JpaDomainReloadState> stateTransitions;
  private final JpaDomainReloadFailureCode failureCode;
  private final String failureMessage;
  private final String rollbackSummary;
  private final JpaDomainDrainReport drainReport;
  private final JpaProviderReplacementSummary providerReplacementSummary;

  public JpaDomainReloadRecord(
      String reloadId,
      String planId,
      String domainId,
      JpaDomainReloadState state,
      long startedAt,
      long finishedAt,
      JpaDomainReloadRequest request,
      JpaDomainReloadPlan plan,
      List<JpaDomainReloadState> stateTransitions,
      JpaDomainReloadFailureCode failureCode,
      String failureMessage,
      String rollbackSummary) {
    this(
        reloadId,
        planId,
        domainId,
        state,
        startedAt,
        finishedAt,
        request,
        plan,
        stateTransitions,
        failureCode,
        failureMessage,
        rollbackSummary,
        null,
        null);
  }

  public JpaDomainReloadRecord(
      String reloadId,
      String planId,
      String domainId,
      JpaDomainReloadState state,
      long startedAt,
      long finishedAt,
      JpaDomainReloadRequest request,
      JpaDomainReloadPlan plan,
      List<JpaDomainReloadState> stateTransitions,
      JpaDomainReloadFailureCode failureCode,
      String failureMessage,
      String rollbackSummary,
      JpaDomainDrainReport drainReport) {
    this(
        reloadId,
        planId,
        domainId,
        state,
        startedAt,
        finishedAt,
        request,
        plan,
        stateTransitions,
        failureCode,
        failureMessage,
        rollbackSummary,
        drainReport,
        null);
  }

  public JpaDomainReloadRecord(
      String reloadId,
      String planId,
      String domainId,
      JpaDomainReloadState state,
      long startedAt,
      long finishedAt,
      JpaDomainReloadRequest request,
      JpaDomainReloadPlan plan,
      List<JpaDomainReloadState> stateTransitions,
      JpaDomainReloadFailureCode failureCode,
      String failureMessage,
      String rollbackSummary,
      JpaDomainDrainReport drainReport,
      JpaProviderReplacementSummary providerReplacementSummary) {
    this.reloadId = reloadId;
    this.planId = planId;
    this.domainId = domainId;
    this.state = state;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.request = request;
    this.plan = plan;
    this.stateTransitions = stateTransitions == null
        ? Collections.<JpaDomainReloadState>emptyList()
        : Collections.unmodifiableList(new ArrayList<>(stateTransitions));
    this.failureCode = failureCode;
    this.failureMessage = failureMessage;
    this.rollbackSummary = rollbackSummary;
    this.drainReport = drainReport;
    this.providerReplacementSummary = providerReplacementSummary;
  }

  public String getReloadId() {
    return reloadId;
  }

  public String getPlanId() {
    return planId;
  }

  public String getDomainId() {
    return domainId;
  }

  public JpaDomainReloadState getState() {
    return state;
  }

  public long getStartedAt() {
    return startedAt;
  }

  public long getFinishedAt() {
    return finishedAt;
  }

  public JpaDomainReloadRequest getRequest() {
    return request;
  }

  public JpaDomainReloadPlan getPlan() {
    return plan;
  }

  public List<JpaDomainReloadState> getStateTransitions() {
    return stateTransitions;
  }

  public JpaDomainReloadFailureCode getFailureCode() {
    return failureCode;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public String getRollbackSummary() {
    return rollbackSummary;
  }

  public JpaDomainDrainReport getDrainReport() {
    return drainReport;
  }

  public JpaProviderReplacementSummary getProviderReplacementSummary() {
    return providerReplacementSummary;
  }
}
