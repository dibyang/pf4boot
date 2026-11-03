package net.xdob.pf4boot.deployment;

import org.junit.Test;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class PluginWorkTrackerTest {
  @Test
  public void nestedDrainDoesNotReleaseOuterAdmissionBarrier() throws Exception {
    PluginWorkTracker tracker = new PluginWorkTracker();
    java.util.List<String> ids = Collections.singletonList("plugin");
    assertTrue(tracker.tryEnter("plugin"));
    tracker.beginDrain(ids);
    tracker.beginDrain(ids);
    tracker.endDrain(ids);
    assertFalse(tracker.tryEnter("plugin"));
    assertFalse(tracker.awaitDrain(ids, 0));
    tracker.exit("plugin");
    assertTrue(tracker.awaitDrain(ids, 0));
    assertEquals(0, tracker.getRunningCount("plugin"));
    tracker.endDrain(ids);
    assertTrue(tracker.tryEnter("plugin"));
    tracker.exit("plugin");
  }

  @Test
  public void concurrentAdmissionCannotEscapeDrainAccounting() throws Exception {
    PluginWorkTracker tracker = new PluginWorkTracker();
    java.util.List<String> ids = Collections.singletonList("plugin");
    for (int i = 0; i < 200; i++) {
      CountDownLatch start = new CountDownLatch(1);
      AtomicBoolean entered = new AtomicBoolean();
      Thread worker = new Thread(() -> {
        try {
          start.await();
          entered.set(tracker.tryEnter("plugin"));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
      worker.start();
      start.countDown();
      tracker.beginDrain(ids);
      worker.join(2000);
      assertFalse(worker.isAlive());
      assertFalse(tracker.tryEnter("plugin"));
      assertEquals(!entered.get(), tracker.awaitDrain(ids, 0));
      if (entered.get()) { tracker.exit("plugin"); }
      tracker.endDrain(ids);
    }
  }

  @Test
  public void waiterWakesWhenLastWorkExits() throws Exception {
    PluginWorkTracker tracker = new PluginWorkTracker();
    tracker.tryEnter("plugin");
    tracker.beginDrain(Collections.singletonList("plugin"));
    CountDownLatch waiting = new CountDownLatch(1);
    AtomicBoolean drained = new AtomicBoolean();
    Thread waiter = new Thread(() -> {
      waiting.countDown();
      try {
        drained.set(tracker.awaitDrain(Collections.singletonList("plugin"), 2000));
      } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    });
    waiter.start();
    assertTrue(waiting.await(1, TimeUnit.SECONDS));
    tracker.exit("plugin");
    waiter.join(2500);
    assertFalse(waiter.isAlive());
    assertTrue(drained.get());
  }
}
