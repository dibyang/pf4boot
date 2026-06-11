package net.xdob.pf4boot.jpa.starter;

import org.springframework.util.StringUtils;

/**
 * 单个共享 JPA domain 的 Bean 绑定。
 */
public class JpaDomainBinding {

  private final String domainId;
  private final String entityManagerFactoryRef;
  private final String transactionManagerRef;
  private final String descriptorRef;

  public JpaDomainBinding(
      String domainId,
      String entityManagerFactoryRef,
      String transactionManagerRef,
      String descriptorRef) {
    this.domainId = domainId;
    this.entityManagerFactoryRef = entityManagerFactoryRef;
    this.transactionManagerRef = transactionManagerRef;
    this.descriptorRef = descriptorRef;
  }

  public String getDomainId() {
    return domainId;
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
}
