package net.xdob.pf4boot.deployment;

import com.google.common.base.Strings;
import net.xdob.pf4boot.DefaultPluginPackageVerifier;
import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.PluginPackageVerificationMode;
import net.xdob.pf4boot.PluginPackageVerificationResult;
import net.xdob.pf4boot.PluginPackageVerifier;
import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.spring.boot.Pf4bootProperties;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.pf4j.VersionManager;
import org.springframework.util.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 默认插件部署编排服务。
 *
 * <p>当前实现只负责生成热替换部署计划和预检结果，不执行插件生命周期动作，也不移动插件包。</p>
 */
public class DefaultPluginDeploymentService implements PluginDeploymentService {

  private final Pf4bootPluginManager pluginManager;
  private final Pf4bootProperties properties;
  private final List<PluginPackageVerifier> pluginPackageVerifiers;
  private final List<PluginTrafficDrainer> trafficDrainers;
  private final List<PluginCleanupVerifier> cleanupVerifiers;
  private final List<PluginHealthVerifier> healthVerifiers;
  private final List<PluginDeploymentRecorder> deploymentRecorders;

  public DefaultPluginDeploymentService(Pf4bootPluginManager pluginManager, Pf4bootProperties properties) {
    this(pluginManager, properties, Collections.emptyList());
  }

  public DefaultPluginDeploymentService(
      Pf4bootPluginManager pluginManager,
      Pf4bootProperties properties,
      List<PluginPackageVerifier> pluginPackageVerifiers) {
    this(pluginManager, properties, pluginPackageVerifiers, Collections.emptyList(), Collections.emptyList());
  }

  public DefaultPluginDeploymentService(
      Pf4bootPluginManager pluginManager,
      Pf4bootProperties properties,
      List<PluginPackageVerifier> pluginPackageVerifiers,
      List<PluginTrafficDrainer> trafficDrainers,
      List<PluginCleanupVerifier> cleanupVerifiers) {
    this(pluginManager, properties, pluginPackageVerifiers, trafficDrainers, cleanupVerifiers,
        Collections.emptyList());
  }

  public DefaultPluginDeploymentService(
      Pf4bootPluginManager pluginManager,
      Pf4bootProperties properties,
      List<PluginPackageVerifier> pluginPackageVerifiers,
      List<PluginTrafficDrainer> trafficDrainers,
      List<PluginCleanupVerifier> cleanupVerifiers,
      List<PluginDeploymentRecorder> deploymentRecorders) {
    this(pluginManager, properties, pluginPackageVerifiers, trafficDrainers, cleanupVerifiers,
        Collections.emptyList(), deploymentRecorders);
  }

  public DefaultPluginDeploymentService(
      Pf4bootPluginManager pluginManager,
      Pf4bootProperties properties,
      List<PluginPackageVerifier> pluginPackageVerifiers,
      List<PluginTrafficDrainer> trafficDrainers,
      List<PluginCleanupVerifier> cleanupVerifiers,
      List<PluginHealthVerifier> healthVerifiers,
      List<PluginDeploymentRecorder> deploymentRecorders) {
    Assert.notNull(pluginManager, "pluginManager must not be null");
    this.pluginManager = pluginManager;
    this.properties = properties == null ? new Pf4bootProperties() : properties;
    this.pluginPackageVerifiers = createPluginPackageVerifiers(pluginPackageVerifiers);
    this.trafficDrainers = unmodifiableCopy(trafficDrainers);
    this.cleanupVerifiers = unmodifiableCopy(cleanupVerifiers);
    this.healthVerifiers = unmodifiableCopy(healthVerifiers);
    this.deploymentRecorders = unmodifiableCopy(deploymentRecorders);
  }

  private List<PluginPackageVerifier> createPluginPackageVerifiers(
      List<PluginPackageVerifier> customVerifiers) {
    List<PluginPackageVerifier> verifiers = new ArrayList<>();
    verifiers.add(new DefaultPluginPackageVerifier(properties));
    if (customVerifiers != null) {
      verifiers.addAll(customVerifiers);
    }
    return Collections.unmodifiableList(verifiers);
  }

