package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementRequest;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

public class PluginManagementRequestFactory {

  private static final String HEADER_REQUEST_ID = "X-Request-Id";

  public PluginManagementRequestContext requestContext(
      String operationId,
      PluginManagementOperation operation,
      String pluginId,
      String deploymentId,
      String principalId,
      HttpServletRequest request,
      Pf4bootManagementProperties properties) {
    // Build a trace context object for logging/audit before any endpoint executes.
    PluginManagementRequest mgmtRequest = requestBody(operation, deploymentId);
    mgmtRequest.setRequestId(requestId(request));
    mgmtRequest.setOperation(operation);
    mgmtRequest.setPluginId(pluginId);
    mgmtRequest.setDeploymentId(deploymentId);
    mgmtRequest.setIdempotencyKey(idempotencyKey(request, properties));
    mgmtRequest.setToken(token(request, properties));
    mgmtRequest.setPrincipalId(principalId);
    mgmtRequest.setRemoteAddress(request.getRemoteAddr());
    mgmtRequest.setOrigin(request.getHeader("Origin"));
    mgmtRequest.setPath(request.getRequestURI());
    mgmtRequest.setMethod(request.getMethod());
    return new PluginManagementRequestContext(
        mgmtRequest.getRequestId(),
        operationId,
        pluginId,
        deploymentId,
        principalId,
        request.getRemoteAddr(),
        request.getRequestURI(),
        request.getMethod(),
        mgmtRequest.getIdempotencyKey(),
        request.getHeader("Origin"),
        mgmtRequest.getToken());
  }

  public PluginManagementRequest toPluginRequest(
      HttpServletRequest request,
      PluginManagementOperation operation,
      String pluginId,
      String deploymentId,
      Pf4bootManagementProperties properties) {
    // Build a request object consistently for auth/authorize/idempotency pipeline.
    PluginManagementRequest mgmtRequest = requestBody(operation, deploymentId);
    mgmtRequest.setRequestId(requestId(request));
    mgmtRequest.setOperation(operation);
    mgmtRequest.setPluginId(pluginId);
    mgmtRequest.setDeploymentId(deploymentId);
    mgmtRequest.setIdempotencyKey(idempotencyKey(request, properties));
    mgmtRequest.setToken(token(request, properties));
    mgmtRequest.setRemoteAddress(request.getRemoteAddr());
    mgmtRequest.setOrigin(request.getHeader("Origin"));
    mgmtRequest.setPath(request.getRequestURI());
    mgmtRequest.setMethod(request.getMethod());
    return mgmtRequest;
  }

  public String buildOperationId() {
    return "op-" + UUID.randomUUID().toString();
  }

  private PluginManagementRequest requestBody(PluginManagementOperation operation, String deploymentId) {
    PluginManagementRequest request = new PluginManagementRequest();
    request.setOperation(operation);
    request.setDeploymentId(deploymentId);
    return request;
  }

  private String requestId(HttpServletRequest request) {
    String headerValue = request.getHeader(HEADER_REQUEST_ID);
    if (StringUtils.hasText(headerValue)) {
      return headerValue;
    }
    return "req-" + UUID.randomUUID().toString();
  }

  private String idempotencyKey(HttpServletRequest request, Pf4bootManagementProperties properties) {
    // Idempotency keys are optional/required by policy; transport extraction happens here.
    return request.getHeader(properties.getIdempotencyHeader());
  }

  private String token(HttpServletRequest request, Pf4bootManagementProperties properties) {
    return request.getHeader(properties.getTokenHeader());
  }
}
