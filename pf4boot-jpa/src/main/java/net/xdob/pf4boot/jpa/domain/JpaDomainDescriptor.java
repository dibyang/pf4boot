package net.xdob.pf4boot.jpa.domain;

import java.util.Arrays;

/**
 * 共享 JPA 事务域描述信息。
 *
 * <p>领域能力插件在 DataSource、EntityManagerFactory 和 TransactionManager 全部导出
 * 成功后导出该描述对象。消费插件可通过它判断 domain 是否 ready，并输出更明确的诊断。</p>
 */
public class JpaDomainDescriptor {

  private final String domainId;
  private final String providerPluginId;
  private final String[] entityPackages;
  private final String dataSourceBeanName;
  private final String entityManagerFactoryBeanName;
  private final String transactionManagerBeanName;
  private final boolean ready;
  private final long createdAt;

  public JpaDomainDescriptor(
      String domainId,
      String providerPluginId,
      String[] entityPackages,
      String dataSourceBeanName,
      String entityManagerFactoryBeanName,
      String transactionManagerBeanName,
      boolean ready,
      long createdAt) {
    this.domainId = domainId;
    this.providerPluginId = providerPluginId;
    this.entityPackages = entityPackages == null ? new String[0] : entityPackages.clone();
    this.dataSourceBeanName = dataSourceBeanName;
    this.entityManagerFactoryBeanName = entityManagerFactoryBeanName;
    this.transactionManagerBeanName = transactionManagerBeanName;
    this.ready = ready;
    this.createdAt = createdAt;
  }

  public String getDomainId() {
    return domainId;
  }

  public String getProviderPluginId() {
    return providerPluginId;
  }

  public String[] getEntityPackages() {
    return entityPackages.clone();
  }

  public String getDataSourceBeanName() {
    return dataSourceBeanName;
  }

  public String getEntityManagerFactoryBeanName() {
    return entityManagerFactoryBeanName;
  }

  public String getTransactionManagerBeanName() {
    return transactionManagerBeanName;
  }

  public boolean isReady() {
    return ready;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  @Override
  public String toString() {
    return "JpaDomainDescriptor{" +
        "domainId='" + domainId + '\'' +
        ", providerPluginId='" + providerPluginId + '\'' +
        ", entityPackages=" + Arrays.toString(entityPackages) +
        ", dataSourceBeanName='" + dataSourceBeanName + '\'' +
        ", entityManagerFactoryBeanName='" + entityManagerFactoryBeanName + '\'' +
        ", transactionManagerBeanName='" + transactionManagerBeanName + '\'' +
        ", ready=" + ready +
        ", createdAt=" + createdAt +
        '}';
  }
}
