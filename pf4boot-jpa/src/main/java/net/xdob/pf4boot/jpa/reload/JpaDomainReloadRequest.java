package net.xdob.pf4boot.jpa.reload;

/**
 * JPA domain 刷新请求。
 */
public class JpaDomainReloadRequest {

  private String domainId;
  private JpaDomainReloadMode mode;
  private boolean dryRun;
  private String idempotencyKey;
  private String requestedBy;
  private String reason;
  private boolean allowInferredConsumers;
  private long drainTimeoutMillis;
  private long healthCheckTimeoutMillis;
  private String providerReplacementPath;

  public String getDomainId() {
    return domainId;
  }

  public void setDomainId(String domainId) {
    this.domainId = domainId;
  }

  public JpaDomainReloadMode getMode() {
    return mode;
  }

  public void setMode(JpaDomainReloadMode mode) {
    this.mode = mode;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getRequestedBy() {
    return requestedBy;
  }

  public void setRequestedBy(String requestedBy) {
    this.requestedBy = requestedBy;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public boolean isAllowInferredConsumers() {
    return allowInferredConsumers;
  }

  public void setAllowInferredConsumers(boolean allowInferredConsumers) {
    this.allowInferredConsumers = allowInferredConsumers;
  }

  public long getDrainTimeoutMillis() {
    return drainTimeoutMillis;
  }

  public void setDrainTimeoutMillis(long drainTimeoutMillis) {
    this.drainTimeoutMillis = drainTimeoutMillis;
  }

  public long getHealthCheckTimeoutMillis() {
    return healthCheckTimeoutMillis;
  }

  public void setHealthCheckTimeoutMillis(long healthCheckTimeoutMillis) {
    this.healthCheckTimeoutMillis = healthCheckTimeoutMillis;
  }

  public String getProviderReplacementPath() {
    return providerReplacementPath;
  }

  public void setProviderReplacementPath(String providerReplacementPath) {
    this.providerReplacementPath = providerReplacementPath;
  }
}