  private static <T> List<T> unmodifiableCopy(List<T> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }

  @Override
  public DeploymentRecord planReplacement(String targetPluginId, Path stagedPluginPath) {
    Assert.hasText(targetPluginId, "targetPluginId must not be empty");
    Assert.notNull(stagedPluginPath, "stagedPluginPath must not be null");

    String deploymentId = UUID.randomUUID().toString();
    long now = System.currentTimeMillis();
    List<DeploymentCheckResult> checks = new ArrayList<>();
    PluginWrapper currentPlugin = pluginManager.getPlugin(targetPluginId);
    PluginDescriptor stagedDescriptor = parseDescriptor(stagedPluginPath, checks);

    checkTargetPlugin(targetPluginId, currentPlugin, stagedDescriptor, checks);
    checkPackage(stagedPluginPath, stagedDescriptor, checks);
    checkSystemCompatibility(stagedDescriptor, checks);
    checkDependencies(stagedDescriptor, checks);
    checkVersionProgress(currentPlugin, stagedDescriptor, checks);

    List<String> stopOrder = stopOrder(targetPluginId);
    List<String> startOrder = reverse(stopOrder);
    List<String> affectedPluginIds = affectedPluginIds(targetPluginId, startOrder);
    RollbackSnapshot rollbackSnapshot = rollbackSnapshot(currentPlugin, stopOrder);

    DeploymentPlan plan = new DeploymentPlan(
        deploymentId,
        targetPluginId,
        stagedPluginPath.toAbsolutePath().normalize().toString(),
        currentPlugin == null ? null : currentPlugin.getPluginPath().toAbsolutePath().normalize().toString(),
        currentPlugin == null ? null : currentPlugin.getDescriptor().getVersion(),
        currentPlugin == null ? null : currentPlugin.getPluginState(),
        stagedDescriptor == null ? null : stagedDescriptor.getVersion(),
        stagedDescriptor == null ? null : stagedDescriptor.getRequires(),
        affectedPluginIds,
        stopOrder,
        startOrder,
        checks,
        rollbackSnapshot);
    DeploymentState state = plan.isExecutable() ? DeploymentState.PRECHECKED : DeploymentState.FAILED;
    DeploymentRecord record = new DeploymentRecord(deploymentId, targetPluginId, state, now, now,
        plan.isExecutable() ? "precheck passed" : "precheck failed", plan,
        DeploymentRecord.history(DeploymentState.PLANNED, state), 0,
        plan.isExecutable() ? null : "PRECHECK_FAILED");
    record(record);
    return record;
  }

  @Override
  public DeploymentRecord replace(String targetPluginId, Path stagedPluginPath) {
    DeploymentRecord precheckRecord = planReplacement(targetPluginId, stagedPluginPath);
    DeploymentPlan plan = precheckRecord.getPlan();
    if (!plan.isExecutable()) {
      return precheckRecord;
    }
    long startedAt = System.currentTimeMillis();

    try {
      beginDrain(plan);
      awaitDrain(plan);
      stopPlugins(plan.getStopOrder());
      verifyStoppedPlugins(plan.getStopOrder());
      unloadPlugins(plan.getStopOrder());
      String loadedPluginId = pluginManager.loadPlugin(stagedPluginPath);
      if (!targetPluginId.equals(loadedPluginId)) {
        throw new PluginRuntimeException(
            "Loaded plugin id '%s' does not match target '%s'", loadedPluginId, targetPluginId);
      }
      loadDependentPlugins(plan);
      startPlugins(plan.getStartOrder());
      verifyHealth(plan);
      return record(plan, DeploymentState.SUCCEEDED, "replacement succeeded", startedAt, null,
          DeploymentState.PRECHECKED, DeploymentState.APPLYING, DeploymentState.DRAINING,
          DeploymentState.STOPPING, DeploymentState.CLEANUP_VERIFYING, DeploymentState.ACTIVATING,
          DeploymentState.STARTING, DeploymentState.VERIFYING, DeploymentState.SUCCEEDED);
    } catch (Throwable replacementFailure) {
      return rollback(plan, replacementFailure, startedAt);
    } finally {
      endDrain(plan);
    }
  }

