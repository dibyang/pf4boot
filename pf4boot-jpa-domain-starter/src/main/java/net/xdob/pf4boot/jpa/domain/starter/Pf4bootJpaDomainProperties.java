package net.xdob.pf4boot.jpa.domain.starter;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域 JPA 能力插件配置。
 *
 * <p>该配置描述一个共享事务域。领域插件通过它创建本地 DataSource、EMF 和事务管理器，
 * 再按约定名称导出到平台上下文，供依赖它的业务插件按名称绑定。</p>
 */
@ConfigurationProperties(prefix = "pf4boot.plugin.jpa.domain")
public class Pf4bootJpaDomainProperties {

  private boolean enabled = true;

  private String id;

  private List<String> entityPackages = new ArrayList<>();

  private DataSourceProperties datasource = new DataSourceProperties();

  private String ddlAuto = "none";

  private String dataSourceName;

  private String entityManagerFactoryName;

  private String transactionManagerName;

  private String descriptorName;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<String> getEntityPackages() {
    return entityPackages;
  }

  public void setEntityPackages(List<String> entityPackages) {
    this.entityPackages = (entityPackages != null ? entityPackages : new ArrayList<>());
  }

  public DataSourceProperties getDatasource() {
    return datasource;
  }

  public void setDatasource(DataSourceProperties datasource) {
    this.datasource = (datasource != null ? datasource : new DataSourceProperties());
  }

  public String getDdlAuto() {
    return ddlAuto;
  }

  public void setDdlAuto(String ddlAuto) {
    this.ddlAuto = ddlAuto;
  }

  public String getDataSourceName() {
    return dataSourceName;
  }

  public void setDataSourceName(String dataSourceName) {
    this.dataSourceName = dataSourceName;
  }

  public String getEntityManagerFactoryName() {
    return entityManagerFactoryName;
  }

  public void setEntityManagerFactoryName(String entityManagerFactoryName) {
    this.entityManagerFactoryName = entityManagerFactoryName;
  }

  public String getTransactionManagerName() {
    return transactionManagerName;
  }

  public void setTransactionManagerName(String transactionManagerName) {
    this.transactionManagerName = transactionManagerName;
  }

  public String getDescriptorName() {
    return descriptorName;
  }

  public void setDescriptorName(String descriptorName) {
    this.descriptorName = descriptorName;
  }

  public String requireDomainId() {
    if (!StringUtils.hasText(this.id)) {
      throw new IllegalStateException(
          "[PJF-004] Domain JPA provider requires 'pf4boot.plugin.jpa.domain.id'.");
    }
    return this.id.trim();
  }

  public String[] resolveEntityPackages() {
    List<String> packages = new ArrayList<>();
    for (String entityPackage : this.entityPackages) {
      if (StringUtils.hasText(entityPackage)) {
        packages.add(entityPackage.trim());
      }
    }
    if (packages.isEmpty()) {
      throw new IllegalStateException(
          "[PJF-005] Domain JPA provider '" + requireDomainId() +
              "' requires at least one 'pf4boot.plugin.jpa.domain.entity-packages' entry.");
    }
    return packages.toArray(new String[0]);
  }

  public String resolveDdlAuto() {
    return StringUtils.hasText(this.ddlAuto) ? this.ddlAuto : "none";
  }

  public String resolveDataSourceName() {
    if (StringUtils.hasText(this.dataSourceName)) {
      return this.dataSourceName;
    }
    return "domain." + requireDomainId() + ".dataSource";
  }

  public String resolveEntityManagerFactoryName() {
    if (StringUtils.hasText(this.entityManagerFactoryName)) {
      return this.entityManagerFactoryName;
    }
    return "domain." + requireDomainId() + ".entityManagerFactory";
  }

  public String resolveTransactionManagerName() {
    if (StringUtils.hasText(this.transactionManagerName)) {
      return this.transactionManagerName;
    }
    return "domain." + requireDomainId() + ".transactionManager";
  }

  public String resolveDescriptorName() {
    if (StringUtils.hasText(this.descriptorName)) {
      return this.descriptorName;
    }
    return "domain." + requireDomainId() + ".descriptor";
  }
}
