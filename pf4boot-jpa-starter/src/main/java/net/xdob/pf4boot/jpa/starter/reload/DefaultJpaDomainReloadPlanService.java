package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.jpa.domain.JpaDomainDescriptor;
import net.xdob.pf4boot.jpa.reload.JpaDomainConsumer;
import net.xdob.pf4boot.jpa.reload.JpaDomainConsumerDetection;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadBlocker;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadFailureCode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadMode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlan;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlanService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest;
import net.xdob.pf4boot.jpa.starter.JpaDomainBinding;
import net.xdob.pf4boot.jpa.starter.JpaPluginBinding;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 默认 JPA domain 刷新计划服务。
 *
 * <p>该服务只生成 PLAN_ONLY 结果，不允许产生插件生命周期副作用。</p>
 */
public class DefaultJpaDomainReloadPlanService implements JpaDomainReloadPlanService {

  private final Pf4bootPluginManager pluginManager;
  private final JpaPluginBindingRegistry bindingRegistry;
  private final Pf4bootJpaProperties properties;

  public DefaultJpaDomainReloadPlanService(
      Pf4bootPluginManager pluginManager,
      JpaPluginBindingRegistry bindingRegistry,
      Pf4bootJpaProperties properties) {
    this.pluginManager = pluginManager;
    this.bindingRegistry = bindingRegistry;
    this.properties = properties == null ? new Pf4bootJpaProperties() : properties;
  }

  @Override
  public JpaDomainReloadPlan plan(JpaDomainReloadRequest request) {
    long now = System.currentTimeMillis();
    String domainId = request == null ? null : request.getDomainId();
    JpaDomainReloadMode mode = resolveMode(request);
    List<JpaDomainReloadBlocker> blockers = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    if (!StringUtils.hasText(domainId)) {
      blockers.add(blocker(JpaDomainReloadFailureCode.INVALID_REQUEST, "domainId is required", domainId));
      return emptyPlan(domainId, blockers, warnings, now);
    }
    if (mode == JpaDomainReloadMode.DISABLED) {
      blockers.add(blocker(JpaDomainReloadFailureCode.RELOAD_DISABLED, "JPA domain reload is disabled", domainId));
    }
    if (StringUtils.hasText(request == null ? null : request.getProviderReplacementPath())) {
      blockers.add(blocker(JpaDomainReloadFailureCode.UNSUPPORTED_REPLACEMENT_PATH,
          "providerReplacementPath is not supported in V1", domainId));
    }

    JpaDomainDescriptor descriptor = findDescriptor(domainId);
    if (descriptor == null) {
      blockers.add(blocker(JpaDomainReloadFailureCode.DOMAIN_NOT_FOUND, "JPA domain descriptor not found", domainId));
      return plan(domainId, null, null, Collections.<JpaDomainConsumer>emptyList(),
          Collections.<JpaDomainConsumer>emptyList(), allPluginIds(), Collections.<String>emptyList(),
          Collections.<String>emptyList(), warnings, blockers, now);
    }
    if (!descriptor.isReady()) {
      blockers.add(blocker(JpaDomainReloadFailureCode.DOMAIN_NOT_READY, "JPA domain descriptor is not ready", domainId));
    }

    String providerPluginId = descriptor.getProviderPluginId();
    PluginWrapper provider = pluginManager == null ? null : pluginManager.getPlugin(providerPluginId);
    if (provider == null || provider.getPluginState() == null || !provider.getPluginState().isStarted()) {
      blockers.add(blocker(JpaDomainReloadFailureCode.PROVIDER_NOT_RUNNING,
          "JPA domain provider is not running", providerPluginId));
    }

    Map<String, JpaDomainConsumer> exactConsumers = exactConsumers(domainId);
    Map<String, JpaDomainConsumer> inferredConsumers = inferredConsumers(providerPluginId, domainId, exactConsumers);
    if (!inferredConsumers.isEmpty()) {
      blockers.add(blocker(JpaDomainReloadFailureCode.INFERRED_CONSUMER_PRESENT,
          "JPA domain has inferred consumers", domainId));
    }
    if (mode == JpaDomainReloadMode.PLAN_ONLY) {
      blockers.add(blocker(JpaDomainReloadFailureCode.PLAN_ONLY_MODE,
          "JPA domain reload is configured as PLAN_ONLY", domainId));
    }

    List<String> stopOrder = stopOrder(exactConsumers.keySet(), providerPluginId);
    List<String> startOrder = new ArrayList<>(stopOrder);
    Collections.reverse(startOrder);
    List<String> unaffected = unaffectedPlugins(providerPluginId, exactConsumers.keySet(), inferredConsumers.keySet());

    List<JpaDomainConsumer> consumers = sortedConsumers(exactConsumers);
    List<JpaDomainConsumer> inferred = sortedConsumers(inferredConsumers);
    return plan(domainId, providerPluginId, descriptor, consumers, inferred, unaffected,
        stopOrder, startOrder, warnings, blockers, now);
  }

