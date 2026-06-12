package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.deployment.DeploymentPlan;
import net.xdob.pf4boot.deployment.DeploymentRecord;
import net.xdob.pf4boot.deployment.DeploymentState;
import net.xdob.pf4boot.deployment.PluginDeploymentService;
import net.xdob.pf4boot.deployment.RollbackSnapshot;
import net.xdob.pf4boot.modal.PluginRuntimeSnapshot;
import net.xdob.pf4boot.management.PluginAdminResponse;
import net.xdob.pf4boot.management.PluginDeploymentRequest;
import net.xdob.pf4boot.management.PluginManagementAuditEvent;
import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementPrincipal;
import net.xdob.pf4boot.management.PluginManagementRequest;
import net.xdob.pf4boot.management.PluginOperationRecord;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("${" + Pf4bootManagementProperties.PREFIX + ".base-path:/pf4boot/admin}")
public class PluginManagementController {

  private static final String CODE_OK = "OK";

  private final Pf4bootPluginManager pluginManager;
  private final PluginDeploymentService deploymentService;
  private final Pf4bootManagementProperties properties;
  private final PluginManagementAuthorizer authorizer;
  private final PluginManagementRequestFactory requestFactory;
  private final PluginManagementPathValidator pathValidator;
  private final PluginManagementIdempotencyService idempotencyService;
  private final PluginDeploymentRecordStore deploymentRecordStore;
  private final PluginManagementAuditRecorder auditRecorder;
  private final PluginOperationStore operationStore;

  public PluginManagementController(
      Pf4bootPluginManager pluginManager,
      PluginDeploymentService deploymentService,
      Pf4bootManagementProperties properties,
      PluginManagementAuthorizer authorizer,
      PluginManagementRequestFactory requestFactory,
      PluginManagementPathValidator pathValidator,
      PluginManagementIdempotencyService idempotencyService,
      PluginDeploymentRecordStore deploymentRecordStore,
      PluginManagementAuditRecorder auditRecorder,
      PluginOperationStore operationStore) {
    this.pluginManager = pluginManager;
    this.deploymentService = deploymentService;
    this.properties = properties;
    this.authorizer = authorizer;
    this.requestFactory = requestFactory;
    this.pathValidator = pathValidator;
    this.idempotencyService = idempotencyService;
    this.deploymentRecordStore = deploymentRecordStore;
    this.auditRecorder = auditRecorder;
    this.operationStore = operationStore;
  }

  @GetMapping("/plugins")
  public PluginAdminResponse<List<PluginRuntimeSnapshot>> plugins(HttpServletRequest request) {
    PluginManagementRequest mgmtRequest = requestFactory.toPluginRequest(
        request,
        PluginManagementOperation.PLUGIN_READ,
        null,
        null,
        properties);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.PLUGIN_READ);
    List<PluginRuntimeSnapshot> snapshots = pluginManager.getPlugins().stream()
        .map(this::toSnapshot)
        .sorted(Comparator.comparing(PluginRuntimeSnapshot::getPluginId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
        .collect(java.util.stream.Collectors.toList());
    PluginManagementAuditEvent event = buildEvent(mgmtRequest, principal, PluginManagementOperation.PLUGIN_READ,
        CODE_OK, "plugins listed");
    auditRecorder.record(event);
    return PluginAdminResponse.ok(mgmtRequest.getRequestId(), requestFactory.buildOperationId(),
        "plugin list fetched", snapshots);
  }

  @GetMapping("/plugins/{pluginId}")
  public PluginAdminResponse<PluginRuntimeSnapshot> plugin(
      @PathVariable String pluginId,
      HttpServletRequest request) {
    PluginManagementRequest mgmtRequest = requestFactory.toPluginRequest(
        request,
        PluginManagementOperation.PLUGIN_READ,
        pluginId,
        null,
        properties);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.PLUGIN_READ);
    PluginRuntimeSnapshot snapshot = pluginSnapshot(pluginId);
    PluginManagementAuditEvent event = buildEvent(mgmtRequest, principal, PluginManagementOperation.PLUGIN_READ,
        CODE_OK, "plugin details fetched");
    auditRecorder.record(event);
    return PluginAdminResponse.ok(
        mgmtRequest.getRequestId(),
        requestFactory.buildOperationId(),
        "plugin found",
        snapshot);
  }

