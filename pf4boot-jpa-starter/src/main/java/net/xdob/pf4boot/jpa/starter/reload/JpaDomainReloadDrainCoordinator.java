package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import net.xdob.pf4boot.jpa.reload.JpaDomainDrainReport;
import net.xdob.pf4boot.jpa.reload.JpaDomainDrainerPhase;
import net.xdob.pf4boot.jpa.reload.JpaDomainDrainerResult;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadFailureCode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlan;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * JPA domain 刷新的 drain 编排器。
 *
 * <p>该组件复用通用 {@link PluginTrafficDrainer}，在停止 consumer/provider 前阻止新入口并等待在途工作归零。</p>
 */
public class JpaDomainReloadDrainCoordinator {

  private final List<NamedDrainer> drainers;
  private final Pf4bootJpaProperties properties;
  private final ConcurrentHashMap<String, List<NamedDrainer>> begunDrainersByPlanId = new ConcurrentHashMap<>();

  public JpaDomainReloadDrainCoordinator(
      ObjectProvider<PluginTrafficDrainer> trafficDrainers,
      Pf4bootJpaProperties properties) {
    this(trafficDrainers == null
        ? Collections.<PluginTrafficDrainer>emptyList()
        : trafficDrainers.orderedStream().collect(Collectors.toList()), properties);
  }

  public JpaDomainReloadDrainCoordinator(
      List<PluginTrafficDrainer> trafficDrainers,
      Pf4bootJpaProperties properties) {
    this.drainers = namedDrainers(trafficDrainers);
    this.properties = properties == null ? new Pf4bootJpaProperties() : properties;
  }

