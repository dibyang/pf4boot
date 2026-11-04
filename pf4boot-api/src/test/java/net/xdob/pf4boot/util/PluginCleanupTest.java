package net.xdob.pf4boot.util;

import org.junit.Test;
import org.pf4j.PluginRuntimeException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** 清理动作可以嵌套，但不得在计划执行中递归消费失败重试列表。 */
public class PluginCleanupTest {
  @Test public void activeRetryPreservesPendingFailuresAndDoesNotExecuteThem() {
    PluginCleanup cleanup = new PluginCleanup();
    AtomicInteger attempts = new AtomicInteger();
    AtomicBoolean failing = new AtomicBoolean(true);
    cleanup.run("resource", () -> {
      attempts.incrementAndGet();
      if (failing.get()) { throw new IllegalStateException("resource failure"); }
    });
    cleanup.run("callback", () -> expectActiveRetryRejection(cleanup));
    assertEquals(1, attempts.get());
    try { cleanup.throwIfFailed(); fail("prior failure must remain"); }
    catch (PluginRuntimeException expected) { assertEquals(1, expected.getSuppressed().length); }
    failing.set(false); cleanup.retry(); cleanup.throwIfFailed();
    assertEquals(2, attempts.get());
  }

  @Test public void nestedRunRetainsOuterProtectionAndRestoresIdleRetry() {
    PluginCleanup cleanup = new PluginCleanup();
    AtomicInteger nested = new AtomicInteger();
    cleanup.run("outer", () -> {
      cleanup.run("inner", nested::incrementAndGet);
      expectActiveRetryRejection(cleanup);
    });
    cleanup.throwIfFailed(); cleanup.retry();
    assertEquals(1, nested.get());
  }

  private static void expectActiveRetryRejection(PluginCleanup cleanup) {
    try { cleanup.retry(); fail("active cleanup must not be retried"); }
    catch (PluginRuntimeException expected) { assertTrue(expected.getMessage().contains("running")); }
  }
}