  @PostMapping("/plugins/{pluginId}/start")
  public PluginAdminResponse<PluginRuntimeSnapshot> start(
      @PathVariable String pluginId,
      HttpServletRequest request) {
    return mutatePlugin(request,
        pluginId,
        PluginManagementOperation.PLUGIN_START,
        "plugin started",
        () -> pluginManager.startPlugin(pluginId));
  }

  @PostMapping("/plugins/{pluginId}/stop")
  public PluginAdminResponse<PluginRuntimeSnapshot> stop(
      @PathVariable String pluginId,
      HttpServletRequest request) {
    return mutatePlugin(request,
        pluginId,
        PluginManagementOperation.PLUGIN_STOP,
        "plugin stopped",
        () -> pluginManager.stopPlugin(pluginId));
  }

  @PostMapping("/plugins/{pluginId}/restart")
  public PluginAdminResponse<PluginRuntimeSnapshot> restart(
      @PathVariable String pluginId,
      HttpServletRequest request) {
    return mutatePlugin(request,
        pluginId,
        PluginManagementOperation.PLUGIN_RESTART,
        "plugin restarted",
        () -> pluginManager.restartPlugin(pluginId));
  }

  @PostMapping("/plugins/{pluginId}/reload")
  public PluginAdminResponse<PluginRuntimeSnapshot> reload(
      @PathVariable String pluginId,
      HttpServletRequest request) {
    return mutatePlugin(request,
        pluginId,
        PluginManagementOperation.PLUGIN_RELOAD,
        "plugin reloaded",
        () -> pluginManager.reloadPlugin(pluginId));
  }

  @PostMapping("/plugins/{pluginId}/enable")
  public PluginAdminResponse<PluginRuntimeSnapshot> enable(
      @PathVariable String pluginId,
      HttpServletRequest request) {
    return mutatePlugin(request,
        pluginId,
        PluginManagementOperation.PLUGIN_ENABLE,
        "plugin enabled",
        () -> {
          pluginManager.enablePlugin(pluginId);
          return pluginManager.getPlugin(pluginId);
        });
  }

  @DeleteMapping("/plugins/{pluginId}/enable")
  public PluginAdminResponse<PluginRuntimeSnapshot> disable(
      @PathVariable String pluginId,
      HttpServletRequest request) {
    return mutatePlugin(request,
        pluginId,
        PluginManagementOperation.PLUGIN_DISABLE,
        "plugin disabled",
        () -> {
          pluginManager.disablePlugin(pluginId);
          return pluginManager.getPlugin(pluginId);
        });
  }

  @GetMapping("/deployments")
  public PluginAdminResponse<List<DeploymentRecord>> deployments(HttpServletRequest request) {
    PluginManagementRequest mgmtRequest = requestFactory.toPluginRequest(
        request,
        PluginManagementOperation.DEPLOYMENT_QUERY,
        null,
        null,
        properties);
    PluginManagementPrincipal principal = authenticateAndAuthorize(
        mgmtRequest,
        PluginManagementOperation.DEPLOYMENT_QUERY);
    List<DeploymentRecord> records = deploymentRecordStore.recent(properties.getMaxRecentOperations());
    PluginManagementAuditEvent event = buildEvent(
        mgmtRequest,
        principal,
        PluginManagementOperation.DEPLOYMENT_QUERY,
        CODE_OK,
        "deployment history fetched");
    auditRecorder.record(event);
    return PluginAdminResponse.ok(
        mgmtRequest.getRequestId(),
        requestFactory.buildOperationId(),
        "deployment records fetched",
        records);
  }

