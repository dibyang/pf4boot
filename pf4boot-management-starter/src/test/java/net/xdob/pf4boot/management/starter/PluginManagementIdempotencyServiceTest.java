package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementRequest;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class PluginManagementIdempotencyServiceTest {

  @Test
  public void beginReplaysWhenKeyAndHashMatch() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setRequireIdempotencyKey(true);
    PluginOperationStore store = new InMemoryPluginOperationStore();
    PluginManagementIdempotencyService service = new PluginManagementIdempotencyService(properties, store);

    PluginManagementRequest request = request("k1", "p1");
    service.begin(request, PluginManagementOperation.PLUGIN_START, "admin", "h1", "op-1", null);
    assertEquals("op-1", store.findByIdempotencyKey("admin:PLUGIN_START:p1:k1").getOperationId());

    assertEquals(
        "h1",
        service.begin(request, PluginManagementOperation.PLUGIN_START, "admin", "h1", "op-2", null).getRequestHash());
  }

  @Test
  public void beginThrowsConflictWhenHashDifferent() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setRequireIdempotencyKey(true);
    PluginOperationStore store = new InMemoryPluginOperationStore();
    PluginManagementIdempotencyService service = new PluginManagementIdempotencyService(properties, store);

    PluginManagementRequest request = request("k1", "p1");
    service.begin(request, PluginManagementOperation.PLUGIN_START, "admin", "h1", "op-1", null);
    try {
      request = request("k1", "p1");
      service.begin(request, PluginManagementOperation.PLUGIN_START, "admin", "h2", "op-2", null);
      fail("Expected idempotency conflict");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.CONFLICT, e.getCode());
      assertEquals(409, e.getStatusCode());
    }
  }

  @Test
  public void beginSkipWhenNotRequireIdempotency() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setRequireIdempotencyKey(false);
    PluginOperationStore store = new InMemoryPluginOperationStore();
    PluginManagementIdempotencyService service = new PluginManagementIdempotencyService(properties, store);

    PluginManagementRequest request = request("k1", "p1");
    assertNull(service.begin(request, PluginManagementOperation.PLUGIN_START, "admin", "h1", "op-1", null));
    assertNull(service.begin(request, PluginManagementOperation.PLUGIN_START, "admin", "h2", "op-2", null));
  }

  private PluginManagementRequest request(String key, String pluginId) {
    PluginManagementRequest request = new PluginManagementRequest();
    request.setIdempotencyKey(key);
    request.setPluginId(pluginId);
    return request;
  }

}
