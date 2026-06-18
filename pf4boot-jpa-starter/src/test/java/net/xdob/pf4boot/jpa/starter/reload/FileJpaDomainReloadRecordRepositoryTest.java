package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.deployment.DeploymentCheckResult;
import net.xdob.pf4boot.deployment.PluginCleanupSummary;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadFailureCode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadState;
import net.xdob.pf4boot.jpa.reload.JpaProviderReplacementSummary;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FileJpaDomainReloadRecordRepositoryTest {

  @Test
  public void reloadsRecordsAndIndexesFromFiles() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-jpa-reload-store");
    FileJpaDomainReloadRecordRepository store =
        new FileJpaDomainReloadRecordRepository(directory, 100, true);
    JpaDomainReloadRecord first = record(
        "reload-1",
        "demo",
        JpaDomainReloadState.SUCCEEDED,
        1000L,
        2000L,
        "key-1");
    JpaDomainReloadRecord second = record(
        "reload-2",
        "demo",
        JpaDomainReloadState.SUCCEEDED,
        3000L,
        4000L,
        "key-2");

    store.save(first);
    store.save(second);

    FileJpaDomainReloadRecordRepository reloaded =
        new FileJpaDomainReloadRecordRepository(directory, 100, true);

    assertEquals("reload-1", reloaded.findById("reload-1").getReloadId());
    assertEquals("reload-1", reloaded.findByIdempotencyKey("key-1").getReloadId());
    assertEquals("reload-2", reloaded.findLatest().getReloadId());
    assertEquals(Arrays.asList("reload-2", "reload-1"), ids(reloaded.recent(10)));
  }

  @Test
  public void scansRecoverableRecordsOldestFirst() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-jpa-reload-store");
    FileJpaDomainReloadRecordRepository store =
        new FileJpaDomainReloadRecordRepository(directory, 100, true);

    store.save(record("reload-2", "demo", JpaDomainReloadState.MANUAL_INTERVENTION_REQUIRED, 3000L, 4000L, "key-2"));
    store.save(record("reload-1", "demo", JpaDomainReloadState.MANUAL_INTERVENTION_REQUIRED, 1000L, 2000L, "key-1"));
    store.save(record("reload-3", "demo", JpaDomainReloadState.SUCCEEDED, 5000L, 6000L, "key-3"));

    assertEquals(Arrays.asList("reload-1", "reload-2"), ids(store.scanRecoverableRecords()));
  }

  @Test
  public void reloadsProviderReplacementSummaryFromFiles() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-jpa-reload-store");
    FileJpaDomainReloadRecordRepository store =
        new FileJpaDomainReloadRecordRepository(directory, 100, true);
    JpaDomainReloadRecord record = new JpaDomainReloadRecord(
        "reload-replace",
        "plan-replace",
        "demo",
        JpaDomainReloadState.SUCCEEDED,
        1000L,
        2000L,
        request("demo", "key-replace"),
        null,
        Arrays.asList(JpaDomainReloadState.SUCCEEDED),
        null,
        null,
        null,
        null,
        new JpaProviderReplacementSummary(
            "deployment-1",
            "provider-demo",
            "plugins/provider.zip",
            "1.0.0",
            "2.0.0",
            "SUCCEEDED",
            "PRECHECK_FAILED",
            null,
            "replacement succeeded"),
        PluginCleanupSummary.passed(
            Arrays.asList("provider-demo"),
            Arrays.asList(DeploymentCheckResult.info("JPA_EXPORTS_REMOVED", "removed"))));

    store.save(record);

    FileJpaDomainReloadRecordRepository reloaded =
        new FileJpaDomainReloadRecordRepository(directory, 100, true);

    JpaDomainReloadRecord restored = reloaded.findById("reload-replace");
    assertEquals("deployment-1", restored.getProviderReplacementSummary().getDeploymentId());
    assertEquals("2.0.0", restored.getProviderReplacementSummary().getStagedVersion());
    assertEquals("PRECHECK_FAILED", restored.getProviderReplacementSummary().getErrorCode());
    assertEquals("reload-replace", reloaded.findByIdempotencyKey("key-replace").getReloadId());
    assertEquals("reload-replace", reloaded.findLatest().getReloadId());
    assertEquals(1, restored.getCleanupSummary().getInfoCount());
  }

  @Test
  public void skipsCorruptedLineWhenFailOpen() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-jpa-reload-store");
    FileJpaDomainReloadRecordRepository store =
        new FileJpaDomainReloadRecordRepository(directory, 100, true);
    store.save(record("reload-1", "demo", JpaDomainReloadState.SUCCEEDED, 1000L, 2000L, "key-1"));
    Files.write(
        directory.resolve("jpa-reloads-2026-06-12.jsonl"),
        "not json\n".getBytes(StandardCharsets.UTF_8));

    FileJpaDomainReloadRecordRepository reloaded =
        new FileJpaDomainReloadRecordRepository(directory, 100, false);

    assertEquals("reload-1", reloaded.findById("reload-1").getReloadId());
  }

  @Test(expected = IllegalStateException.class)
  public void failsOnCorruptedLineWhenFailClosed() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-jpa-reload-store");
    Files.write(
        directory.resolve("jpa-reloads-2026-06-12.jsonl"),
        "not json\n".getBytes(StandardCharsets.UTF_8));

    new FileJpaDomainReloadRecordRepository(directory, 100, true);
  }

  @Test
  public void returnsNullWhenLimitIsZero() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-jpa-reload-store");
    FileJpaDomainReloadRecordRepository store =
        new FileJpaDomainReloadRecordRepository(directory, 100, true);
    store.save(record("reload-1", "demo", JpaDomainReloadState.SUCCEEDED, 1000L, 2000L, null));

    assertEquals(0, store.recent(0).size());
    assertNull(store.findByIdempotencyKey(null));
  }

  private static JpaDomainReloadRecord record(
      String reloadId,
      String domainId,
      JpaDomainReloadState state,
      long startedAt,
      long finishedAt,
      String idempotencyKey) {
    return new JpaDomainReloadRecord(
        reloadId,
        "plan-" + reloadId,
        domainId,
        state,
        startedAt,
        finishedAt,
        request(domainId, idempotencyKey),
        null,
        Arrays.asList(state),
        state == JpaDomainReloadState.MANUAL_INTERVENTION_REQUIRED
            ? JpaDomainReloadFailureCode.OPERATION_FAILED
            : null,
        null,
        null);
  }

  private static JpaDomainReloadRequest request(String domainId, String idempotencyKey) {
    JpaDomainReloadRequest request = new JpaDomainReloadRequest();
    request.setDomainId(domainId);
    request.setIdempotencyKey(idempotencyKey);
    return request;
  }

  private static List<String> ids(List<JpaDomainReloadRecord> records) {
    java.util.ArrayList<String> ids = new java.util.ArrayList<>();
    for (JpaDomainReloadRecord record : records) {
      ids.add(record.getReloadId());
    }
    return ids;
  }
}
