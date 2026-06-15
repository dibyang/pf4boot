package net.xdob.pf4boot.jpa.starter;

import net.xdob.pf4boot.jpa.reload.JpaDomainReloadMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件 JPA 启动配置。
 *
 * <p>该配置只描述插件侧 JPA starter 的启动模式。默认保持本地模式，避免影响
 * 现有插件；共享模式只解析并校验领域能力插件导出的 EMF/TM。</p>
 */
@ConfigurationProperties(prefix = "pf4boot.plugin.jpa")
public class Pf4bootJpaProperties {

  /**
   * JPA 启动模式。
   */
  public enum Mode {
    LOCAL,
    SHARED
  }

  private Mode mode = Mode.LOCAL;

  private String domainId;

  private String entityManagerFactoryRef;

  private String transactionManagerRef;

  private String descriptorRef;

  private List<DomainBinding> additionalDomains = new ArrayList<>();

  private Map<String, Binding> plugins = new LinkedHashMap<>();

  private DomainReload domainReload = new DomainReload();

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = (mode != null ? mode : Mode.LOCAL);
  }

  public String getDomainId() {
    return domainId;
  }

  public void setDomainId(String domainId) {
    this.domainId = domainId;
  }

  public String getEntityManagerFactoryRef() {
    return entityManagerFactoryRef;
  }

  public void setEntityManagerFactoryRef(String entityManagerFactoryRef) {
    this.entityManagerFactoryRef = entityManagerFactoryRef;
  }

  public String getTransactionManagerRef() {
    return transactionManagerRef;
  }

  public void setTransactionManagerRef(String transactionManagerRef) {
    this.transactionManagerRef = transactionManagerRef;
  }

  public String getDescriptorRef() {
    return descriptorRef;
  }

  public void setDescriptorRef(String descriptorRef) {
    this.descriptorRef = descriptorRef;
  }

  public List<DomainBinding> getAdditionalDomains() {
    return additionalDomains;
  }

  public void setAdditionalDomains(List<DomainBinding> additionalDomains) {
    this.additionalDomains = additionalDomains == null ? new ArrayList<>() : additionalDomains;
  }

  public Map<String, Binding> getPlugins() {
    return plugins;
  }

  public void setPlugins(Map<String, Binding> plugins) {
    this.plugins = plugins == null ? new LinkedHashMap<>() : plugins;
  }

  public DomainReload getDomainReload() {
    return domainReload;
  }

  public void setDomainReload(DomainReload domainReload) {
    this.domainReload = domainReload == null ? new DomainReload() : domainReload;
  }

  public boolean isShared() {
    return Mode.SHARED == this.mode;
  }

  public String resolveEntityManagerFactoryRef() {
    if (StringUtils.hasText(this.entityManagerFactoryRef)) {
      return this.entityManagerFactoryRef;
    }
    return "domain." + this.domainId + ".entityManagerFactory";
  }

  public String resolveTransactionManagerRef() {
    if (StringUtils.hasText(this.transactionManagerRef)) {
      return this.transactionManagerRef;
    }
    return "domain." + this.domainId + ".transactionManager";
  }

  public String resolveDescriptorRef() {
    if (StringUtils.hasText(this.descriptorRef)) {
      return this.descriptorRef;
    }
    return "domain." + this.domainId + ".descriptor";
  }

  /**
   * 单个插件的 JPA 绑定配置。
   */
  public static class Binding extends DomainBinding {
    private Mode mode;
    private List<DomainBinding> additionalDomains = new ArrayList<>();

    public Mode getMode() {
      return mode;
    }

    public void setMode(Mode mode) {
      this.mode = mode;
    }

    public List<DomainBinding> getAdditionalDomains() {
      return additionalDomains;
    }

    public void setAdditionalDomains(List<DomainBinding> additionalDomains) {
      this.additionalDomains = additionalDomains == null ? new ArrayList<>() : additionalDomains;
    }
  }

  /**
   * 单个共享 domain 的 JPA Bean 引用配置。
   */
  public static class DomainBinding {
    private String domainId;
    private String entityManagerFactoryRef;
    private String transactionManagerRef;
    private String descriptorRef;

    public String getDomainId() {
      return domainId;
    }

    public void setDomainId(String domainId) {
      this.domainId = domainId;
    }

    public String getEntityManagerFactoryRef() {
      return entityManagerFactoryRef;
    }

    public void setEntityManagerFactoryRef(String entityManagerFactoryRef) {
      this.entityManagerFactoryRef = entityManagerFactoryRef;
    }

    public String getTransactionManagerRef() {
      return transactionManagerRef;
    }

    public void setTransactionManagerRef(String transactionManagerRef) {
      this.transactionManagerRef = transactionManagerRef;
    }

    public String getDescriptorRef() {
      return descriptorRef;
    }

    public void setDescriptorRef(String descriptorRef) {
      this.descriptorRef = descriptorRef;
    }
  }

  /**
   * JPA domain 运行时刷新配置。
   */
  public static class DomainReload {
    private JpaDomainReloadMode mode = JpaDomainReloadMode.DISABLED;
    private boolean requireIdempotencyKey = true;
    private long defaultDrainTimeout = 30000L;
    private long defaultHealthCheckTimeout = 60000L;
    private boolean allowInferredConsumers;
    private int maxRecentRecords = 100;

    public JpaDomainReloadMode getMode() {
      return mode;
    }

    public void setMode(JpaDomainReloadMode mode) {
      this.mode = mode == null ? JpaDomainReloadMode.DISABLED : mode;
    }

    public boolean isRequireIdempotencyKey() {
      return requireIdempotencyKey;
    }

    public void setRequireIdempotencyKey(boolean requireIdempotencyKey) {
      this.requireIdempotencyKey = requireIdempotencyKey;
    }

    public long getDefaultDrainTimeout() {
      return defaultDrainTimeout;
    }

    public void setDefaultDrainTimeout(long defaultDrainTimeout) {
      this.defaultDrainTimeout = defaultDrainTimeout;
    }

    public long getDefaultHealthCheckTimeout() {
      return defaultHealthCheckTimeout;
    }

    public void setDefaultHealthCheckTimeout(long defaultHealthCheckTimeout) {
      this.defaultHealthCheckTimeout = defaultHealthCheckTimeout;
    }

    public boolean isAllowInferredConsumers() {
      return allowInferredConsumers;
    }

    public void setAllowInferredConsumers(boolean allowInferredConsumers) {
      this.allowInferredConsumers = allowInferredConsumers;
    }

    public int getMaxRecentRecords() {
      return maxRecentRecords;
    }

    public void setMaxRecentRecords(int maxRecentRecords) {
      this.maxRecentRecords = maxRecentRecords;
    }
  }
}
