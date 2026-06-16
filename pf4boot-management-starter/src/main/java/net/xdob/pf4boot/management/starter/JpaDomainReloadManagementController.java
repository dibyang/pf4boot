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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.Path;

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
    this.planService = planService;
    this.reloadService = reloadService;
    this.properties = properties;
    this.authorizer = authorizer;
    this.requestFactory = requestFactory;
    this.auditRecorder = auditRecorder;
    this.pathValidator = pathValidator == null ? new PluginManagementPathValidator() : pathValidator;
  }

  @PostMapping("/domains/{domainId}/reload/plan")
  public PluginAdminResponse<JpaDomainReloadPlan> plan(
      @PathVariable String domainId,
      @RequestBody(required = false) JpaDomainReloadRequest body,
      HttpServletRequest request) {
    PluginManagementRequest mgmtRequest = managementRequest(request, PluginManagementOperation.PLUGIN_READ, domainId);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.PLUGIN_READ);
    String operationId = requestFactory.buildOperationId();
    JpaDomainReloadRequest reloadRequest = requestBody(domainId, body);
    reloadRequest.setDryRun(true);
    JpaDomainReloadPlan plan = planService.plan(reloadRequest);
    audit(mgmtRequest, principal, operationId, PluginManagementOperation.PLUGIN_READ, CODE_OK,
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
    PluginManagementRequest mgmtRequest = managementRequest(request, PluginManagementOperation.PLUGIN_RELOAD, domainId);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.PLUGIN_RELOAD);
    String operationId = requestFactory.buildOperationId();
    JpaDomainReloadService service = reloadService.getIfAvailable();
    if (service == null) {
      audit(mgmtRequest, principal, operationId, PluginManagementOperation.PLUGIN_RELOAD,
          PluginManagementErrorCode.UNAVAILABLE.getCode(), "JPA domain reload execution is unavailable");
      return PluginAdminResponse.failed(
          mgmtRequest.getRequestId(),
          operationId,
          PluginManagementErrorCode.UNAVAILABLE,
          "JPA domain reload execution is unavailable");
    }
    JpaDomainReloadRecord record = service.reload(requestBody(domainId, body, mgmtRequest));
    audit(mgmtRequest, principal, operationId, PluginManagementOperation.PLUGIN_RELOAD, CODE_OK,
        "JPA domain reload requested");
    return PluginAdminResponse.ok(
        mgmtRequest.getRequestId(),
        operationId,
        "JPA domain reload requested",
        record);
  }

  @GetMapping("/reloads/{reloadId}")
  public PluginAdminResponse<JpaDomainReloadRecord> record(
      @PathVariable String reloadId,
      HttpServletRequest request) {
    PluginManagementRequest mgmtRequest = managementRequest(request, PluginManagementOperation.PLUGIN_READ, null);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.PLUGIN_READ);
    String operationId = requestFactory.buildOperationId();
    JpaDomainReloadService service = reloadService.getIfAvailable();
    JpaDomainReloadRecord record = service == null ? null : service.getRecord(reloadId);
    if (record == null) {
      throw new PluginManagementException(
          PluginManagementErrorCode.NOT_FOUND,
          "JPA domain reload record not found: " + reloadId,
          404);
    }
    audit(mgmtRequest, principal, operationId, PluginManagementOperation.PLUGIN_READ, CODE_OK,
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
    PluginManagementRequest mgmtRequest = managementRequest(request, PluginManagementOperation.PLUGIN_READ, domainId);
    PluginManagementPrincipal principal = authenticateAndAuthorize(mgmtRequest, PluginManagementOperation.PLUGIN_READ);
    String operationId = requestFactory.buildOperationId();
    JpaDomainReloadService service = reloadService.getIfAvailable();
    JpaDomainReloadRecord record = service == null ? null : service.getCurrent(domainId);
    audit(mgmtRequest, principal, operationId, PluginManagementOperation.PLUGIN_READ, CODE_OK,
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
}
