package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ConfigurationProperties(prefix = Pf4bootManagementProperties.PREFIX)
public class Pf4bootManagementProperties {

  public static final String PREFIX = "spring.pf4boot.management.http";

  private boolean enabled = false;
  private String basePath = "/pf4boot/admin";
  private PluginManagementMode mode = PluginManagementMode.DISABLED;
  private boolean allowLoopbackOnly = true;
  private String token = "";
  private String tokenHeader = "X-PF4Boot-Admin-Token";
  private boolean requireIdempotencyKey = true;
  private String idempotencyHeader = "X-Idempotency-Key";
  private boolean dryRunDefault = true;
  private boolean auditEnabled = true;
  private String stagingRoot = "plugins/staged";
  private int maxRecentOperations = 20;
  private OperationStoreProperties operationStore = new OperationStoreProperties();
  private RateLimitProperties rateLimit = new RateLimitProperties();
  private CsrfProperties csrf = new CsrfProperties();
  private List<String> allowedOperations = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public PluginManagementMode getMode() {
    return mode;
  }

  public void setMode(PluginManagementMode mode) {
    this.mode = mode;
  }

  public boolean isAllowLoopbackOnly() {
    return allowLoopbackOnly;
  }

  public void setAllowLoopbackOnly(boolean allowLoopbackOnly) {
    this.allowLoopbackOnly = allowLoopbackOnly;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getTokenHeader() {
    return tokenHeader;
  }

  public void setTokenHeader(String tokenHeader) {
    this.tokenHeader = tokenHeader;
  }

  public boolean isRequireIdempotencyKey() {
    return requireIdempotencyKey;
  }

  public void setRequireIdempotencyKey(boolean requireIdempotencyKey) {
    this.requireIdempotencyKey = requireIdempotencyKey;
  }

  public String getIdempotencyHeader() {
    return idempotencyHeader;
  }

  public void setIdempotencyHeader(String idempotencyHeader) {
    this.idempotencyHeader = idempotencyHeader;
  }

  public boolean isDryRunDefault() {
    return dryRunDefault;
  }

  public void setDryRunDefault(boolean dryRunDefault) {
    this.dryRunDefault = dryRunDefault;
  }

  public boolean isAuditEnabled() {
    return auditEnabled;
  }

  public void setAuditEnabled(boolean auditEnabled) {
    this.auditEnabled = auditEnabled;
  }

  public String getStagingRoot() {
    return stagingRoot;
  }

  public void setStagingRoot(String stagingRoot) {
    this.stagingRoot = stagingRoot;
  }

  public RateLimitProperties getRateLimit() {
    return rateLimit;
  }

  public void setRateLimit(RateLimitProperties rateLimit) {
    this.rateLimit = rateLimit;
  }

  public CsrfProperties getCsrf() {
    return csrf;
  }

  public void setCsrf(CsrfProperties csrf) {
    this.csrf = csrf;
  }

  public int getMaxRecentOperations() {
    return maxRecentOperations;
  }

  public void setMaxRecentOperations(int maxRecentOperations) {
    this.maxRecentOperations = maxRecentOperations;
  }

  public OperationStoreProperties getOperationStore() {
    return operationStore;
  }

  public void setOperationStore(OperationStoreProperties operationStore) {
    this.operationStore = operationStore == null ? new OperationStoreProperties() : operationStore;
  }

  public List<String> getAllowedOperations() {
    return Collections.unmodifiableList(allowedOperations);
  }

  public void setAllowedOperations(List<String> allowedOperations) {
    this.allowedOperations = allowedOperations == null ? new ArrayList<String>() : allowedOperations;
  }

  public static class RateLimitProperties {
    private boolean enabled = true;
    private int writesPerMinute = 30;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getWritesPerMinute() {
      return writesPerMinute;
    }

    public void setWritesPerMinute(int writesPerMinute) {
      this.writesPerMinute = writesPerMinute;
    }
  }

  public static class OperationStoreProperties {
    private String type = "memory";
    private String directory = "work/pf4boot/operations";
    private boolean failClosed = true;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getDirectory() {
      return directory;
    }

    public void setDirectory(String directory) {
      this.directory = directory;
    }

    public boolean isFailClosed() {
      return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
      this.failClosed = failClosed;
    }
  }

  public static class CsrfProperties {
    private String enabled = "auto";

    public String getEnabled() {
      return enabled;
    }

    public void setEnabled(String enabled) {
      this.enabled = enabled;
    }
  }
}
