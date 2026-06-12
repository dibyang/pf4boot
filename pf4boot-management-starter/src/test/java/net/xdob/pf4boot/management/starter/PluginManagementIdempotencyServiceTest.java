package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementRequest;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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

  @Test
  public void beginReservesIdempotencyKeyAtomically() throws Exception {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setRequireIdempotencyKey(true);
    PluginOperationStore store = new InMemoryPluginOperationStore();
    final PluginManagementIdempotencyService service = new PluginManagementIdempotencyService(properties, store);
    final CountDownLatch ready = new CountDownLatch(2);
    final CountDownLatch start = new CountDownLatch(1);
    final AtomicInteger executors = new AtomicInteger();

    Runnable task = new Runnable() {
      @Override
      public void run() {
        ready.countDown();
        try {
          start.await();
          PluginManagementRequest request = request("same-key", "p1");
          if (service.begin(request, PluginManagementOperation.PLUGIN_START, "admin", "h1",
              "op-" + Thread.currentThread().getId(), null) == null) {
            executors.incrementAndGet();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };

    Thread first = new Thread(task, "idem-first");
    Thread second = new Thread(task, "idem-second");
    first.start();
    second.start();
    ready.await();
    start.countDown();
    first.join();
    second.join();

    assertEquals(1, executors.get());
  }

  private PluginManagementRequest request(String key, String pluginId) {
    PluginManagementRequest request = new PluginManagementRequest();
    request.setIdempotencyKey(key);
    request.setPluginId(pluginId);
    return request;
  }

}
