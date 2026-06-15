package net.xdob.pf4boot.actuate;

import net.xdob.pf4boot.jpa.reload.JpaDomainDrainReport;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadFailureCode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
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
}
