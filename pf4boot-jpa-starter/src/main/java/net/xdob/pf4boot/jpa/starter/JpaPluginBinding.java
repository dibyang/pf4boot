package net.xdob.pf4boot.jpa.starter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 当前插件最终生效的 JPA 绑定。
 */
public class JpaPluginBinding {

  private final String pluginId;
  private final Pf4bootJpaProperties.Mode mode;
  private final String domainId;
  private final String entityManagerFactoryRef;
  private final String transactionManagerRef;
  private final String descriptorRef;
  private final List<JpaDomainBinding> additionalDomains;
  private final boolean pluginSpecific;

  public JpaPluginBinding(
      String pluginId,
      Pf4bootJpaProperties.Mode mode,
      String domainId,
      String entityManagerFactoryRef,
      String transactionManagerRef,
      String descriptorRef,
      List<JpaDomainBinding> additionalDomains,
      boolean pluginSpecific) {
    this.pluginId = pluginId;
    this.mode = mode == null ? Pf4bootJpaProperties.Mode.LOCAL : mode;
    this.domainId = domainId;
    this.entityManagerFactoryRef = entityManagerFactoryRef;
    this.transactionManagerRef = transactionManagerRef;
    this.descriptorRef = descriptorRef;
    this.additionalDomains = additionalDomains == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(additionalDomains));
    this.pluginSpecific = pluginSpecific;
  }

  public String getPluginId() {
    return pluginId;
  }

  public Pf4bootJpaProperties.Mode getMode() {
    return mode;
  }

  public String getDomainId() {
    return domainId;
  }

  public boolean isShared() {
    return Pf4bootJpaProperties.Mode.SHARED == this.mode;
  }

  public boolean isPluginSpecific() {
    return pluginSpecific;
  }

  public JpaDomainBinding primaryDomain() {
    return new JpaDomainBinding(
        this.domainId,
        this.entityManagerFactoryRef,
        this.transactionManagerRef,
        this.descriptorRef);
  }

  public List<JpaDomainBinding> getAdditionalDomains() {
    return additionalDomains;
  }

  public String resolveEntityManagerFactoryRef() {
    return primaryDomain().resolveEntityManagerFactoryRef();
  }

  public String resolveTransactionManagerRef() {
    return primaryDomain().resolveTransactionManagerRef();
  }

  public String resolveDescriptorRef() {
    return primaryDomain().resolveDescriptorRef();
  }
}
