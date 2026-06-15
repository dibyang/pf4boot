package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import net.xdob.pf4boot.jpa.reload.JpaDomainDrainReport;
import net.xdob.pf4boot.jpa.reload.JpaDomainDrainerPhase;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadFailureCode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlan;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JpaDomainReloadDrainCoordinatorTest {

  @Test
  public void noDrainerContinuesForCompatibility() {
    JpaDomainReloadDrainCoordinator coordinator =
        new JpaDomainReloadDrainCoordinator(Collections.emptyList(), new Pf4bootJpaProperties());

    JpaDomainDrainReport report = coordinator.drain(plan(), 100L);

    assertTrue(report.isAccepted());
    assertEquals(Arrays.asList("consumer-b", "consumer-a", "provider-demo"), report.getPluginIds());
    assertEquals(1, report.getWarnings().size());
  }

  @Test
  public void noDrainerRejectsWhenStrictModeEnabled() {
    Pf4bootJpaProperties properties = new Pf4bootJpaProperties();
    properties.getDomainReload().setRequireDrainer(true);
    JpaDomainReloadDrainCoordinator coordinator =
        new JpaDomainReloadDrainCoordinator(Collections.emptyList(), properties);

    JpaDomainDrainReport report = coordinator.drain(plan(), 100L);

    assertFalse(report.isAccepted());
    assertEquals(JpaDomainReloadFailureCode.DRAIN_REJECTED, report.getFailureCode());
  }

  @Test
  public void beginFailureEndsAlreadyBegunDrainers() {
    TestDrainer first = new TestDrainer("first");
    TestDrainer second = new TestDrainer("second");
    second.beginFailure = true;
    JpaDomainReloadDrainCoordinator coordinator =
        new JpaDomainReloadDrainCoordinator(Arrays.<PluginTrafficDrainer>asList(first, second), new Pf4bootJpaProperties());

    JpaDomainDrainReport report = coordinator.drain(plan(), 100L);

    assertFalse(report.isAccepted());
    assertEquals(JpaDomainReloadFailureCode.DRAIN_REJECTED, report.getFailureCode());
    assertEquals(Arrays.asList("begin:first", "end:first"), first.events);
    assertEquals(Collections.singletonList("begin:second"), second.events);
  }

  @Test
  public void awaitFalseReturnsTimeoutAndEndsDrainers() {
    TestDrainer drainer = new TestDrainer("timeout");
    drainer.awaitResult = false;
    JpaDomainReloadDrainCoordinator coordinator =
        new JpaDomainReloadDrainCoordinator(Collections.<PluginTrafficDrainer>singletonList(drainer), new Pf4bootJpaProperties());

    JpaDomainDrainReport report = coordinator.drain(plan(), 100L);

    assertFalse(report.isAccepted());
    assertEquals(JpaDomainReloadFailureCode.DRAIN_TIMEOUT, report.getFailureCode());
    assertEquals("begin:timeout", drainer.events.get(0));
    assertTrue(drainer.events.get(1).startsWith("await:timeout:"));
    assertEquals("end:timeout", drainer.events.get(2));
  }

  @Test
  public void awaitExceptionReturnsRejectedAndEndsDrainers() {
    TestDrainer drainer = new TestDrainer("reject");
    drainer.awaitFailure = true;
    JpaDomainReloadDrainCoordinator coordinator =
        new JpaDomainReloadDrainCoordinator(Collections.<PluginTrafficDrainer>singletonList(drainer), new Pf4bootJpaProperties());

    JpaDomainDrainReport report = coordinator.drain(plan(), 100L);

    assertFalse(report.isAccepted());
    assertEquals(JpaDomainReloadFailureCode.DRAIN_REJECTED, report.getFailureCode());
    assertEquals("begin:reject", drainer.events.get(0));
    assertTrue(drainer.events.get(1).startsWith("await:reject:"));
    assertEquals("end:reject", drainer.events.get(2));
  }

  @Test
  public void awaitInterruptedReturnsRejectedAndRestoresInterruptStatus() {
    TestDrainer drainer = new TestDrainer("interrupted");
    drainer.awaitInterrupted = true;
    JpaDomainReloadDrainCoordinator coordinator =
        new JpaDomainReloadDrainCoordinator(Collections.<PluginTrafficDrainer>singletonList(drainer), new Pf4bootJpaProperties());

    JpaDomainDrainReport report = coordinator.drain(plan(), 100L);

    assertFalse(report.isAccepted());
    assertEquals(JpaDomainReloadFailureCode.DRAIN_REJECTED, report.getFailureCode());
    assertTrue(Thread.currentThread().isInterrupted());
    assertEquals("end:interrupted", drainer.events.get(2));
    Thread.interrupted();
  }

  @Test
  public void successfulDrainEndsLaterByPlanId() {
    TestDrainer first = new TestDrainer("first");
    TestDrainer second = new TestDrainer("second");
    JpaDomainReloadDrainCoordinator coordinator =
        new JpaDomainReloadDrainCoordinator(Arrays.<PluginTrafficDrainer>asList(first, second), new Pf4bootJpaProperties());
    JpaDomainReloadPlan plan = plan();

    JpaDomainDrainReport report = coordinator.drain(plan, 100L);
    JpaDomainDrainReport endReport = coordinator.endDrain(plan);

    assertTrue(report.isAccepted());
    assertTrue(endReport.isAccepted());
    assertEquals(
        "begin:first",
        first.events.get(0));
    assertTrue(first.events.get(1).startsWith("await:first:"));
    assertEquals("end:first", first.events.get(2));
    assertEquals(
        "begin:second",
        second.events.get(0));
    assertTrue(second.events.get(1).startsWith("await:second:"));
    assertEquals("end:second", second.events.get(2));
    assertEquals(JpaDomainDrainerPhase.END, endReport.getDrainerResults().get(0).getPhase());
  }

  @Test
  public void endFailureIsWarningOnly() {
    TestDrainer drainer = new TestDrainer("end-failure");
    drainer.endFailure = true;
    JpaDomainReloadDrainCoordinator coordinator =
        new JpaDomainReloadDrainCoordinator(Collections.<PluginTrafficDrainer>singletonList(drainer), new Pf4bootJpaProperties());
    JpaDomainReloadPlan plan = plan();

    JpaDomainDrainReport report = coordinator.drain(plan, 100L);
    JpaDomainDrainReport endReport = coordinator.endDrain(plan);

    assertTrue(report.isAccepted());
    assertFalse(endReport.isAccepted());
    assertEquals(1, endReport.getWarnings().size());
  }

  private static JpaDomainReloadPlan plan() {
    return new JpaDomainReloadPlan(
        "plan-1",
        "demo",
        "provider-demo",
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Arrays.asList("consumer-b", "consumer-a", "consumer-b"),
        Arrays.asList("consumer-a", "consumer-b"),
        Collections.emptyList(),
        Collections.emptyList(),
        1L,
        true);
  }

  private static class TestDrainer implements PluginTrafficDrainer {
    private final String name;
    private final List<String> events = new ArrayList<>();
    private boolean beginFailure;
    private boolean awaitFailure;
    private boolean awaitInterrupted;
    private boolean endFailure;
    private boolean awaitResult = true;

    private TestDrainer(String name) {
      this.name = name;
    }

    @Override
    public void beginDrain(java.util.Collection<String> pluginIds) {
      events.add("begin:" + name);
      if (beginFailure) {
        throw new IllegalStateException("begin failed");
      }
    }

    @Override
    public boolean awaitDrain(java.util.Collection<String> pluginIds, long timeoutMillis) throws InterruptedException {
      events.add("await:" + name + ":" + timeoutMillis);
      if (awaitInterrupted) {
        throw new InterruptedException("await interrupted");
      }
      if (awaitFailure) {
        throw new IllegalStateException("await failed");
      }
      return awaitResult;
    }

    @Override
    public void endDrain(java.util.Collection<String> pluginIds) {
      events.add("end:" + name);
      if (endFailure) {
        throw new IllegalStateException("end failed");
      }
    }
  }
}
