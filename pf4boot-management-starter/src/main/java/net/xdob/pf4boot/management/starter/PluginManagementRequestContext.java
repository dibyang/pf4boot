package net.xdob.pf4boot.management.starter;

/**
 * 管理接口链路中的请求上下文.
 */
public class PluginManagementRequestContext {
  private final String requestId;
  private final String operationId;
  private final String pluginId;
  private final String deploymentId;
  private final String principalId;
  private final String remoteAddress;
  private final String path;
  private final String method;
  private final String idempotencyKey;
  private final String origin;
  private final String token;

  public PluginManagementRequestContext(
      String requestId,
      String operationId,
      String pluginId,
      String deploymentId,
      String principalId,
      String remoteAddress,
      String path,
      String method,
      String idempotencyKey,
      String origin,
      String token) {
    this.requestId = requestId;
    this.operationId = operationId;
    this.pluginId = pluginId;
    this.deploymentId = deploymentId;
    this.principalId = principalId;
    this.remoteAddress = remoteAddress;
    this.path = path;
    this.method = method;
    this.idempotencyKey = idempotencyKey;
    this.origin = origin;
    this.token = token;
  }

  public String getRequestId() {
    return requestId;
  }

  public String getOperationId() {
    return operationId;
  }

  public String getPluginId() {
    return pluginId;
  }

  public String getDeploymentId() {
    return deploymentId;
  }

  public String getPrincipalId() {
    return principalId;
  }

  public String getRemoteAddress() {
    return remoteAddress;
  }

  public String getPath() {
    return path;
  }

  public String getMethod() {
    return method;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getOrigin() {
    return origin;
  }

  public String getToken() {
    return token;
  }
}