  @GetMapping("/deployments/{deploymentId}")
  public PluginAdminResponse<DeploymentRecord> deployment(
      @PathVariable String deploymentId,
      HttpServletRequest request) {
    PluginManagementRequest mgmtRequest = requestFactory.toPluginRequest(
        request,
        PluginManagementOperation.DEPLOYMENT_QUERY,
        null,
        deploymentId,
        properties);
    PluginManagementPrincipal principal = authenticateAndAuthorize(
        mgmtRequest,
        PluginManagementOperation.DEPLOYMENT_QUERY);
    DeploymentRecord record = deploymentRecordStore.findById(deploymentId);
    if (record == null) {
      throw new PluginManagementException(
          PluginManagementErrorCode.NOT_FOUND,
          "Deployment record not found: " + deploymentId,
          404);
    }
    PluginManagementAuditEvent event = buildEvent(
        mgmtRequest,
        principal,
        PluginManagementOperation.DEPLOYMENT_QUERY,
        CODE_OK,
        "deployment record found");
    auditRecorder.record(event);
    return PluginAdminResponse.ok(
        mgmtRequest.getRequestId(),
        requestFactory.buildOperationId(),
        "deployment record found",
        record);
  }

  @PostMapping("/deployments/plan")
  public PluginAdminResponse<DeploymentRecord> plan(
      @RequestBody PluginDeploymentRequest body,
      HttpServletRequest request) {
    return deploymentOperation(request, body, PluginManagementOperation.DEPLOYMENT_PLAN);
  }

  @PostMapping("/deployments/replace")
  public PluginAdminResponse<DeploymentRecord> replace(
      @RequestBody PluginDeploymentRequest body,
      HttpServletRequest request) {
    return deploymentOperation(request, body, PluginManagementOperation.DEPLOYMENT_REPLACE);
  }

  @PostMapping("/deployments/{deploymentId}/rollback")
  public PluginAdminResponse<DeploymentRecord> rollback(
      @PathVariable("deploymentId") String deploymentId,
      HttpServletRequest request) {
    PluginManagementRequest mgmtRequest = requestFactory.toPluginRequest(
        request,
        PluginManagementOperation.DEPLOYMENT_ROLLBACK,
        null,
        deploymentId,
        properties);
    PluginManagementPrincipal principal = authenticateAndAuthorize(
        mgmtRequest,
        PluginManagementOperation.DEPLOYMENT_ROLLBACK);

    String principalId = safePrincipalId(principal, mgmtRequest);
    String operationId = requestFactory.buildOperationId();
    String requestHash = requestHash(mgmtRequest, "deployment-rollback", deploymentId, "");
    PluginOperationRecord recordReplay = idempotencyService.begin(
        mgmtRequest,
        PluginManagementOperation.DEPLOYMENT_ROLLBACK,
        principalId,
        requestHash,
        operationId,
        deploymentId);
    if (recordReplay != null) {
      return replayResponse(mgmtRequest, recordReplay, "deployment rollback replayed");
    }

    try {
      DeploymentRecord source = deploymentRecordStore.findById(deploymentId);
      if (source == null) {
        throw new PluginManagementException(
            PluginManagementErrorCode.NOT_FOUND,
            "Deployment record not found: " + deploymentId,
            404);
      }
      DeploymentRecord result = rollbackByPlan(source.getPlan());
      deploymentRecordStore.save(result);

      PluginOperationRecord operationRecord = operationStore.findById(operationId);
      idempotencyService.markFinished(operationRecord, result.getState() == DeploymentState.SUCCEEDED,
          result.getState() == DeploymentState.SUCCEEDED ? CODE_OK : PluginManagementErrorCode.OPERATION_FAILED.getCode(),
          messageForDeployment(result),
          summary(result));
      PluginManagementAuditEvent event = buildEvent(
          mgmtRequest,
          principal,
          PluginManagementOperation.DEPLOYMENT_ROLLBACK,
          result.getState() == DeploymentState.SUCCEEDED ? CODE_OK : PluginManagementErrorCode.OPERATION_FAILED.getCode(),
          messageForDeployment(result));
      auditRecorder.record(event);
      if (result.getState() != DeploymentState.SUCCEEDED) {
        return PluginAdminResponse.failed(
            mgmtRequest.getRequestId(),
            operationId,
            PluginManagementErrorCode.OPERATION_FAILED,
            messageForDeployment(result),
            Arrays.asList(messageForDeployment(result)));
      }
      return PluginAdminResponse.ok(
          mgmtRequest.getRequestId(),
          operationId,
          messageForDeployment(result),
          result);
    } catch (RuntimeException e) {
      PluginOperationRecord operationRecord = operationStore.findById(operationId);
      idempotencyService.markFinished(operationRecord, false, PluginManagementErrorCode.OPERATION_FAILED.getCode(),
          safeMessage(e),
          null);
      throw e;
    }
  }

