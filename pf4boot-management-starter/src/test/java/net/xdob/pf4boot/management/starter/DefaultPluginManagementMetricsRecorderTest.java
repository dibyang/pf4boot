package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementMetricsSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultPluginManagementMetricsRecorderTest {

  @Test
  public void snapshotReturnsRecordedManagementCounters() {
    DefaultPluginManagementMetricsRecorder recorder = new DefaultPluginManagementMetricsRecorder();

    recorder.recordRequest();
    recorder.recordRequest();
    recorder.recordRejected();
    recorder.recordIdempotencyHit();

    PluginManagementMetricsSnapshot snapshot = recorder.snapshot();
    assertEquals(2, snapshot.getRequestTotal());
    assertEquals(1, snapshot.getRejectedTotal());
    assertEquals(1, snapshot.getIdempotencyHitTotal());
  }
}
