package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlan;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlanService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecord;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRequest;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadService;
import net.xdob.pf4boot.management.PluginAdminResponse;
import net.xdob.pf4boot.management.PluginManagementAuditEvent;
import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementPrincipal;
import net.xdob.pf4boot.management.PluginManagementRequest;
import net.xdob.pf4boot.management.PluginOperationRecord;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * JPA domain 刷新管理接口。
 *
 * <p>V1-Plan 阶段只暴露计划和记录查询能力；执行入口委托给
 * `JpaDomainReloadService`，服务不存在时不会产生任何运行时变更。</p>
 */
@RestController
@RequestMapping("${" + Pf4bootManagementProperties.PREFIX + ".base-path:/pf4boot/admin}/jpa")
public class JpaDomainReloadManagementController {

  private static final String CODE_OK = "OK";

  private final JpaDomainReloadPlanService planService;
  private final ObjectProvider<JpaDomainReloadService> reloadService;
  private final Pf4bootManagementProperties properties;
  private final PluginManagementAuthorizer authorizer;
  private final PluginManagementRequestFactory requestFactory;
  private final PluginManagementAuditRecorder auditRecorder;
  private final PluginManagementPathValidator pathValidator;
  private final PluginManagementIdempotencyService idempotencyService;
  private final PluginOperationStore operationStore;
  private final PluginManagementWriteSecurityPolicy writeSecurityPolicy;
  private final DefaultPluginManagementMetricsRecorder managementMetricsRecorder;

  public JpaDomainReloadManagementController(
      JpaDomainReloadPlanService planService,
      ObjectProvider<JpaDomainReloadService> reloadService,
      Pf4bootManagementProperties properties,
      PluginManagementAuthorizer authorizer,
      PluginManagementRequestFactory requestFactory,
      PluginManagementAuditRecorder auditRecorder) {
    this(planService, reloadService, properties, authorizer, requestFactory, auditRecorder,
        new PluginManagementPathValidator());
  }

  public JpaDomainReloadManagementController(
      JpaDomainReloadPlanService planService,
      ObjectProvider<JpaDomainReloadService> reloadService,
      Pf4bootManagementProperties properties,
      PluginManagementAuthorizer authorizer,
      PluginManagementRequestFactory requestFactory,
      PluginManagementAuditRecorder auditRecorder,
      PluginManagementPathValidator pathValidator) {
    this(
        planService,
        reloadService,
        properties,
        authorizer,
        requestFactory,
        auditRecorder,
        pathValidator,
        null,
        null,
        null,
        null);
  }

  public JpaDomainReloadManagementController(
      JpaDomainReloadPlanService planService,
      ObjectProvider<JpaDomainReloadService> reloadService,
      Pf4bootManagementProperties properties,
      PluginManagementAuthorizer authorizer,
      PluginManagementRequestFactory requestFactory,
      PluginManagementAuditRecorder auditRecorder,
      PluginManagementPathValidator pathValidator,
      PluginManagementIdempotencyService idempotencyService,
      PluginOperationStore operationStore,
      PluginManagementWriteSecurityPolicy writeSecurityPolicy,
      DefaultPluginManagementMetricsRecorder managementMetricsRecorder) {
    this.planService = planService;
    this.reloadService = reloadService;
    this.properties = properties;
    this.authorizer = authorizer;
    this.requestFactory = requestFactory;
    this.auditRecorder = auditRecorder;
    this.pathValidator = pathValidator == null ? new PluginManagementPathValidator() : pathValidator;
    PluginOperationStore resolvedStore = operationStore == null
        ? new InMemoryPluginOperationStore()
        : operationStore;
    Pf4bootManagementProperties resolvedProperties = properties == null
        ? new Pf4bootManagementProperties()
        : properties;
    this.idempotencyService = idempotencyService == null
        ? new PluginManagementIdempotencyService(resolvedProperties, resolvedStore)
        : idempotencyService;
    this.operationStore = resolvedStore;
    this.writeSecurityPolicy = writeSecurityPolicy == null
        ? new PluginManagementWriteSecurityPolicy(
            resolvedProperties,
            new PluginManagementRateLimiter(resolvedProperties))
        : writeSecurityPolicy;
    this.managementMetricsRecorder = managementMetricsRecorder == null
        ? new DefaultPluginManagementMetricsRecorder()
        : managementMetricsRecorder;
  }