  private DeploymentRecord rollback(DeploymentPlan plan, Throwable replacementFailure, long startedAt) {
    try {
      stopPlugins(plan.getStopOrder());
      unloadPlugins(plan.getStopOrder());
      restoreSnapshot(plan);
      startSnapshotPlugins(plan);
      verifyHealth(plan);
      return record(plan, DeploymentState.FAILED,
          "replacement failed and rollback succeeded: " + replacementFailure.getMessage(),
          startedAt, errorCode(replacementFailure),
          DeploymentState.PRECHECKED, DeploymentState.APPLYING, DeploymentState.DRAINING,
          DeploymentState.VERIFYING, DeploymentState.ROLLING_BACK, DeploymentState.VERIFYING,
          DeploymentState.FAILED);
    } catch (Throwable rollbackFailure) {
      return record(plan, DeploymentState.MANUAL_INTERVENTION,
          "replacement failed and rollback also failed: " + rollbackFailure.getMessage(),
          startedAt, errorCode(rollbackFailure),
          DeploymentState.PRECHECKED, DeploymentState.APPLYING, DeploymentState.DRAINING,
          DeploymentState.ROLLING_BACK, DeploymentState.MANUAL_INTERVENTION);
    }
  }

  private DeploymentRecord record(
      DeploymentPlan plan,
      DeploymentState state,
      String message,
      long startedAt,
      String errorCode,
      DeploymentState... history) {
    long now = System.currentTimeMillis();
    DeploymentRecord record = new DeploymentRecord(plan.getDeploymentId(), plan.getTargetPluginId(), state,
        startedAt, now, message, plan, DeploymentRecord.history(history), now - startedAt, errorCode);
    record(record);
    return record;
  }

  private void record(DeploymentRecord record) {
    for (PluginDeploymentRecorder recorder : deploymentRecorders) {
      recorder.record(record);
    }
  }

  private String errorCode(Throwable failure) {
    if (failure == null || failure.getMessage() == null) {
      return "DEPLOYMENT_FAILED";
    }
    String message = failure.getMessage();
    if (message.contains("health")) {
      return "HEALTH_CHECK_FAILED";
    }
    if (message.contains("drain")) {
      return "DRAIN_TIMEOUT";
    }
    if (message.contains("cleanup")) {
      return "CLEANUP_VERIFICATION_FAILED";
    }
    if (message.contains("start")) {
      return "PLUGIN_START_FAILED";
    }
    return "DEPLOYMENT_FAILED";
  }

  private void stopPlugins(List<String> pluginIds) {
    for (String pluginId : pluginIds) {
      PluginWrapper plugin = pluginManager.getPlugin(pluginId);
      if (plugin != null && plugin.getPluginState().isStarted()) {
        pluginManager.stopPlugin(pluginId);
      }
    }
  }

  private void beginDrain(DeploymentPlan plan) {
    for (PluginTrafficDrainer drainer : trafficDrainers) {
      drainer.beginDrain(plan.getStartOrder());
    }
  }

  private void awaitDrain(DeploymentPlan plan) throws InterruptedException {
    for (PluginTrafficDrainer drainer : trafficDrainers) {
      boolean drained = drainer.awaitDrain(plan.getStartOrder(), properties.getPluginDrainTimeoutMillis());
      if (!drained) {
        throw new PluginRuntimeException(
            "Plugin drain timed out after %s ms: %s",
            properties.getPluginDrainTimeoutMillis(),
            plan.getStartOrder());
      }
    }
  }

  private void endDrain(DeploymentPlan plan) {
    for (PluginTrafficDrainer drainer : trafficDrainers) {
      try {
        drainer.endDrain(plan.getStartOrder());
      } catch (RuntimeException ignored) {
      }
    }
  }

