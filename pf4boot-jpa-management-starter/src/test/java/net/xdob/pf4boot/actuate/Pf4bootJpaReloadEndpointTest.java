package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.jpa.reload.JpaDomainDrainReport;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadFailureCode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadState;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Pf4bootJpaReloadEndpointTest {

  @Test
  public void summaryReturnsEmptyDrainFieldsWhenNoRecordExists() {
    Pf4bootJpaReloadEndpoint endpoint = new Pf4bootJpaReloadEndpoint(null, null);

    Map<String, Object> summary = endpoint.summary();

    assertEquals(false, summary.get("planAvailable"));
    assertEquals(false, summary.get("executeAvailable"));
    assertNull(summary.get("lastReloadId"));
    assertNull(summary.get("recordStoreType"));
    assertEquals(0, summary.get("recentRecordCount"));
    assertEquals(0, summary.get("recoverableRecordCount"));
    assertEquals(0L, summary.get("lastDrainDurationMillis"));
    assertEquals(0, summary.get("lastDrainPluginCount"));
    assertEquals(0, summary.get("lastDrainWarningCount"));
  }

  @Test
  public void summaryReturnsLatestDrainSummary() {
    JpaDomainDrainReport drainReport = new JpaDomainDrainReport(
        false,
        JpaDomainReloadFailureCode.DRAIN_TIMEOUT,
        "timeout",
        Arrays.asList("consumer", "provider"),
        Collections.emptyList(),
        100L,
        130L,
        Collections.singletonList("warning"));
    JpaDomainReloadRecord record = new JpaDomainReloadRecord(
        "reload-1",
        "plan-1",
        "demo",
        JpaDomainReloadState.FAILED,
        100L,
        130L,
        null,
        null,
        Collections.singletonList(JpaDomainReloadState.FAILED),
        JpaDomainReloadFailureCode.DRAIN_TIMEOUT,
        "timeout",
        null,
        drainReport);
    Pf4bootJpaReloadEndpoint endpoint = new Pf4bootJpaReloadEndpoint(null, new TestReloadService(record));

    Map<String, Object> summary = endpoint.summary();

    assertEquals("reload-1", summary.get("lastReloadId"));
    assertEquals(false, summary.get("lastDrainAccepted"));
    assertEquals(30L, summary.get("lastDrainDurationMillis"));
    assertEquals("DRAIN_TIMEOUT", summary.get("lastDrainFailureCode"));
    assertEquals(2, summary.get("lastDrainPluginCount"));
    assertEquals(1, summary.get("lastDrainWarningCount"));
  }

  @Test
  public void summaryReturnsRecordStoreCountsWhenRepositoryExists() {
    JpaDomainReloadRecord latest = record("reload-2", JpaDomainReloadState.SUCCEEDED);
    JpaDomainReloadRecord recoverable = record("reload-1", JpaDomainReloadState.MANUAL_INTERVENTION_REQUIRED);
    Pf4bootJpaReloadEndpoint endpoint =
        new Pf4bootJpaReloadEndpoint(null, null, new TestRecordRepository(latest, recoverable));

    Map<String, Object> summary = endpoint.summary();

    assertEquals("TestRecordRepository", summary.get("recordStoreType"));
    assertEquals("reload-2", summary.get("lastReloadId"));
    assertEquals(2, summary.get("recentRecordCount"));
    assertEquals(1, summary.get("recoverableRecordCount"));
  }

  private static JpaDomainReloadRecord record(String reloadId, JpaDomainReloadState state) {
    return new JpaDomainReloadRecord(
        reloadId,
        "plan-" + reloadId,
        "demo",
        state,
        100L,
        130L,
        null,
        null,
        Collections.singletonList(state),
        null,
        null,
        null);
  }

  private static class TestReloadService implements JpaDomainReloadService {
    private final JpaDomainReloadRecord record;

    private TestReloadService(JpaDomainReloadRecord record) {
      this.record = record;
    }

    @Override
    public net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlan plan(
        net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest request) {
      return null;
    }

    @Override
    public JpaDomainReloadRecord reload(net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest request) {
      return null;
    }

    @Override
    public JpaDomainReloadRecord getRecord(String reloadId) {
      return null;
    }

    @Override
    public JpaDomainReloadRecord getCurrent(String domainId) {
      return null;
    }

    @Override
    public JpaDomainReloadRecord getLatestRecord() {
      return record;
    }
  }

  private static class TestRecordRepository implements JpaDomainReloadRecordRepository {
    private final JpaDomainReloadRecord latest;
    private final JpaDomainReloadRecord recoverable;

    private TestRecordRepository(JpaDomainReloadRecord latest, JpaDomainReloadRecord recoverable) {
      this.latest = latest;
      this.recoverable = recoverable;
    }

    @Override
    public void save(JpaDomainReloadRecord record) {
    }

    @Override
    public JpaDomainReloadRecord findById(String reloadId) {
      return null;
    }

    @Override
    public JpaDomainReloadRecord findByIdempotencyKey(String idempotencyKey) {
      return null;
    }

    @Override
    public void bindIdempotencyKey(String idempotencyKey, String reloadId) {
    }

    @Override
    public JpaDomainReloadRecord findLatest() {
      return latest;
    }

    @Override
    public java.util.List<JpaDomainReloadRecord> recent(int limit) {
      return Arrays.asList(latest, recoverable);
    }

    @Override
    public java.util.List<JpaDomainReloadRecord> scanRecoverableRecords() {
      return Collections.singletonList(recoverable);
    }
  }
}
