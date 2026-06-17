package net.xdob.pf4boot.jpa.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件侧声明的共享 JPA domain 定义。
 *
 * <p>实体包、数据源、DDL 策略和导出 Bean 名称都是 provider 插件的自治契约。框架只读取
 * 该定义并据此创建/导出平台 Bean，不再要求宿主替插件维护结构性配置。</p>
 */
public final class JpaDomainDefinition {

  private final String domainId;
  private final List<String> entityPackages;
  private final JpaDataSourceDefinition dataSource;
  private final String ddlAuto;
  private final String dataSourceName;
  private final String entityManagerFactoryName;
  private final String transactionManagerName;
  private final String descriptorName;

  private JpaDomainDefinition(Builder builder) {
    this.domainId = trimToNull(builder.domainId);
    this.entityPackages = Collections.unmodifiableList(trimList(builder.entityPackages));
    this.dataSource = builder.dataSource;
    this.ddlAuto = trimToNull(builder.ddlAuto);
    this.dataSourceName = trimToNull(builder.dataSourceName);
    this.entityManagerFactoryName = trimToNull(builder.entityManagerFactoryName);
    this.transactionManagerName = trimToNull(builder.transactionManagerName);
    this.descriptorName = trimToNull(builder.descriptorName);
  }

  public static Builder builder(String domainId) {
    return new Builder(domainId);
  }

  public String getDomainId() {
    return domainId;
  }

  public List<String> getEntityPackages() {
    return entityPackages;
  }

  public JpaDataSourceDefinition getDataSource() {
    return dataSource;
  }

  public String getDdlAuto() {
    return ddlAuto;
  }

  public String getDataSourceName() {
    return dataSourceName;
  }

  public String getEntityManagerFactoryName() {
    return entityManagerFactoryName;
  }

  public String getTransactionManagerName() {
    return transactionManagerName;
  }

  public String getDescriptorName() {
    return descriptorName;
  }

  public String requireDomainId() {
    if (!hasText(this.domainId)) {
      throw new IllegalStateException("[PJF-004] JPA domain definition requires domainId.");
    }
    return this.domainId;
  }

  public String[] resolveEntityPackages() {
    if (this.entityPackages.isEmpty()) {
      throw new IllegalStateException(
          "[PJF-005] JPA domain definition '" + requireDomainId()
              + "' requires at least one entity package.");
    }
    return this.entityPackages.toArray(new String[0]);
  }

  public JpaDataSourceDefinition requireDataSource() {
    if (this.dataSource == null || !hasText(this.dataSource.getUrl())) {
      throw new IllegalStateException(
          "[PJF-004] JPA domain definition '" + requireDomainId()
              + "' requires datasource url.");
    }
    return this.dataSource;
  }

  public String resolveDdlAuto() {
    return hasText(this.ddlAuto) ? this.ddlAuto : "none";
  }

  public String resolveDataSourceName() {
    return hasText(this.dataSourceName)
        ? this.dataSourceName
        : "domain." + requireDomainId() + ".dataSource";
  }

  public String resolveEntityManagerFactoryName() {
    return hasText(this.entityManagerFactoryName)
        ? this.entityManagerFactoryName
        : "domain." + requireDomainId() + ".entityManagerFactory";
  }

  public String resolveTransactionManagerName() {
    return hasText(this.transactionManagerName)
        ? this.transactionManagerName
        : "domain." + requireDomainId() + ".transactionManager";
  }

  public String resolveDescriptorName() {
    return hasText(this.descriptorName)
        ? this.descriptorName
        : "domain." + requireDomainId() + ".descriptor";
  }

  private static List<String> trimList(List<String> values) {
    List<String> result = new ArrayList<>();
    if (values == null) {
      return result;
    }
    for (String value : values) {
      String trimmed = trimToNull(value);
      if (trimmed != null) {
        result.add(trimmed);
      }
    }
    return result;
  }

  private static boolean hasText(String value) {
    return trimToNull(value) != null;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() == 0 ? null : trimmed;
  }

  /**
   * Domain 定义构建器。
   */
  public static final class Builder {
    private String domainId;
    private List<String> entityPackages = new ArrayList<>();
    private JpaDataSourceDefinition dataSource;
    private String ddlAuto = "none";
    private String dataSourceName;
    private String entityManagerFactoryName;
    private String transactionManagerName;
    private String descriptorName;

    private Builder(String domainId) {
      this.domainId = domainId;
    }

    public Builder entityPackage(String entityPackage) {
      this.entityPackages.add(entityPackage);
      return this;
    }

    public Builder entityPackages(List<String> entityPackages) {
      this.entityPackages = entityPackages == null ? new ArrayList<>() : new ArrayList<>(entityPackages);
      return this;
    }

    public Builder dataSource(JpaDataSourceDefinition dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public Builder ddlAuto(String ddlAuto) {
      this.ddlAuto = ddlAuto;
      return this;
    }

    public Builder dataSourceName(String dataSourceName) {
      this.dataSourceName = dataSourceName;
      return this;
    }

    public Builder entityManagerFactoryName(String entityManagerFactoryName) {
      this.entityManagerFactoryName = entityManagerFactoryName;
      return this;
    }

    public Builder transactionManagerName(String transactionManagerName) {
      this.transactionManagerName = transactionManagerName;
      return this;
    }

    public Builder descriptorName(String descriptorName) {
      this.descriptorName = descriptorName;
      return this;
    }

    public JpaDomainDefinition build() {
      return new JpaDomainDefinition(this);
    }
  }
}