  @PostMapping("/domains/{domainId}/reload/plan")
  public PluginAdminResponse<JpaDomainReloadPlan> plan(
      @PathVariable String domainId,
      @RequestBody(required = false) JpaDomainReloadRequest body,
      HttpServletRequest request) {
    recordManagementRequest();
    PluginManagementRequest mgmtRequest = managementRequest(request, PluginManagementOperation.JPA_RELOAD_PLAN, domainId);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.JPA_RELOAD_PLAN);
    String operationId = requestFactory.buildOperationId();
    JpaDomainReloadRequest reloadRequest = requestBody(domainId, body);
    reloadRequest.setDryRun(true);
    JpaDomainReloadPlan plan = planService.plan(reloadRequest);
    audit(mgmtRequest, principal, operationId, PluginManagementOperation.JPA_RELOAD_PLAN, CODE_OK,
        "JPA domain reload plan generated");
    return PluginAdminResponse.ok(
        mgmtRequest.getRequestId(),
        operationId,
        "JPA domain reload plan generated",
        plan,
        plan.getWarnings());
  }

  @PostMapping("/domains/{domainId}/reload")
  public PluginAdminResponse<JpaDomainReloadRecord> reload(
      @PathVariable String domainId,
      @RequestBody(required = false) JpaDomainReloadRequest body,
      HttpServletRequest request) {
    recordManagementRequest();
    PluginManagementRequest mgmtRequest = managementRequest(request, PluginManagementOperation.JPA_RELOAD_EXECUTE, domainId);
    validateWriteRequest(request, mgmtRequest, PluginManagementOperation.JPA_RELOAD_EXECUTE);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.JPA_RELOAD_EXECUTE);
    String operationId = requestFactory.buildOperationId();
    String principalId = safePrincipalId(principal, mgmtRequest);
    JpaDomainReloadRequest reloadRequest = requestBody(domainId, body, mgmtRequest);
    String requestHash = requestHash(
        mgmtRequest,
        domainId,
        safe(reloadRequest.getMode() == null ? null : reloadRequest.getMode().name()),
        safe(reloadRequest.getProviderReplacementPath()),
        String.valueOf(reloadRequest.getDrainTimeoutMillis()),
        String.valueOf(reloadRequest.getHealthCheckTimeoutMillis()));
    PluginOperationRecord cached = beginIdempotency(
        mgmtRequest,
        PluginManagementOperation.JPA_RELOAD_EXECUTE,
        principal,
        principalId,
        requestHash,
        operationId);
    if (cached != null) {
      JpaDomainReloadRecord replayed = replayedRecord(cached);
      return replayResponse(mgmtRequest, cached, "JPA domain reload replayed", replayed);
    }
    JpaDomainReloadService service = reloadService.getIfAvailable();
    if (service == null) {
      markFinished(operationId, false, PluginManagementErrorCode.UNAVAILABLE.getCode(),
          "JPA domain reload execution is unavailable", null);
      audit(mgmtRequest, principal, operationId, PluginManagementOperation.JPA_RELOAD_EXECUTE,
          PluginManagementErrorCode.UNAVAILABLE.getCode(), "JPA domain reload execution is unavailable");
      return PluginAdminResponse.failed(
          mgmtRequest.getRequestId(),
          operationId,
          PluginManagementErrorCode.UNAVAILABLE,
          "JPA domain reload execution is unavailable");
    }
    try {
      JpaDomainReloadRecord record = service.reload(reloadRequest);
      PluginOperationRecord operationRecord = operationStore.findById(operationId);
      if (operationRecord != null && record != null) {
        operationRecord.setDeploymentId(record.getReloadId());
      }
      markFinished(operationId, true, CODE_OK, "JPA domain reload requested", summary(record));
      audit(mgmtRequest, principal, operationId, PluginManagementOperation.JPA_RELOAD_EXECUTE, CODE_OK,
          "JPA domain reload requested");
      return PluginAdminResponse.ok(
          mgmtRequest.getRequestId(),
          operationId,
          "JPA domain reload requested",
          record);
    } catch (RuntimeException e) {
      markFinished(operationId, false, PluginManagementErrorCode.OPERATION_FAILED.getCode(),
          safeMessage(e), null);
      recordFailureAudit(mgmtRequest, principal, operationId, PluginManagementOperation.JPA_RELOAD_EXECUTE, e);
      throw e;
    }
  }

  @GetMapping("/reloads/{reloadId}")
  public PluginAdminResponse<JpaDomainReloadRecord> record(
      @PathVariable String reloadId,
      HttpServletRequest request) {
    recordManagementRequest();
    PluginManagementRequest mgmtRequest = managementRequest(request, PluginManagementOperation.JPA_RELOAD_QUERY, null);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.JPA_RELOAD_QUERY);
    String operationId = requestFactory.buildOperationId();
    JpaDomainReloadService service = reloadService.getIfAvailable();
    JpaDomainReloadRecord record = service == null ? null : service.getRecord(reloadId);
    if (record == null) {
      throw new PluginManagementException(
          PluginManagementErrorCode.NOT_FOUND,
          "JPA domain reload record not found: " + reloadId,
          404);
    }
    audit(mgmtRequest, principal, operationId, PluginManagementOperation.JPA_RELOAD_QUERY, CODE_OK,
        "JPA domain reload record found");
    return PluginAdminResponse.ok(
        mgmtRequest.getRequestId(),
        operationId,
        "JPA domain reload record found",
        record);
  }

  @GetMapping("/domains/{domainId}/reload/current")
  public PluginAdminResponse<JpaDomainReloadRecord> current(
      @PathVariable String domainId,
      HttpServletRequest request) {
    recordManagementRequest();
    PluginManagementRequest mgmtRequest = managementRequest(request, PluginManagementOperation.JPA_RELOAD_QUERY, domainId);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.JPA_RELOAD_QUERY);
    String operationId = requestFactory.buildOperationId();
    JpaDomainReloadService service = reloadService.getIfAvailable();
    JpaDomainReloadRecord record = service == null ? null : service.getCurrent(domainId);
    audit(mgmtRequest, principal, operationId, PluginManagementOperation.JPA_RELOAD_QUERY, CODE_OK,
        "JPA domain current reload fetched");
    return PluginAdminResponse.ok(
        mgmtRequest.getRequestId(),
        operationId,
        "JPA domain current reload fetched",
        record);
  }

  private JpaDomainReloadRequest requestBody(String domainId, JpaDomainReloadRequest body) {
    JpaDomainReloadRequest request = body == null ? new JpaDomainReloadRequest() : body;
    request.setDomainId(domainId);
    if (request.getProviderReplacementPath() != null && !request.getProviderReplacementPath().trim().isEmpty()) {
      Path stagedPath = pathValidator.resolveStagedPath(
          properties == null ? null : properties.getStagingRoot(),
          request.getProviderReplacementPath());
      request.setProviderReplacementPath(stagedPath.toString());
    }
    return request;
  }

  private JpaDomainReloadRequest requestBody(
      String domainId,
      JpaDomainReloadRequest body,
      PluginManagementRequest managementRequest) {
    JpaDomainReloadRequest request = requestBody(domainId, body);
    if (request.getIdempotencyKey() == null && managementRequest != null) {
      request.setIdempotencyKey(managementRequest.getIdempotencyKey());
    }
    if (request.getRequestedBy() == null && managementRequest != null) {
      request.setRequestedBy(managementRequest.getPrincipalId());
    }
    return request;
  }

  private PluginManagementRequest managementRequest(
      HttpServletRequest request,
      PluginManagementOperation operation,
      String domainId) {
    return requestFactory.toPluginRequest(request, operation, domainId, null, properties);
  }

  private PluginManagementPrincipal authenticateAndAuthorize(
      PluginManagementRequest request,
      PluginManagementOperation operation) {
    try {
      PluginManagementPrincipal principal = authorizer.authenticate(request);
      if (principal == null) {
        throw new PluginManagementException(
            PluginManagementErrorCode.UNAUTHENTICATED,
            "No principal returned from authorizer",
            401);
      }
      authorizer.authorize(principal, operation);
      return principal;
    } catch (RuntimeException e) {
      recordFailureAudit(request, null, null, operation, e);
      throw e;
    }
  }

  private void validateWriteRequest(
      HttpServletRequest servletRequest,
      PluginManagementRequest request,
      PluginManagementOperation operation) {
    try {
      writeSecurityPolicy.validateWriteRequest(servletRequest, request);
    } catch (RuntimeException e) {
      recordFailureAudit(request, null, null, operation, e);
      throw e;
    }
  }

  private PluginOperationRecord beginIdempotency(
      PluginManagementRequest request,
      PluginManagementOperation operation,
      PluginManagementPrincipal principal,
      String principalId,
      String requestHash,
      String operationId) {
    try {
      PluginOperationRecord record = idempotencyService.begin(
          request,
          operation,
          principalId,
          requestHash,
          operationId,
          null);
      if (record != null) {
        managementMetricsRecorder.recordIdempotencyHit();
      }
      return record;
    } catch (RuntimeException e) {
      recordFailureAudit(request, principal, operationId, operation, e);
      throw e;
    }
  }

  private void markFinished(
      String operationId,
      boolean success,
      String code,
      String message,
      String responseBodySummary) {
    PluginOperationRecord operationRecord = operationStore.findById(operationId);
    idempotencyService.markFinished(operationRecord, success, code, message, responseBodySummary);
  }

  private JpaDomainReloadRecord replayedRecord(PluginOperationRecord cached) {
    if (cached == null || cached.getDeploymentId() == null) {
      return null;
    }
    JpaDomainReloadService service = reloadService.getIfAvailable();
    return service == null ? null : service.getRecord(cached.getDeploymentId());
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
    response.setMessage(record == null || record.getResponseMessage() == null
        ? fallbackMessage
        : record.getResponseMessage());
    response.setData(data);
    return response;
  }

  private void audit(
      PluginManagementRequest request,
      PluginManagementPrincipal principal,
      String operationId,
      PluginManagementOperation operation,
      String code,
      String message) {
    PluginManagementAuditEvent event = new PluginManagementAuditEvent();
    event.setRequestId(request.getRequestId());
    event.setOperationId(operationId);
    event.setOperation(operation);
    event.setSuccess(CODE_OK.equals(code));
    event.setCode(code);
    event.setMessage(PluginManagementResponseSanitizer.safeText(message));
    event.setTimestamp(System.currentTimeMillis());
    event.setPluginId(request.getPluginId());
    if (principal != null) {
      event.setPrincipalId(principal.getPrincipalId());
      event.setPrincipalName(principal.getPrincipalName());
    }
    auditRecorder.record(event);
  }

  private void recordFailureAudit(
      PluginManagementRequest request,
      PluginManagementPrincipal principal,
      String operationId,
      PluginManagementOperation operation,
      RuntimeException e) {
    if (request == null) {
      return;
    }
    managementMetricsRecorder.recordRejected();
    PluginManagementErrorCode code = errorCode(e);
    audit(request, principal, operationId, operation, code.getCode(),
        PluginManagementResponseSanitizer.safeMessage(code));
  }

  private PluginManagementErrorCode errorCode(RuntimeException e) {
    if (e instanceof PluginManagementException) {
      return ((PluginManagementException) e).getCode();
    }
    return PluginManagementErrorCode.OPERATION_FAILED;
  }

  private String safePrincipalId(PluginManagementPrincipal principal, PluginManagementRequest request) {
    if (principal != null && principal.getPrincipalId() != null) {
      return principal.getPrincipalId();
    }
    return request == null ? "anonymous" : request.getRemoteAddress();
  }

  private String requestHash(PluginManagementRequest request, String... parts) {
    StringBuilder source = new StringBuilder();
    source.append(request.getOperation() == null ? "" : request.getOperation().name());
    source.append('|').append(safe(request.getPluginId()));
    source.append('|').append(safe(request.getDeploymentId()));
    if (parts != null) {
      for (String part : parts) {
        source.append('|').append(safe(part));
      }
    }
    return hash(source.toString());
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
      return Integer.toHexString(source.hashCode());
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String safeMessage(RuntimeException e) {
    return PluginManagementResponseSanitizer.safeText(e == null ? null : e.getMessage());
  }

  private String summary(JpaDomainReloadRecord record) {
    if (record == null) {
      return null;
    }
    return "reloadId=" + safe(record.getReloadId())
        + ",domainId=" + safe(record.getDomainId())
        + ",state=" + (record.getState() == null ? "" : record.getState().name())
        + ",failureCode=" + (record.getFailureCode() == null ? "" : record.getFailureCode().name());
  }

  private void recordManagementRequest() {
    managementMetricsRecorder.recordRequest();
  }
}