  private PluginAdminResponse<DeploymentRecord> deploymentOperation(
      HttpServletRequest request,
      PluginDeploymentRequest body,
      PluginManagementOperation operation) {
    PluginManagementRequest mgmtRequest = requestFactory.toPluginRequest(
        request,
        operation,
        null,
        null,
        properties);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, operation);
    String pluginId = body == null ? null : body.getPluginId();
    String stagedPluginPath = body == null ? null : body.getStagedPluginPath();
    if (pluginId == null || pluginId.trim().isEmpty()) {
      throw new PluginManagementException(
          PluginManagementErrorCode.INVALID_REQUEST,
          "pluginId is required",
          400);
    }
    if (stagedPluginPath == null || stagedPluginPath.trim().isEmpty()) {
      throw new PluginManagementException(
          PluginManagementErrorCode.INVALID_REQUEST,
          "stagedPluginPath is required",
          400);
    }

    boolean dryRun = operation == PluginManagementOperation.DEPLOYMENT_PLAN;
    if (body != null && body.getDryRun() != null) {
      dryRun = body.getDryRun();
    } else if (operation == PluginManagementOperation.DEPLOYMENT_REPLACE) {
      dryRun = properties.isDryRunDefault();
    }

    Path resolvedStagedPath = pathValidator.resolveStagedPath(properties.getStagingRoot(), stagedPluginPath);
    String principalId = safePrincipalId(principal, mgmtRequest);
    String operationId = requestFactory.buildOperationId();
    String requestHash = requestHash(
        mgmtRequest,
        pluginId,
        resolvedStagedPath.toString(),
        String.valueOf(dryRun));

    PluginManagementRequest requestForHash = requestFactory.toPluginRequest(
        request,
        operation,
        pluginId,
        null,
        properties);
    requestForHash.setIdempotencyKey(mgmtRequest.getIdempotencyKey());

    PluginOperationRecord cached = idempotencyService.begin(
        requestForHash,
        operation,
        principalId,
        requestHash,
        operationId,
        null);
      if (cached != null) {
        return replayResponse(
            requestForHash,
            cached,
            "deployment operation replayed");
      }