  private void verifyStoppedPlugins(List<String> pluginIds) {
    if (cleanupVerifiers.isEmpty()) {
      return;
    }
    for (String pluginId : pluginIds) {
      PluginWrapper plugin = pluginManager.getPlugin(pluginId);
      if (plugin == null) {
        continue;
      }
      for (PluginCleanupVerifier verifier : cleanupVerifiers) {
        List<DeploymentCheckResult> results =
            verifier.verifyStoppedPlugin(pluginId, plugin.getPluginClassLoader());
        if (results == null) {
          continue;
        }
        for (DeploymentCheckResult result : results) {
          if (result != null && result.isError()) {
            throw new PluginRuntimeException(
                "Plugin cleanup verification failed for '%s': [%s] %s",
                pluginId,
                result.getCode(),
                result.getMessage());
          }
        }
      }
    }
  }

  private void unloadPlugins(List<String> pluginIds) {
    for (String pluginId : pluginIds) {
      if (pluginManager.getPlugin(pluginId) != null) {
        pluginManager.unloadPlugin(pluginId);
      }
    }
  }

  private void loadDependentPlugins(DeploymentPlan plan) {
    RollbackSnapshot snapshot = plan.getRollbackSnapshot();
    if (snapshot == null) {
      return;
    }
    for (String pluginId : plan.getAffectedPluginIds()) {
      if (pluginManager.getPlugin(pluginId) == null) {
        String pluginPath = snapshot.getPluginPaths().get(pluginId);
        if (pluginPath != null) {
          pluginManager.loadPlugin(new java.io.File(pluginPath).toPath());
        }
      }
    }
  }

  private void restoreSnapshot(DeploymentPlan plan) {
    RollbackSnapshot snapshot = plan.getRollbackSnapshot();
    if (snapshot == null) {
      throw new PluginRuntimeException("Rollback snapshot is not available: " + plan.getDeploymentId());
    }
    Map<String, String> pluginPaths = snapshot.getPluginPaths();
    for (String pluginId : plan.getStartOrder()) {
      if (pluginManager.getPlugin(pluginId) == null) {
        String pluginPath = pluginPaths.get(pluginId);
        if (pluginPath == null) {
          throw new PluginRuntimeException("Rollback plugin path is not available: " + pluginId);
        }
        pluginManager.loadPlugin(new java.io.File(pluginPath).toPath());
      }
    }
  }

  private void startSnapshotPlugins(DeploymentPlan plan) {
    RollbackSnapshot snapshot = plan.getRollbackSnapshot();
    for (String pluginId : plan.getStartOrder()) {
      if (snapshot.getStartedPluginIds().contains(pluginId)) {
        startPlugin(pluginId);
      }
    }
  }

  private void startPlugins(List<String> pluginIds) {
    for (String pluginId : pluginIds) {
      startPlugin(pluginId);
    }
  }

  private void startPlugin(String pluginId) {
    PluginState state = pluginManager.startPlugin(pluginId);
    if (!state.isStarted()) {
      throw new PluginRuntimeException("Plugin '%s' failed to start, state=%s", pluginId, state);
    }
  }

  private void verifyHealth(DeploymentPlan plan) {
    for (String pluginId : plan.getStartOrder()) {
      PluginWrapper plugin = pluginManager.getPlugin(pluginId);
      if (plugin == null) {
        throw new PluginRuntimeException("Plugin health check failed, plugin is not loaded: %s", pluginId);
      }
      if (!plugin.getPluginState().isStarted()) {
        throw new PluginRuntimeException(
            "Plugin health check failed, plugin '%s' state=%s",
            pluginId,
            plugin.getPluginState());
      }
      if (pluginManager.getPluginErrors(pluginId) != null) {
        throw new PluginRuntimeException(
            "Plugin health check failed, plugin '%s' has error: %s",
            pluginId,
            pluginManager.getPluginErrors(pluginId).getErrorMessage());
      }
      PluginHealthContext context = new PluginHealthContext(
          plan.getDeploymentId(),
          pluginId,
          plugin.getPluginState());
      runModuleHealthVerifiers(plugin, context);
      runPluginHealthProbes(plugin, context);
    }
  }