  private JpaDomainReloadPlan emptyPlan(
      String domainId,
      List<JpaDomainReloadBlocker> blockers,
      List<String> warnings,
      long now) {
    return plan(domainId, null, null, Collections.<JpaDomainConsumer>emptyList(),
        Collections.<JpaDomainConsumer>emptyList(), Collections.<String>emptyList(),
        Collections.<String>emptyList(), Collections.<String>emptyList(), warnings, blockers, now);
  }

  private JpaDomainReloadPlan plan(
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
      long now) {
    return new JpaDomainReloadPlan(
        "jpa-plan-" + UUID.randomUUID().toString(),
        domainId,
        providerPluginId,
        descriptor,
        consumers,
        inferredConsumers,
        unaffectedPlugins,
        stopOrder,
        startOrder,
        warnings,
        blockers,
        now,
        blockers == null || blockers.isEmpty());
  }

  private JpaDomainReloadMode resolveMode(JpaDomainReloadRequest request) {
    JpaDomainReloadMode configuredMode = properties.getDomainReload().getMode();
    if (configuredMode == JpaDomainReloadMode.DISABLED || configuredMode == JpaDomainReloadMode.PLAN_ONLY) {
      return configuredMode;
    }
    if (request != null && request.getMode() != null) {
      return request.getMode();
    }
    return configuredMode;
  }

  private JpaDomainDescriptor findDescriptor(String domainId) {
    if (pluginManager == null) {
      return null;
    }
    String beanName = "domain." + domainId + ".descriptor";
    for (String group : platformGroups()) {
      ConfigurableApplicationContext context = pluginManager.getPlatformContext(group);
      if (context == null) {
        continue;
      }
      try {
        if (context.containsBean(beanName)) {
          return context.getBean(beanName, JpaDomainDescriptor.class);
        }
      } catch (Exception ignored) {
        // 忽略单个 group 的临时异常，继续查找其它平台上下文。
      }
    }
    return null;
  }

  private Set<String> platformGroups() {
    Set<String> groups = new LinkedHashSet<>();
    groups.add(PluginStarter.DEFAULT);
    for (PluginWrapper wrapper : plugins()) {
      try {
        if (wrapper != null && wrapper.getPlugin() instanceof Pf4bootPlugin) {
          groups.add(((Pf4bootPlugin) wrapper.getPlugin()).getGroup());
        }
      } catch (RuntimeException ignored) {
        // 部分测试或未实例化插件无法返回 plugin 实例，默认 group 仍可覆盖 V1 常见场景。
      }
    }
    return groups;
  }

  private Map<String, JpaDomainConsumer> exactConsumers(String domainId) {
    Map<String, JpaDomainConsumer> consumers = new HashMap<>();
    if (bindingRegistry == null) {
      return consumers;
    }
    for (JpaPluginBinding binding : bindingRegistry.findByDomainId(domainId)) {
      String pluginId = binding.getPluginId();
      PluginWrapper wrapper = pluginManager == null ? null : pluginManager.getPlugin(pluginId);
      consumers.put(pluginId, consumer(binding, pluginVersion(wrapper), JpaDomainConsumerDetection.EXACT_BINDING));
    }
    return consumers;
  }

  private Map<String, JpaDomainConsumer> inferredConsumers(
      String providerPluginId,
      String domainId,
      Map<String, JpaDomainConsumer> exactConsumers) {
    Map<String, JpaDomainConsumer> consumers = new HashMap<>();
    if (!StringUtils.hasText(providerPluginId)) {
      return consumers;
    }
    for (PluginWrapper wrapper : plugins()) {
      String pluginId = wrapper.getPluginId();
      if (providerPluginId.equals(pluginId) || exactConsumers.containsKey(pluginId)) {
        continue;
      }
      List<String> path = dependencyPath(pluginId, providerPluginId);
      if (!path.isEmpty()) {
        consumers.put(pluginId, new JpaDomainConsumer(
            pluginId,
            pluginVersion(wrapper),
            domainId,
            "UNKNOWN",
            JpaDomainConsumerDetection.INFERRED_DEPENDENCY,
            path,
            null,
            null,
            null));
      }
    }
    return consumers;
  }