  /**
   * 开始 drain 并等待在途工作归零。
   */
  public JpaDomainDrainReport drain(JpaDomainReloadPlan plan, long timeoutMillis) {
    List<String> pluginIds = impactPluginIds(plan);
    long startedAt = System.currentTimeMillis();
    List<JpaDomainDrainerResult> results = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<NamedDrainer> begun = new ArrayList<>();
    if (drainers.isEmpty()) {
      if (properties.getDomainReload().isRequireDrainer()) {
        return rejected("no PluginTrafficDrainer found", pluginIds, startedAt, results, warnings);
      }
      warnings.add("no PluginTrafficDrainer found");
      return accepted("no drainer, continue for compatibility", pluginIds, startedAt, results, warnings);
    }
    try {
      for (NamedDrainer drainer : drainers) {
        long phaseStarted = System.currentTimeMillis();
        try {
          drainer.delegate.beginDrain(pluginIds);
          begun.add(drainer);
          results.add(result(drainer, JpaDomainDrainerPhase.BEGIN, true, phaseStarted, "begun"));
        } catch (RuntimeException e) {
          results.add(result(drainer, JpaDomainDrainerPhase.BEGIN, false, phaseStarted, sanitize(e)));
          endBegunDrainers(begun, pluginIds, results, warnings);
          return rejected("drainer begin rejected", pluginIds, startedAt, results, warnings);
        }
      }
      long deadline = timeoutMillis <= 0 ? System.currentTimeMillis() : System.currentTimeMillis() + timeoutMillis;
      for (NamedDrainer drainer : drainers) {
        long remaining = timeoutMillis <= 0 ? 0L : Math.max(0L, deadline - System.currentTimeMillis());
        long phaseStarted = System.currentTimeMillis();
        try {
          boolean drained = drainer.delegate.awaitDrain(pluginIds, remaining);
          results.add(result(drainer, JpaDomainDrainerPhase.AWAIT, drained, phaseStarted,
              drained ? "drained" : "timeout"));
          if (!drained) {
            endBegunDrainers(begun, pluginIds, results, warnings);
            return timeout("drainer await timed out", pluginIds, startedAt, results, warnings);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          results.add(result(drainer, JpaDomainDrainerPhase.AWAIT, false, phaseStarted, "interrupted"));
          endBegunDrainers(begun, pluginIds, results, warnings);
          return rejected("drainer interrupted", pluginIds, startedAt, results, warnings);
        } catch (RuntimeException e) {
          results.add(result(drainer, JpaDomainDrainerPhase.AWAIT, false, phaseStarted, sanitize(e)));
          endBegunDrainers(begun, pluginIds, results, warnings);
          return rejected("drainer await rejected", pluginIds, startedAt, results, warnings);
        }
      }
      String planId = plan == null ? null : plan.getPlanId();
      if (planId != null) {
        begunDrainersByPlanId.put(planId, new ArrayList<>(begun));
      }
      return accepted("drained", pluginIds, startedAt, results, warnings);
    } catch (RuntimeException e) {
      endBegunDrainers(begun, pluginIds, results, warnings);
      return rejected("drainer failed", pluginIds, startedAt, results, warnings);
    }
  }

  /**
   * 结束本次 plan 的 drain 状态。
   */
  public JpaDomainDrainReport endDrain(JpaDomainReloadPlan plan) {
    String planId = plan == null ? null : plan.getPlanId();
    List<NamedDrainer> begun = planId == null ? Collections.<NamedDrainer>emptyList() : begunDrainersByPlanId.remove(planId);
    if (begun == null) {
      begun = Collections.emptyList();
    }
    List<String> pluginIds = impactPluginIds(plan);
    long startedAt = System.currentTimeMillis();
    List<JpaDomainDrainerResult> results = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    endBegunDrainers(begun, pluginIds, results, warnings);
    return new JpaDomainDrainReport(
        warnings.isEmpty(),
        warnings.isEmpty() ? null : JpaDomainReloadFailureCode.DRAIN_REJECTED,
        warnings.isEmpty() ? "drain ended" : "drain ended with warnings",
        pluginIds,
        results,
        startedAt,
        System.currentTimeMillis(),
        warnings);
  }

  private List<String> impactPluginIds(JpaDomainReloadPlan plan) {
    if (plan == null) {
      return Collections.emptyList();
    }
    Map<String, Boolean> ids = new LinkedHashMap<>();
    for (String pluginId : plan.getStopOrder()) {
      if (hasText(pluginId)) {
        ids.put(pluginId, Boolean.TRUE);
      }
    }
    if (hasText(plan.getProviderPluginId())) {
      ids.put(plan.getProviderPluginId(), Boolean.TRUE);
    }
    return Collections.unmodifiableList(new ArrayList<>(ids.keySet()));
  }

  private void endBegunDrainers(
      List<NamedDrainer> begun,
      List<String> pluginIds,
      List<JpaDomainDrainerResult> results,
      List<String> warnings) {
    for (int i = begun.size() - 1; i >= 0; i--) {
      NamedDrainer drainer = begun.get(i);
      long phaseStarted = System.currentTimeMillis();
      try {
        drainer.delegate.endDrain(pluginIds);
        results.add(result(drainer, JpaDomainDrainerPhase.END, true, phaseStarted, "ended"));
      } catch (RuntimeException e) {
        String message = "endDrain failed: " + drainer.name + ": " + sanitize(e);
        warnings.add(message);
        results.add(result(drainer, JpaDomainDrainerPhase.END, false, phaseStarted, sanitize(e)));
      }
    }
  }

  private JpaDomainDrainReport accepted(
      String message,
      List<String> pluginIds,
      long startedAt,
      List<JpaDomainDrainerResult> results,
      List<String> warnings) {
    return new JpaDomainDrainReport(
        true,
        null,
        message,
        pluginIds,
        results,
        startedAt,
        System.currentTimeMillis(),
        warnings);
  }

  private JpaDomainDrainReport timeout(
      String message,
      List<String> pluginIds,
      long startedAt,
      List<JpaDomainDrainerResult> results,
      List<String> warnings) {
    return failed(JpaDomainReloadFailureCode.DRAIN_TIMEOUT, message, pluginIds, startedAt, results, warnings);
  }

  private JpaDomainDrainReport rejected(
      String message,
      List<String> pluginIds,
      long startedAt,
      List<JpaDomainDrainerResult> results,
      List<String> warnings) {
    return failed(JpaDomainReloadFailureCode.DRAIN_REJECTED, message, pluginIds, startedAt, results, warnings);
  }

  private JpaDomainDrainReport failed(
      JpaDomainReloadFailureCode failureCode,
      String message,
      List<String> pluginIds,
      long startedAt,
      List<JpaDomainDrainerResult> results,
      List<String> warnings) {
    return new JpaDomainDrainReport(
        false,
        failureCode,
        message,
        pluginIds,
        results,
        startedAt,
        System.currentTimeMillis(),
        warnings);
  }

  private JpaDomainDrainerResult result(
      NamedDrainer drainer,
      JpaDomainDrainerPhase phase,
      boolean accepted,
      long startedAt,
      String message) {
    return new JpaDomainDrainerResult(
        drainer.name,
        phase,
        accepted,
        startedAt,
        System.currentTimeMillis(),
        message);
  }

  private static List<NamedDrainer> namedDrainers(List<PluginTrafficDrainer> drainers) {
    if (drainers == null || drainers.isEmpty()) {
      return Collections.emptyList();
    }
    List<NamedDrainer> result = new ArrayList<>();
    int index = 0;
    for (PluginTrafficDrainer drainer : drainers) {
      if (drainer != null) {
        result.add(new NamedDrainer(drainerName(drainer, index++), drainer));
      }
    }
    return Collections.unmodifiableList(result);
  }

  private static String drainerName(PluginTrafficDrainer drainer, int index) {
    String className = drainer.getClass().getName();
    return hasText(className) ? className : "PluginTrafficDrainer#" + index;
  }

  private static String sanitize(Exception e) {
    String message = e == null ? null : e.getMessage();
    if (!hasText(message)) {
      message = e == null ? "unknown" : e.getClass().getSimpleName();
    }
    return message.length() <= 512 ? message : message.substring(0, 512);
  }

  private static boolean hasText(String value) {
    return value != null && value.trim().length() > 0;
  }

  private static class NamedDrainer {
    private final String name;
    private final PluginTrafficDrainer delegate;

    private NamedDrainer(String name, PluginTrafficDrainer delegate) {
      this.name = name;
      this.delegate = delegate;
    }
  }
}