  private void runModuleHealthVerifiers(PluginWrapper pluginWrapper, PluginHealthContext context) {
    for (PluginHealthVerifier verifier : healthVerifiers) {
      List<DeploymentCheckResult> results =
          verifier.verifyStartedPlugin(context, pluginWrapper.getPluginClassLoader());
      if (results == null) {
        continue;
      }
      for (DeploymentCheckResult result : results) {
        if (result == null || result.isError()) {
          throw new PluginRuntimeException(
              "Plugin health check failed by verifier '%s' for '%s': %s",
              verifier.getClass().getName(),
              pluginWrapper.getPluginId(),
              result == null ? "null result" : result.getMessage());
        }
      }
    }
  }

  private void runPluginHealthProbes(PluginWrapper pluginWrapper, PluginHealthContext context) {
    if (!(pluginWrapper.getPlugin() instanceof Pf4bootPlugin)) {
      return;
    }
    Pf4bootPlugin plugin = (Pf4bootPlugin) pluginWrapper.getPlugin();
    if (plugin.getPluginContext() == null || !plugin.getPluginContext().isActive()) {
      return;
    }
    Map<String, PluginHealthProbe> probes =
        plugin.getPluginContext().getBeansOfType(PluginHealthProbe.class);
    for (Map.Entry<String, PluginHealthProbe> entry : probes.entrySet()) {
      DeploymentCheckResult result = entry.getValue().check(context);
      if (result == null || result.isError()) {
        throw new PluginRuntimeException(
            "Plugin health check failed by probe '%s' for '%s': %s",
            entry.getKey(),
            pluginWrapper.getPluginId(),
            result == null ? "null result" : result.getMessage());
      }
    }
  }

  private PluginDescriptor parseDescriptor(Path stagedPluginPath, List<DeploymentCheckResult> checks) {
    if (!Files.exists(stagedPluginPath)) {
      checks.add(DeploymentCheckResult.error(
          "STAGED_NOT_FOUND", "Staged plugin package does not exist: " + stagedPluginPath));
      return null;
    }
    try {
      PluginDescriptor descriptor = pluginManager.getPluginDescriptorFinder().find(stagedPluginPath);
      if (descriptor == null) {
        checks.add(DeploymentCheckResult.error(
            "DESCRIPTOR_NOT_FOUND", "Plugin descriptor not found: " + stagedPluginPath));
        return null;
      }
      checks.add(DeploymentCheckResult.info(
          "DESCRIPTOR_PARSED", "Parsed staged plugin descriptor: " + descriptor.getPluginId()));
      return descriptor;
    } catch (RuntimeException e) {
      checks.add(DeploymentCheckResult.error(
          "DESCRIPTOR_PARSE_FAILED", "Parse staged plugin descriptor failed: " + e.getMessage()));
      return null;
    }
  }

  private void checkTargetPlugin(
      String targetPluginId,
      PluginWrapper currentPlugin,
      PluginDescriptor stagedDescriptor,
      List<DeploymentCheckResult> checks) {
    if (currentPlugin == null) {
      checks.add(DeploymentCheckResult.error(
          "TARGET_NOT_LOADED", "Target plugin is not loaded: " + targetPluginId));
    } else {
      checks.add(DeploymentCheckResult.info(
          "TARGET_FOUND", "Current plugin version is " + currentPlugin.getDescriptor().getVersion()));
    }
    if (stagedDescriptor == null) {
      return;
    }
    if (!targetPluginId.equals(stagedDescriptor.getPluginId())) {
      checks.add(DeploymentCheckResult.error(
          "PLUGIN_ID_MISMATCH",
          "Staged plugin id '" + stagedDescriptor.getPluginId()
              + "' does not match target '" + targetPluginId + "'"));
      return;
    }
    checks.add(DeploymentCheckResult.info("PLUGIN_ID_MATCHED", "Staged plugin id matches target"));
  }