  private JpaDomainConsumer consumer(
      JpaPluginBinding binding,
      String pluginVersion,
      JpaDomainConsumerDetection detection) {
    JpaDomainBinding primary = binding.primaryDomain();
    return new JpaDomainConsumer(
        binding.getPluginId(),
        pluginVersion,
        primary.getDomainId(),
        binding.getMode().name(),
        detection,
        Collections.<String>emptyList(),
        primary.resolveEntityManagerFactoryRef(),
        primary.resolveTransactionManagerRef(),
        primary.resolveDescriptorRef());
  }

  private List<String> unaffectedPlugins(
      String providerPluginId,
      Set<String> exactConsumers,
      Set<String> inferredConsumers) {
    List<String> result = new ArrayList<>();
    for (PluginWrapper wrapper : plugins()) {
      String pluginId = wrapper.getPluginId();
      if (providerPluginId != null && providerPluginId.equals(pluginId)) {
        continue;
      }
      if (exactConsumers.contains(pluginId) || inferredConsumers.contains(pluginId)) {
        continue;
      }
      result.add(pluginId);
    }
    result.sort(String.CASE_INSENSITIVE_ORDER);
    return result;
  }

  private List<String> stopOrder(Set<String> pluginIds, String providerPluginId) {
    List<String> result = new ArrayList<>(pluginIds);
    result.sort((left, right) -> {
      boolean leftDependsOnRight = dependsOn(left, right);
      boolean rightDependsOnLeft = dependsOn(right, left);
      if (leftDependsOnRight && !rightDependsOnLeft) {
        return -1;
      }
      if (rightDependsOnLeft && !leftDependsOnRight) {
        return 1;
      }
      return left.compareToIgnoreCase(right);
    });
    return result;
  }

  private boolean dependsOn(String pluginId, String dependencyId) {
    return !dependencyPath(pluginId, dependencyId).isEmpty();
  }

  private List<String> dependencyPath(String pluginId, String dependencyId) {
    if (!StringUtils.hasText(pluginId) || !StringUtils.hasText(dependencyId)) {
      return Collections.emptyList();
    }
    ArrayDeque<String> path = new ArrayDeque<>();
    Set<String> visited = new HashSet<>();
    if (dependencyPath(pluginId, dependencyId, visited, path)) {
      return new ArrayList<>(path);
    }
    return Collections.emptyList();
  }

  private boolean dependencyPath(
      String pluginId,
      String dependencyId,
      Set<String> visited,
      ArrayDeque<String> path) {
    if (!visited.add(pluginId)) {
      return false;
    }
    path.addLast(pluginId);
    PluginWrapper wrapper = pluginManager == null ? null : pluginManager.getPlugin(pluginId);
    if (wrapper == null || wrapper.getDescriptor() == null) {
      path.removeLast();
      return false;
    }
    for (PluginDependency dependency : wrapper.getDescriptor().getDependencies()) {
      if (dependencyId.equals(dependency.getPluginId())) {
        path.addLast(dependencyId);
        return true;
      }
      if (dependencyPath(dependency.getPluginId(), dependencyId, visited, path)) {
        return true;
      }
    }
    path.removeLast();
    return false;
  }

  private List<PluginWrapper> plugins() {
    if (pluginManager == null || pluginManager.getPlugins() == null) {
      return Collections.emptyList();
    }
    List<PluginWrapper> wrappers = new ArrayList<>(pluginManager.getPlugins());
    wrappers.sort(Comparator.comparing(PluginWrapper::getPluginId, String.CASE_INSENSITIVE_ORDER));
    return wrappers;
  }

  private List<String> allPluginIds() {
    List<String> ids = new ArrayList<>();
    for (PluginWrapper wrapper : plugins()) {
      ids.add(wrapper.getPluginId());
    }
    return ids;
  }

  private List<JpaDomainConsumer> sortedConsumers(Map<String, JpaDomainConsumer> consumers) {
    List<JpaDomainConsumer> values = new ArrayList<>(consumers.values());
    values.sort(Comparator.comparing(JpaDomainConsumer::getPluginId, String.CASE_INSENSITIVE_ORDER));
    return values;
  }

  private String pluginVersion(PluginWrapper wrapper) {
    PluginDescriptor descriptor = wrapper == null ? null : wrapper.getDescriptor();
    return descriptor == null ? null : descriptor.getVersion();
  }

  private JpaDomainReloadBlocker blocker(
      JpaDomainReloadFailureCode code,
      String message,
      String subject) {
    return new JpaDomainReloadBlocker(code, message, subject);
  }
}
