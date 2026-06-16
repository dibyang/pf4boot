package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.jpa.reload.JpaDomainDrainReport;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlanService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA domain 刷新只读观测端点。
 */
@Endpoint(id = "pf4bootjpareload")
public class Pf4bootJpaReloadEndpoint {

  private final JpaDomainReloadPlanService planService;
  private final JpaDomainReloadService reloadService;
  private final JpaDomainReloadRecordRepository recordRepository;

  public Pf4bootJpaReloadEndpoint(
      JpaDomainReloadPlanService planService,
      JpaDomainReloadService reloadService) {
    this(planService, reloadService, null);
  }

  public Pf4bootJpaReloadEndpoint(
      JpaDomainReloadPlanService planService,
      JpaDomainReloadService reloadService,
      JpaDomainReloadRecordRepository recordRepository) {
    this.planService = planService;
    this.reloadService = reloadService;
    this.recordRepository = recordRepository;
  }

  /**
   * 返回 JPA domain 刷新能力摘要。
   */
  @ReadOperation
  public Map<String, Object> summary() {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("planAvailable", planService != null);
    summary.put("executeAvailable", reloadService != null);
    summary.put("recordStoreType", recordRepository == null ? null : recordRepository.getClass().getSimpleName());
    summary.put("mode", reloadService == null ? "PLAN_ONLY_OR_DISABLED" : "EXECUTE_SERVICE_PRESENT");
    JpaDomainReloadRecord latest = latestRecord();
    JpaDomainDrainReport drainReport = latest == null ? null : latest.getDrainReport();
    summary.put("lastReloadId", latest == null ? null : latest.getReloadId());
    summary.put("latestReloadId", latest == null ? null : latest.getReloadId());
    summary.put("recentRecordCount", recentRecords().size());
    summary.put("recoverableRecordCount", recoverableRecords().size());
    summary.put("lastDrainAccepted", drainReport == null ? null : drainReport.isAccepted());
    summary.put("lastDrainDurationMillis", drainReport == null ? 0L : drainReport.getDurationMillis());
    summary.put("lastDrainFailureCode", drainReport == null || drainReport.getFailureCode() == null
        ? null
        : drainReport.getFailureCode().name());
    summary.put("lastDrainPluginCount", drainReport == null ? 0 : drainReport.getPluginIds().size());
    summary.put("lastDrainWarningCount", drainReport == null ? 0 : drainReport.getWarnings().size());
    return summary;
  }

  private JpaDomainReloadRecord latestRecord() {
    JpaDomainReloadRecord latest = recordRepository == null ? null : recordRepository.findLatest();
    if (latest != null) {
      return latest;
    }
    return reloadService == null ? null : reloadService.getLatestRecord();
  }

  private List<JpaDomainReloadRecord> recentRecords() {
    return recordRepository == null ? Collections.emptyList() : recordRepository.recent(100);
  }

  private List<JpaDomainReloadRecord> recoverableRecords() {
    return recordRepository == null ? Collections.emptyList() : recordRepository.scanRecoverableRecords();
  }
}
