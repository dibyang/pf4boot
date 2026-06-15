package net.xdob.pf4boot.jpa.reload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 使用某个 JPA domain 的消费插件摘要。
 */
public class JpaDomainConsumer {

  private final String pluginId;
  private final String pluginVersion;
  private final String domainId;
  private final String mode;
  private final JpaDomainConsumerDetection detection;
  private final List<String> dependencyPath;
  private final String entityManagerFactoryRef;
  private final String transactionManagerRef;
  private final String descriptorRef;

  public JpaDomainConsumer(
      String pluginId,
      String pluginVersion,
      String domainId,
      String mode,
      JpaDomainConsumerDetection detection,
      List<String> dependencyPath,
      String entityManagerFactoryRef,
      String transactionManagerRef,
      String descriptorRef) {
    this.pluginId = pluginId;
    this.pluginVersion = pluginVersion;
    this.domainId = domainId;
    this.mode = mode;
    this.detection = detection;
    this.dependencyPath = dependencyPath == null
        ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<>(dependencyPath));
    this.entityManagerFactoryRef = entityManagerFactoryRef;
    this.transactionManagerRef = transactionManagerRef;
    this.descriptorRef = descriptorRef;
  }

  public String getPluginId() {
    return pluginId;
  }

  public String getPluginVersion() {
    return pluginVersion;
  }

  public String getDomainId() {
    return domainId;
  }

  public String getMode() {
    return mode;
  }

  public JpaDomainConsumerDetection getDetection() {
    return detection;
  }

  public List<String> getDependencyPath() {
    return dependencyPath;
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
}