  private void checkPackage(
      Path stagedPluginPath,
      PluginDescriptor stagedDescriptor,
      List<DeploymentCheckResult> checks) {
    if (stagedDescriptor == null) {
      return;
    }
    for (PluginPackageVerifier verifier : pluginPackageVerifiers) {
      PluginPackageVerificationResult result = verifier.verify(stagedPluginPath, stagedDescriptor);
      if (result == null) {
        checks.add(DeploymentCheckResult.error(
            "PACKAGE_VERIFIER_INVALID",
            "Plugin package verifier returned null: " + verifier.getClass().getName()));
        continue;
      }
      if (!result.isValid()) {
        checks.add(DeploymentCheckResult.error("PACKAGE_VERIFICATION_FAILED", result.getMessage()));
      } else if (result.isWarning()) {
        checks.add(DeploymentCheckResult.warn("PACKAGE_VERIFICATION_WARN", result.getMessage()));
      } else {
        checks.add(DeploymentCheckResult.info("PACKAGE_VERIFICATION_PASSED", result.getMessage()));
      }
    }
  }

  private void checkSystemCompatibility(
      PluginDescriptor stagedDescriptor,
      List<DeploymentCheckResult> checks) {
    if (stagedDescriptor == null) {
      return;
    }
    String requires = stagedDescriptor.getRequires();
    if (Strings.isNullOrEmpty(requires)) {
      checks.add(DeploymentCheckResult.info(
          "SYSTEM_REQUIRES_EMPTY", "Staged plugin does not declare system version constraint"));
      return;
    }
    PluginPackageVerificationMode mode = properties.getPluginCompatibilityVerificationMode();
    if (mode == null || PluginPackageVerificationMode.DISABLED.equals(mode)) {
      checks.add(DeploymentCheckResult.info(
          "SYSTEM_COMPATIBILITY_SKIPPED",
          "System version compatibility check is disabled; requires=" + requires));
      return;
    }
    boolean compatible = pluginManager.getVersionManager()
        .checkVersionConstraint(pluginManager.getSystemVersion(), requires);
    if (compatible) {
      checks.add(DeploymentCheckResult.info(
          "SYSTEM_COMPATIBILITY_PASSED", "System version satisfies requires=" + requires));
    } else if (PluginPackageVerificationMode.ENFORCE.equals(mode)) {
      checks.add(DeploymentCheckResult.error(
          "SYSTEM_COMPATIBILITY_FAILED",
          "System version " + pluginManager.getSystemVersion() + " does not satisfy " + requires));
    } else {
      checks.add(DeploymentCheckResult.warn(
          "SYSTEM_COMPATIBILITY_WARN",
          "System version " + pluginManager.getSystemVersion() + " does not satisfy " + requires));
    }
  }

  private void checkDependencies(PluginDescriptor stagedDescriptor, List<DeploymentCheckResult> checks) {
    if (stagedDescriptor == null || stagedDescriptor.getDependencies() == null) {
      return;
    }
    VersionManager versionManager = pluginManager.getVersionManager();
    for (PluginDependency dependency : stagedDescriptor.getDependencies()) {
      PluginWrapper dependencyPlugin = pluginManager.getPlugin(dependency.getPluginId());
      if (dependencyPlugin == null) {
        if (dependency.isOptional()) {
          checks.add(DeploymentCheckResult.info(
              "OPTIONAL_DEPENDENCY_MISSING",
              "Optional dependency is not loaded: " + dependency.getPluginId()));
        } else {
          checks.add(DeploymentCheckResult.error(
              "REQUIRED_DEPENDENCY_MISSING",
              "Required dependency is not loaded: " + dependency.getPluginId()));
        }
        continue;
      }
      String versionSupport = dependency.getPluginVersionSupport();
      if (!Strings.isNullOrEmpty(versionSupport)
          && !versionManager.checkVersionConstraint(
          dependencyPlugin.getDescriptor().getVersion(), versionSupport)) {
        DeploymentCheckResult result = dependency.isOptional()
            ? DeploymentCheckResult.warn(
            "OPTIONAL_DEPENDENCY_VERSION_UNMATCHED",
            "Optional dependency " + dependency.getPluginId()
                + " version " + dependencyPlugin.getDescriptor().getVersion()
                + " does not satisfy " + versionSupport)
            : DeploymentCheckResult.error(
            "REQUIRED_DEPENDENCY_VERSION_UNMATCHED",
            "Required dependency " + dependency.getPluginId()
                + " version " + dependencyPlugin.getDescriptor().getVersion()
                + " does not satisfy " + versionSupport);
        checks.add(result);
      } else {
        checks.add(DeploymentCheckResult.info(
            "DEPENDENCY_COMPATIBLE", "Dependency is available: " + dependency.getPluginId()));
      }
    }
  }

