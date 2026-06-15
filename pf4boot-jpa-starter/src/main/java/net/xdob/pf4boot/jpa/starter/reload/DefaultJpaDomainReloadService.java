package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.jpa.domain.JpaDomainDescriptor;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadBlocker;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadFailureCode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadMode;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlan;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadState;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 默认 JPA domain 重启式刷新服务。
 */
public class DefaultJpaDomainReloadService implements JpaDomainReloadService {

  private final Pf4bootPluginManager pluginManager;
  private final DefaultJpaDomainReloadPlanService planService;
  private final JpaDomainReloadRecordRepository recordRepository;
  private final Pf4bootJpaProperties properties;
  private final ReentrantLock globalLock = new ReentrantLock();
  private final ConcurrentHashMap<String, ReentrantLock> domainLocks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> currentReloads = new ConcurrentHashMap<>();

  public DefaultJpaDomainReloadService(
      Pf4bootPluginManager pluginManager,
      DefaultJpaDomainReloadPlanService planService,
      JpaDomainReloadRecordRepository recordRepository,
      Pf4bootJpaProperties properties) {
    this.pluginManager = pluginManager;
    this.planService = planService;
    this.recordRepository = recordRepository;
    this.properties = properties == null ? new Pf4bootJpaProperties() : properties;
  }

  @Override
  public JpaDomainReloadPlan plan(JpaDomainReloadRequest request) {
    return planService.plan(request);
  }

  @Override
  public JpaDomainReloadRecord reload(JpaDomainReloadRequest request) {
    validateRequest(request);
    if (StringUtils.hasText(request.getProviderReplacementPath())) {
      JpaDomainReloadRecord record = failedRecord(
          request,
          null,
          JpaDomainReloadFailureCode.UNSUPPORTED_REPLACEMENT_PATH,
          "providerReplacementPath is not supported in V1");
      save(record);
      return record;
    }
    JpaDomainReloadRecord replay = recordRepository.findByIdempotencyKey(request.getIdempotencyKey());
    if (replay != null) {
      return replay;
    }
    String domainId = request.getDomainId();
    ReentrantLock domainLock = domainLocks.computeIfAbsent(domainId, key -> new ReentrantLock());
    if (!globalLock.tryLock()) {
      return failedRecord(request, null, JpaDomainReloadFailureCode.CONCURRENT_RELOAD, "another reload is running");
    }
    boolean domainLocked = false;
    String reloadId = "jpa-reload-" + UUID.randomUUID().toString();
    try {
      domainLocked = domainLock.tryLock();
      if (!domainLocked) {
        return failedRecord(request, null, JpaDomainReloadFailureCode.CONCURRENT_RELOAD, "domain reload is running");
      }
      currentReloads.put(domainId, reloadId);
      JpaDomainReloadPlan plan = planService.plan(request);
      if (!plan.isExecutable()) {
        JpaDomainReloadRecord record = failedRecord(request, plan, firstBlocker(plan), "JPA domain reload plan is not executable");
        save(record);
        return record;
      }
      List<JpaDomainReloadState> states = new ArrayList<>();
      long startedAt = System.currentTimeMillis();
      try {
        states.add(JpaDomainReloadState.PLANNED);
        states.add(JpaDomainReloadState.DRAINING);
        states.add(JpaDomainReloadState.STOPPING_CONSUMERS);
        stopPlugins(plan.getStopOrder());
        states.add(JpaDomainReloadState.STOPPING_PROVIDER);
        stopPlugin(plan.getProviderPluginId(), JpaDomainReloadFailureCode.PROVIDER_STOP_FAILED);
        verifyProviderExportsRemoved(request, plan);
        states.add(JpaDomainReloadState.STARTING_PROVIDER);
        startProviderWithRetry(plan.getProviderPluginId());
        states.add(JpaDomainReloadState.STARTING_CONSUMERS);
        startPlugins(plan.getStartOrder());
        states.add(JpaDomainReloadState.HEALTH_CHECKING);
        JpaDomainReloadPlan afterStartPlan = planService.plan(request);
        if (afterStartPlan.getDescriptor() == null || !afterStartPlan.getDescriptor().isReady()) {
          throw new ReloadStepException(JpaDomainReloadFailureCode.HEALTH_CHECK_FAILED, "provider descriptor is not ready");
        }
        states.add(JpaDomainReloadState.SUCCEEDED);
        JpaDomainReloadRecord record = new JpaDomainReloadRecord(
            reloadId,
            plan.getPlanId(),
            domainId,
            JpaDomainReloadState.SUCCEEDED,
            startedAt,
            System.currentTimeMillis(),
            request,
            plan,
            states,
            null,
            null,
            null);
        save(record);
        return record;
      } catch (ReloadStepException e) {
        JpaDomainReloadRecord record = new JpaDomainReloadRecord(
            reloadId,
            plan.getPlanId(),
            domainId,
            JpaDomainReloadState.MANUAL_INTERVENTION_REQUIRED,
            startedAt,
            System.currentTimeMillis(),
            request,
            plan,
            states,
            e.failureCode,
            e.getMessage(),
            "manual intervention required");
        save(record);
        return record;
      }
    } finally {
      currentReloads.remove(domainId, reloadId);
      if (domainLocked) {
        domainLock.unlock();
      }
      globalLock.unlock();
    }
  }

  @Override
  public JpaDomainReloadRecord getRecord(String reloadId) {
    return recordRepository.findById(reloadId);
  }

  @Override
  public JpaDomainReloadRecord getCurrent(String domainId) {
    String reloadId = currentReloads.get(domainId);
    return reloadId == null ? null : recordRepository.findById(reloadId);
  }

