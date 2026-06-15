package net.xdob.pf4boot.jpa.reload;

import net.xdob.pf4boot.jpa.domain.JpaDomainDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JPA domain 刷新计划。
 */
public class JpaDomainReloadPlan {

  private final String planId;
  private final String domainId;
  private final String providerPluginId;
  private final JpaDomainDescriptor descriptor;
  private final List<JpaDomainConsumer> consumers;
  private final List<JpaDomainConsumer> inferredConsumers;
  private final List<String> unaffectedPlugins;
  private final List<String> stopOrder;
  private final List<String> startOrder;
  private final List<String> warnings;
  private final List<JpaDomainReloadBlocker> blockers;
  private final long createdAt;
  private final boolean executable;

  public JpaDomainReloadPlan(
      String planId,
      String domainId,
      String providerPluginId,
      JpaDomainDescriptor descriptor,
      List<JpaDomainConsumer> consumers,
      List<JpaDomainConsumer> inferredConsumers,
      List<String> unaffectedPlugins,
      List<String> stopOrder,
      List<String> startOrder,
      List<String> warnings,
      List<JpaDomainReloadBlocker> blockers,
      long createdAt,
      boolean executable) {
    this.planId = planId;
    this.domainId = domainId;
    this.providerPluginId = providerPluginId;
    this.descriptor = descriptor;
    this.consumers = copy(consumers);
    this.inferredConsumers = copy(inferredConsumers);
    this.unaffectedPlugins = copyStrings(unaffectedPlugins);
    this.stopOrder = copyStrings(stopOrder);
    this.startOrder = copyStrings(startOrder);
    this.warnings = copyStrings(warnings);
    this.blockers = copy(blockers);
    this.createdAt = createdAt;
    this.executable = executable;
  }

  public String getPlanId() {
    return planId;
  }

  public String getDomainId() {
    return domainId;
  }

  public String getProviderPluginId() {
    return providerPluginId;
  }

  public JpaDomainDescriptor getDescriptor() {
    return descriptor;
  }

  public List<JpaDomainConsumer> getConsumers() {
    return consumers;
  }

  public List<JpaDomainConsumer> getInferredConsumers() {
    return inferredConsumers;
  }

  public List<String> getUnaffectedPlugins() {
    return unaffectedPlugins;
  }

  public List<String> getStopOrder() {
    return stopOrder;
  }

  public List<String> getStartOrder() {
    return startOrder;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public List<JpaDomainReloadBlocker> getBlockers() {
    return blockers;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public boolean isExecutable() {
    return executable;
  }

  private static <T> List<T> copy(List<T> values) {
    return values == null
        ? Collections.<T>emptyList()
        : Collections.unmodifiableList(new ArrayList<>(values));
  }

  private static List<String> copyStrings(List<String> values) {
    return values == null
        ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<>(values));
  }
}