  private void checkVersionProgress(
      PluginWrapper currentPlugin,
      PluginDescriptor stagedDescriptor,
      List<DeploymentCheckResult> checks) {
    if (currentPlugin == null || stagedDescriptor == null) {
      return;
    }
    int compare = pluginManager.getVersionManager()
        .compareVersions(currentPlugin.getDescriptor().getVersion(), stagedDescriptor.getVersion());
    if (compare < 0) {
      checks.add(DeploymentCheckResult.info(
          "VERSION_UPGRADE",
          "Staged version " + stagedDescriptor.getVersion()
              + " is newer than " + currentPlugin.getDescriptor().getVersion()));
    } else if (compare == 0) {
      checks.add(DeploymentCheckResult.warn(
          "VERSION_SAME",
          "Staged version is same as current version: " + stagedDescriptor.getVersion()));
    } else {
      checks.add(DeploymentCheckResult.warn(
          "VERSION_DOWNGRADE",
          "Staged version " + stagedDescriptor.getVersion()
              + " is older than " + currentPlugin.getDescriptor().getVersion()));
    }
  }

  private List<String> stopOrder(String targetPluginId) {
    List<String> order = new ArrayList<>();
    appendStopOrder(targetPluginId, order, new LinkedHashSet<String>());
    return order;
  }

  private void appendStopOrder(String pluginId, List<String> order, Set<String> visiting) {
    if (!visiting.add(pluginId)) {
      throw new PluginRuntimeException("Circular plugin dependency detected when planning deployment: " + pluginId);
    }
    for (PluginWrapper candidate : pluginManager.getPlugins()) {
      if (dependsOn(candidate, pluginId)) {
        appendStopOrder(candidate.getPluginId(), order, visiting);
      }
    }
    if (!order.contains(pluginId)) {
      order.add(pluginId);
    }
    visiting.remove(pluginId);
  }

  private boolean dependsOn(PluginWrapper candidate, String dependencyPluginId) {
    List<PluginDependency> dependencies = candidate.getDescriptor().getDependencies();
    if (dependencies == null) {
      return false;
    }
    for (PluginDependency dependency : dependencies) {
      if (dependencyPluginId.equals(dependency.getPluginId())) {
        return true;
      }
    }
    return false;
  }

  private List<String> reverse(List<String> source) {
    List<String> reversed = new ArrayList<>(source);
    Collections.reverse(reversed);
    return reversed;
  }

  private List<String> affectedPluginIds(String targetPluginId, List<String> startOrder) {
    List<String> affected = new ArrayList<>(startOrder);
    affected.remove(targetPluginId);
    return affected;
  }

  private RollbackSnapshot rollbackSnapshot(PluginWrapper currentPlugin, List<String> stopOrder) {
    if (currentPlugin == null) {
      return null;
    }
    List<String> startedPluginIds = new ArrayList<>();
    Map<String, String> pluginPaths = new LinkedHashMap<>();
    for (String pluginId : stopOrder) {
      PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
      if (wrapper != null) {
        pluginPaths.put(pluginId, wrapper.getPluginPath().toAbsolutePath().normalize().toString());
      }
      if (wrapper != null && wrapper.getPluginState().isStarted()) {
        startedPluginIds.add(pluginId);
      }
    }
    return new RollbackSnapshot(
        currentPlugin.getPluginId(),
        currentPlugin.getPluginPath().toAbsolutePath().normalize().toString(),
        currentPlugin.getDescriptor().getVersion(),
        currentPlugin.getPluginState(),
        startedPluginIds,
        pluginPaths);
  }
}