  private void validateRequest(JpaDomainReloadRequest request) {
    if (request == null || !StringUtils.hasText(request.getDomainId())) {
      throw new IllegalArgumentException("domainId is required");
    }
    if (properties.getDomainReload().isRequireIdempotencyKey()
        && !StringUtils.hasText(request.getIdempotencyKey())) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    if (request.getReason() != null && request.getReason().length() > 512) {
      throw new IllegalArgumentException("reason is too long");
    }
  }

  private void stopPlugins(List<String> pluginIds) {
    for (String pluginId : pluginIds) {
      stopPlugin(pluginId, JpaDomainReloadFailureCode.CONSUMER_STOP_FAILED);
    }
  }

  private void startPlugins(List<String> pluginIds) {
    for (String pluginId : pluginIds) {
      startPlugin(pluginId, JpaDomainReloadFailureCode.CONSUMER_START_FAILED);
    }
  }

  private void stopPlugin(String pluginId, JpaDomainReloadFailureCode code) {
    PluginWrapper plugin = pluginManager.getPlugin(pluginId);
    if (plugin != null && plugin.getPluginState() != null && plugin.getPluginState().isStarted()) {
      PluginState state = pluginManager.stopPlugin(pluginId);
      if (state == null || state.isStarted()) {
        throw new ReloadStepException(code, "plugin stop failed: " + pluginId);
      }
    }
  }

  private void startPlugin(String pluginId, JpaDomainReloadFailureCode code) {
    PluginState state = pluginManager.startPlugin(pluginId);
    if (state == null || !state.isStarted()) {
      throw new ReloadStepException(code, "plugin start failed: " + pluginId);
    }
  }

  private void startProviderWithRetry(String pluginId) {
    try {
      startPlugin(pluginId, JpaDomainReloadFailureCode.PROVIDER_START_FAILED);
    } catch (ReloadStepException firstFailure) {
      startPlugin(pluginId, JpaDomainReloadFailureCode.PROVIDER_START_FAILED);
    }
  }

  private void verifyProviderExportsRemoved(JpaDomainReloadRequest request, JpaDomainReloadPlan plan) {
    JpaDomainDescriptor descriptor = plan == null ? null : plan.getDescriptor();
    if (descriptor != null) {
      assertPlatformBeanAbsent(descriptor.getDataSourceBeanName(), request.getDomainId());
      assertPlatformBeanAbsent(descriptor.getEntityManagerFactoryBeanName(), request.getDomainId());
      assertPlatformBeanAbsent(descriptor.getTransactionManagerBeanName(), request.getDomainId());
      assertPlatformBeanAbsent("domain." + request.getDomainId() + ".descriptor", request.getDomainId());
    }
    JpaDomainReloadPlan stoppedPlan = planService.plan(request);
    if (stoppedPlan.getDescriptor() != null) {
      throw new ReloadStepException(
          JpaDomainReloadFailureCode.PROVIDER_STOP_FAILED,
          "provider JPA exports were not removed: " + request.getDomainId());
    }
  }

  private void assertPlatformBeanAbsent(String beanName, String domainId) {
    if (!StringUtils.hasText(beanName) || pluginManager == null) {
      return;
    }
    for (String group : platformGroups()) {
      ConfigurableApplicationContext context = pluginManager.getPlatformContext(group);
      if (context != null && context.containsBean(beanName)) {
        throw new ReloadStepException(
            JpaDomainReloadFailureCode.PROVIDER_STOP_FAILED,
            "provider JPA export bean was not removed: " + domainId + "/" + beanName);
      }
    }
  }

  private Set<String> platformGroups() {
    Set<String> groups = new LinkedHashSet<>();
    groups.add(PluginStarter.DEFAULT);
    if (pluginManager == null || pluginManager.getPlugins() == null) {
      return groups;
    }
    for (PluginWrapper wrapper : pluginManager.getPlugins()) {
      try {
        if (wrapper != null && wrapper.getPlugin() instanceof Pf4bootPlugin) {
          groups.add(((Pf4bootPlugin) wrapper.getPlugin()).getGroup());
        }
      } catch (RuntimeException ignored) {
        // 测试或未实例化插件可能无法返回插件实例，默认 group 仍覆盖常规导出路径。
      }
    }
    return groups;
  }

  private JpaDomainReloadFailureCode firstBlocker(JpaDomainReloadPlan plan) {
    if (plan == null || plan.getBlockers().isEmpty()) {
      return JpaDomainReloadFailureCode.OPERATION_FAILED;
    }
    JpaDomainReloadBlocker blocker = plan.getBlockers().get(0);
    return blocker.getCode() == null ? JpaDomainReloadFailureCode.OPERATION_FAILED : blocker.getCode();
  }

  private JpaDomainReloadRecord failedRecord(
      JpaDomainReloadRequest request,
      JpaDomainReloadPlan plan,
      JpaDomainReloadFailureCode code,
      String message) {
    return new JpaDomainReloadRecord(
        "jpa-reload-" + UUID.randomUUID().toString(),
        plan == null ? null : plan.getPlanId(),
        request == null ? null : request.getDomainId(),
        JpaDomainReloadState.FAILED,
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        request,
        plan,
        Collections.singletonList(JpaDomainReloadState.FAILED),
        code,
        message,
        null);
  }

  private void save(JpaDomainReloadRecord record) {
    recordRepository.save(record);
    if (record.getRequest() != null) {
      recordRepository.bindIdempotencyKey(record.getRequest().getIdempotencyKey(), record.getReloadId());
    }
  }

  private static class ReloadStepException extends RuntimeException {
    private final JpaDomainReloadFailureCode failureCode;

    ReloadStepException(JpaDomainReloadFailureCode failureCode, String message) {
      super(message);
      this.failureCode = failureCode;
    }
  }
}
