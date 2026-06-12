package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginOperationRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FilePluginOperationStoreTest {

  @Test
  public void appendAndReadLatestRecordAfterRestart() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-operation-store");
    FilePluginOperationStore store = new FilePluginOperationStore(directory);
    PluginOperationRecord record = record("op-1", "idem-1", "dep-1", "STARTED");
    store.save(record);
    record.setState("SUCCEEDED");
    record.setResponseCode("OK");
    store.save(record);

    FilePluginOperationStore reloaded = new FilePluginOperationStore(directory);

    assertEquals("SUCCEEDED", reloaded.findById("op-1").getState());
    assertEquals("op-1", reloaded.findByIdempotencyKey("idem-1").getOperationId());
    assertEquals("op-1", reloaded.findByDeploymentId("dep-1").getOperationId());
  }

  @Test
  public void skipCorruptedLineWhenReloading() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-operation-store");
    Files.write(directory.resolve("operations-2026-06-12.jsonl"),
        ("not json\n" + jsonLine("op-1", "SUCCEEDED")).getBytes("UTF-8"));

    FilePluginOperationStore store = new FilePluginOperationStore(directory);

    assertEquals("SUCCEEDED", store.findById("op-1").getState());
  }

  @Test
  public void saveIfIdempotencyKeyAbsentReturnsExistingRecord() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-operation-store");
    FilePluginOperationStore store = new FilePluginOperationStore(directory);

    assertNull(store.saveIfIdempotencyKeyAbsent(record("op-1", "idem-1", null, "STARTED")));
    PluginOperationRecord existing = store.saveIfIdempotencyKeyAbsent(
        record("op-2", "idem-1", null, "STARTED"));

    assertEquals("op-1", existing.getOperationId());
    assertNull(store.findById("op-2"));
  }

  @Test
  public void scanRecoverableRecordsReturnsOnlyRunningStates() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-operation-store");
    FilePluginOperationStore store = new FilePluginOperationStore(directory);
    store.save(record("op-1", "idem-1", null, "STARTED"));
    store.save(record("op-2", "idem-2", null, "EXECUTING"));
    store.save(record("op-3", "idem-3", null, "SUCCEEDED"));

    List<PluginOperationRecord> records = store.scanRecoverableRecords();

    assertEquals(2, records.size());
    assertEquals("op-1", records.get(0).getOperationId());
    assertEquals("op-2", records.get(1).getOperationId());
  }

  private PluginOperationRecord record(String operationId, String idempotencyKey, String deploymentId, String state) {
    PluginOperationRecord record = new PluginOperationRecord();
    record.setOperationId(operationId);
    record.setRequestId("req-" + operationId);
    record.setOperation(PluginManagementOperation.PLUGIN_START);
    record.setPrincipalId("admin");
    record.setPluginId("sample");
    record.setDeploymentId(deploymentId);
    record.setIdempotencyKey(idempotencyKey);
    record.setRequestHash("hash-" + operationId);
    record.setState(state);
    return record;
  }

  private String jsonLine(String operationId, String state) {
    return "{"
        + "\"schemaVersion\":1,"
        + "\"operationId\":\"" + operationId + "\","
        + "\"requestId\":\"req-" + operationId + "\","
        + "\"operation\":\"PLUGIN_START\","
        + "\"principalId\":\"admin\","
        + "\"pluginId\":\"sample\","
        + "\"idempotencyKey\":\"idem-" + operationId + "\","
        + "\"requestHash\":\"hash-" + operationId + "\","
        + "\"success\":true,"
        + "\"state\":\"" + state + "\","
        + "\"createdAt\":1,"
        + "\"updatedAt\":2"
        + "}";
  }
}