    try {
      DeploymentRecord record = executeDeployment(operation, pluginId, resolvedStagedPath, dryRun);
      deploymentRecordStore.save(record);
      PluginOperationRecord operationRecord = operationStore.findById(operationId);
      if (!isDeploymentSucceeded(record)) {
        PluginManagementErrorCode errorCode = mapDeploymentErrorCode(record);
        idempotencyService.markFinished(operationRecord, false, errorCode.getCode(), messageForDeployment(record),
            summary(record));
        PluginManagementAuditEvent event = buildEvent(requestForHash, principal, operation, errorCode.getCode(),
            messageForDeployment(record));
        auditRecorder.record(event);
        return PluginAdminResponse.failed(
            requestForHash.getRequestId(),
            operationId,
            errorCode,
            messageForDeployment(record),
            record == null ? Collections.<String>emptyList() : Collections.singletonList(summary(record)));
      }

      idempotencyService.markFinished(operationRecord, true, CODE_OK, messageForDeployment(record), summary(record));
      PluginManagementAuditEvent event = buildEvent(
          requestForHash,
          principal,
          operation,
          CODE_OK,
          messageForDeployment(record));
      auditRecorder.record(event);
      return PluginAdminResponse.ok(
          requestForHash.getRequestId(),
          operationId,
          messageForDeployment(record),
          record);
    } catch (RuntimeException e) {
      PluginOperationRecord operationRecord = operationStore.findById(operationId);
      idempotencyService.markFinished(
          operationRecord,
          false,
          PluginManagementErrorCode.OPERATION_FAILED.getCode(),
          safeMessage(e),
          null);
      throw e;
    }
  }

  private PluginAdminResponse<PluginRuntimeSnapshot> mutatePlugin(
      HttpServletRequest request,
      String pluginId,
      PluginManagementOperation operation,
      String successMessage,
      PluginAction action) {
    PluginManagementRequest mgmtRequest = requestFactory.toPluginRequest(
        request,
        operation,
        pluginId,
        null,
        properties);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, operation);
    String principalId = safePrincipalId(principal, mgmtRequest);
    String operationId = requestFactory.buildOperationId();
    String requestHash = requestHash(mgmtRequest, pluginId, operation.name());
    PluginOperationRecord cached = idempotencyService.begin(
        mgmtRequest,
        operation,
        principalId,
        requestHash,
        operationId,
        null);
    if (cached != null) {
      PluginRuntimeSnapshot snapshot = pluginSnapshot(cached.getPluginId());
      return replayResponse(mgmtRequest, cached, successMessage, snapshot);
    }

    try {
      action.execute();
      PluginRuntimeSnapshot snapshot = pluginSnapshot(pluginId);
      PluginOperationRecord operationRecord = operationStore.findById(operationId);
      idempotencyService.markFinished(operationRecord, true, CODE_OK, successMessage, summary(snapshot));
      PluginManagementAuditEvent event = buildEvent(mgmtRequest, principal, operation, CODE_OK, successMessage);
      auditRecorder.record(event);
      return PluginAdminResponse.ok(
          mgmtRequest.getRequestId(),
          operationId,
          successMessage,
          snapshot);
    } catch (RuntimeException e) {
      PluginOperationRecord operationRecord = operationStore.findById(operationId);
      idempotencyService.markFinished(operationRecord, false, PluginManagementErrorCode.OPERATION_FAILED.getCode(),
          safeMessage(e), null);
      throw e;
    }
  }

  private PluginManagementPrincipal authenticateAndAuthorize(
      PluginManagementRequest request,
      PluginManagementOperation operation) {
    PluginManagementPrincipal principal = authorizer.authenticate(request);
    if (principal == null) {
      throw new PluginManagementException(
          PluginManagementErrorCode.UNAUTHENTICATED,
          "No principal returned from authorizer",
          401);
    }
    authorizer.authorize(principal, operation);
    return principal;
  }

  private PluginRuntimeSnapshot pluginSnapshot(String pluginId) {
    PluginWrapper plugin = pluginManager.getPlugin(pluginId);
    if (plugin == null) {
      throw new PluginManagementException(
          PluginManagementErrorCode.NOT_FOUND,
          "Plugin not found: " + pluginId,
          404);
    }
    return toSnapshot(plugin);
  }

  private PluginRuntimeSnapshot toSnapshot(PluginWrapper pluginWrapper) {
    PluginRuntimeSnapshot snapshot = new PluginRuntimeSnapshot();
    if (pluginWrapper == null) {
      return snapshot;
    }
    PluginDescriptor descriptor = pluginWrapper.getDescriptor();
    if (descriptor != null) {
      snapshot.setPluginId(descriptor.getPluginId());
      snapshot.setVersion(descriptor.getVersion());
      if (descriptor.getDependencies() != null) {
        List<String> dependencies = new ArrayList<String>();
        for (org.pf4j.PluginDependency dep : descriptor.getDependencies()) {
          dependencies.add(dep.getPluginId());
        }
        snapshot.setDependencies(dependencies);
      }
    }
    snapshot.setState(pluginWrapper.getPluginState());
    snapshot.setPluginPath(pluginWrapper.getPluginPath() != null ? pluginWrapper.getPluginPath().toString() : null);
    return snapshot;
  }

  private DeploymentRecord executeDeployment(
      PluginManagementOperation operation,
      String pluginId,
      Path stagedPath,
      boolean dryRun) {
    if (operation == PluginManagementOperation.DEPLOYMENT_REPLACE) {
      return deploymentService.replace(pluginId, stagedPath);
    }
    if (operation == PluginManagementOperation.DEPLOYMENT_PLAN) {
      return deploymentService.planReplacement(pluginId, stagedPath);
    }
    throw new PluginManagementException(
        PluginManagementErrorCode.INVALID_REQUEST,
        "Unsupported deployment operation: " + operation,
        400);
  }

  private DeploymentRecord rollbackByPlan(DeploymentPlan plan) {
    if (plan == null || plan.getRollbackSnapshot() == null) {
      throw new PluginManagementException(
          PluginManagementErrorCode.PRECHECK_FAILED,
          "Deployment plan can not be rolled back because rollback snapshot is unavailable",
          409);
    }
    long startedAt = System.currentTimeMillis();
    RollbackSnapshot snapshot = plan.getRollbackSnapshot();
    try {
      stopPlugins(plan.getStopOrder());
      unloadPlugins(plan.getStopOrder());
      restoreSnapshot(plan, snapshot);
      startSnapshotPlugins(plan, snapshot);
      verifyHealth(plan.getStartOrder(), snapshot);
      return new DeploymentRecord(
          plan.getDeploymentId(),
          plan.getTargetPluginId(),
          DeploymentState.SUCCEEDED,
          startedAt,
          System.currentTimeMillis(),
          "rollback succeeded",
          plan,
          DeploymentRecord.history(DeploymentState.PLANNED, DeploymentState.ROLLING_BACK,
              DeploymentState.SUCCEEDED),
          System.currentTimeMillis() - startedAt,
          null);
    } catch (RuntimeException rollbackFailure) {
      return new DeploymentRecord(
          plan.getDeploymentId(),
          plan.getTargetPluginId(),
          DeploymentState.MANUAL_INTERVENTION,
          startedAt,
          System.currentTimeMillis(),
          "rollback failed: " + rollbackFailure.getMessage(),
          plan,
          DeploymentRecord.history(DeploymentState.PLANNED, DeploymentState.ROLLING_BACK,
              DeploymentState.MANUAL_INTERVENTION),
          System.currentTimeMillis() - startedAt,
          "ROLLBACK_FAILED");
    }
  }

  private void stopPlugins(List<String> pluginIds) {
    for (String pluginId : pluginIds) {
      PluginWrapper plugin = pluginManager.getPlugin(pluginId);
      if (plugin != null && plugin.getPluginState().isStarted()) {
        pluginManager.stopPlugin(pluginId);
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

  private void restoreSnapshot(DeploymentPlan plan, RollbackSnapshot snapshot) {
    for (String pluginId : plan.getStartOrder()) {
      if (pluginManager.getPlugin(pluginId) == null) {
        String pluginPath = snapshot.getPluginPaths().get(pluginId);
        if (pluginPath == null) {
          throw new PluginManagementException(
              PluginManagementErrorCode.PRECHECK_FAILED,
              "Rollback plugin path is not available: " + pluginId,
              409);
        }
        pluginManager.loadPlugin(Paths.get(pluginPath));
      }
    }
  }

  private void startSnapshotPlugins(DeploymentPlan plan, RollbackSnapshot snapshot) {
    for (String pluginId : plan.getStartOrder()) {
      if (snapshot.getStartedPluginIds().contains(pluginId)) {
        PluginState state = pluginManager.startPlugin(pluginId);
        if (!state.isStarted()) {
          throw new PluginManagementException(
              PluginManagementErrorCode.OPERATION_FAILED,
              "Rollback plugin start failed: " + pluginId,
              500);
        }
      }
    }
  }

  private void verifyHealth(List<String> pluginIds, RollbackSnapshot snapshot) {
    for (String pluginId : pluginIds) {
      PluginWrapper plugin = pluginManager.getPlugin(pluginId);
      if (plugin == null) {
        throw new PluginManagementException(
            PluginManagementErrorCode.OPERATION_FAILED,
            "Plugin not found after rollback: " + pluginId,
            500);
      }
      if (snapshot.getStartedPluginIds().contains(pluginId) && !plugin.getPluginState().isStarted()) {
        throw new PluginManagementException(
            PluginManagementErrorCode.OPERATION_FAILED,
            "Rollback plugin not started: " + pluginId,
            500);
      }
      if (pluginManager.getPluginErrors(pluginId) != null) {
        throw new PluginManagementException(
            PluginManagementErrorCode.OPERATION_FAILED,
            "Plugin error during rollback: " + pluginId,
            500);
      }
    }
  }

  private <T> PluginAdminResponse<T> replayResponse(
      PluginManagementRequest request,
      PluginOperationRecord record,
      String fallbackMessage,
      T data) {
    PluginAdminResponse<T> response = new PluginAdminResponse<>();
    response.setSuccess(record != null && record.isSuccess());
    response.setRequestId(request.getRequestId());
    response.setOperationId(record == null ? null : record.getOperationId());
    response.setCode(record == null || record.getResponseCode() == null ? CODE_OK : record.getResponseCode());
    response.setMessage(record == null || !hasText(record.getResponseMessage())
        ? fallbackMessage
        : record.getResponseMessage());
    response.setData(data);
    response.setWarnings(null);
    return response;
  }

  private <T> PluginAdminResponse<T> replayResponse(
      PluginManagementRequest request,
      PluginOperationRecord record,
      String fallbackMessage) {
    return replayResponse(request, record, fallbackMessage, (T) null);
  }

  private PluginManagementAuditEvent buildEvent(
      PluginManagementRequest request,
      PluginManagementPrincipal principal,
      PluginManagementOperation operation,
      String code,
      String message) {
    PluginManagementAuditEvent event = new PluginManagementAuditEvent();
    event.setRequestId(request.getRequestId());
    event.setOperationId(request.getIdempotencyKey());
    event.setOperation(operation);
    event.setSuccess("OK".equals(code));
    event.setCode(code);
    event.setMessage(message);
    event.setTimestamp(System.currentTimeMillis());
    if (principal != null) {
      event.setPrincipalId(principal.getPrincipalId());
      event.setPrincipalName(principal.getPrincipalName());
    }
    event.setPluginId(request.getPluginId());
    event.setDeploymentId(request.getDeploymentId());
    return event;
  }

  private String requestHash(PluginManagementRequest request, String... parts) {
    List<String> chunks = new ArrayList<String>();
    chunks.add(request.getOperation() == null ? "" : request.getOperation().name());
    chunks.add(safe(request.getPluginId()));
    chunks.add(safe(request.getDeploymentId()));
    chunks.addAll(Arrays.asList(parts));
    return hash(String.join("|", chunks));
  }

  private String hash(String source) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
          .digest(source.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte b : bytes) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (Exception e) {
      return String.valueOf(source.hashCode());
    }
  }

  private boolean isDeploymentSucceeded(DeploymentRecord record) {
    if (record == null) {
      return false;
    }
    return DeploymentState.SUCCEEDED.equals(record.getState()) || DeploymentState.PRECHECKED.equals(record.getState());
  }

  private PluginManagementErrorCode mapDeploymentErrorCode(DeploymentRecord record) {
    String errorCode = record == null ? null : record.getErrorCode();
    if ("PRECHECK_FAILED".equals(errorCode)) {
      return PluginManagementErrorCode.PRECHECK_FAILED;
    }
    return PluginManagementErrorCode.OPERATION_FAILED;
  }

  private String messageForDeployment(DeploymentRecord record) {
    return record == null ? "deployment failed" : String.valueOf(record.getMessage());
  }

  private String summary(PluginRuntimeSnapshot snapshot) {
    if (snapshot == null) {
      return "null";
    }
    return safe(snapshot.getPluginId()) + "|" + (snapshot.getState() == null ? "" : snapshot.getState().name());
  }

  private String summary(DeploymentRecord record) {
    if (record == null) {
      return "null";
    }
    return safe(record.getDeploymentId()) + "|" + record.getState();
  }

  private String safePrincipalId(PluginManagementPrincipal principal, PluginManagementRequest request) {
    if (principal == null) {
      return request.getRemoteAddress();
    }
    if (principal.getPrincipalId() == null) {
      return "anonymous";
    }
    return principal.getPrincipalId();
  }

  private String safe(String text) {
    return text == null ? "" : text;
  }

  private boolean hasText(String text) {
    return text != null && text.trim().length() > 0;
  }

  private String safeMessage(RuntimeException e) {
    return e == null || e.getMessage() == null ? "operation failed" : e.getMessage();
  }

  @FunctionalInterface
  private interface PluginAction {
    Object execute();
  }
}
