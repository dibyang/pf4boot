package net.xdob.pf4boot.jpa.binding;

/**
 * Consumer 插件对单个共享 JPA domain 的绑定定义。
 */
public final class JpaConsumerDomainBinding {

  private final String domainId;
  private final String entityManagerFactoryRef;
  private final String transactionManagerRef;
  private final String descriptorRef;

  private JpaConsumerDomainBinding(Builder builder) {
    this.domainId = trimToNull(builder.domainId);
    this.entityManagerFactoryRef = trimToNull(builder.entityManagerFactoryRef);
    this.transactionManagerRef = trimToNull(builder.transactionManagerRef);
    this.descriptorRef = trimToNull(builder.descriptorRef);
  }

  public static Builder builder(String domainId) {
    return new Builder(domainId);
  }

  public String getDomainId() {
    return domainId;
  }

  public String getEntityManagerFactoryRef() {
    return entityManagerFactoryRef;
  }

  public String getTransactionManagerRef() {
    return transactionManagerRef;
  }

  public String getDescriptorRef() {
    return descriptorRef;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() == 0 ? null : trimmed;
  }

  /**
   * Shared domain 绑定构建器。
   */
  public static final class Builder {
    private String domainId;
    private String entityManagerFactoryRef;
    private String transactionManagerRef;
    private String descriptorRef;

    private Builder(String domainId) {
      this.domainId = domainId;
    }

    public Builder entityManagerFactoryRef(String entityManagerFactoryRef) {
      this.entityManagerFactoryRef = entityManagerFactoryRef;
      return this;
    }

    public Builder transactionManagerRef(String transactionManagerRef) {
      this.transactionManagerRef = transactionManagerRef;
      return this;
    }

    public Builder descriptorRef(String descriptorRef) {
      this.descriptorRef = descriptorRef;
      return this;
    }

    public JpaConsumerDomainBinding build() {
      return new JpaConsumerDomainBinding(this);
    }
  }
}
