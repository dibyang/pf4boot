package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.deployment.PluginDeploymentService;
import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.servlet.Servlet;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnClass({Servlet.class, Pf4bootPluginManager.class, PluginDeploymentService.class})
@EnableConfigurationProperties(Pf4bootManagementProperties.class)
@ConditionalOnProperty(prefix = Pf4bootManagementProperties.PREFIX, name = "enabled", havingValue = "true")
public class Pf4bootManagementAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(PluginManagementAuthorizer.class)
  @ConditionalOnProperty(prefix = Pf4bootManagementProperties.PREFIX, name = "mode",
      havingValue = "LOCAL_TOKEN")
  public PluginManagementAuthorizer pluginManagementAuthorizer(Pf4bootManagementProperties properties) {
    return new LocalTokenPluginManagementAuthorizer(properties);
  }

  @Bean
  @Primary
  @ConditionalOnProperty(prefix = Pf4bootManagementProperties.PREFIX, name = "mode",
      havingValue = "REMOTE_DELEGATED")
  public PluginManagementAuthorizer delegatingPluginManagementAuthorizer(
      ObjectProvider<PluginManagementAuthorizer> authorizers) {
    List<PluginManagementAuthorizer> delegators = collectAuthorizers(authorizers);
    return new DelegatingPluginManagementAuthorizer(delegators);
  }

  @Bean(initMethod = "validate")
  public PluginManagementStartupValidator pluginManagementStartupValidator(
      Pf4bootManagementProperties properties,
      ObjectProvider<PluginManagementAuthorizer> authorizers) {
    return new PluginManagementStartupValidator(properties, authorizers);
  }

  @Bean
  @ConditionalOnMissingBean(PluginManagementRequestFactory.class)
  public PluginManagementRequestFactory pluginManagementRequestFactory() {
    return new PluginManagementRequestFactory();
  }

  @Bean
  @ConditionalOnMissingBean(PluginManagementPathValidator.class)
  public PluginManagementPathValidator pluginManagementPathValidator() {
    return new PluginManagementPathValidator();
  }

  @Bean
  @ConditionalOnMissingBean(PluginManagementAuditRecorder.class)
  public PluginManagementAuditRecorder pluginManagementAuditRecorder() {
    return new LoggingPluginManagementAuditRecorder();
  }

  @Bean
  @ConditionalOnMissingBean(PluginDeploymentRecordStore.class)
  public PluginDeploymentRecordStore pluginDeploymentRecordStore() {
    return new InMemoryPluginDeploymentRecordStore();
  }

  @Bean
  @ConditionalOnMissingBean(PluginOperationStore.class)
  public PluginOperationStore pluginOperationStore() {
    return new InMemoryPluginOperationStore();
  }

  @Bean
  @ConditionalOnMissingBean(PluginManagementIdempotencyService.class)
  public PluginManagementIdempotencyService pluginManagementIdempotencyService(
      Pf4bootManagementProperties properties,
      PluginOperationStore operationStore) {
    return new PluginManagementIdempotencyService(properties, operationStore);
  }

  @Bean
  @ConditionalOnMissingBean(PluginManagementController.class)
  public PluginManagementController pluginManagementController(
      Pf4bootPluginManager pluginManager,
      PluginDeploymentService deploymentService,
      Pf4bootManagementProperties properties,
      PluginManagementAuthorizer authorizer,
      PluginManagementRequestFactory requestFactory,
      PluginManagementPathValidator pathValidator,
      PluginManagementIdempotencyService idempotencyService,
      PluginDeploymentRecordStore deploymentRecordStore,
      PluginManagementAuditRecorder auditRecorder,
      PluginOperationStore operationStore) {
    return new PluginManagementController(
        pluginManager,
        deploymentService,
        properties,
        authorizer,
        requestFactory,
        pathValidator,
        idempotencyService,
        deploymentRecordStore,
        auditRecorder,
        operationStore);
  }

  @Bean
  @ConditionalOnMissingBean(PluginManagementExceptionHandler.class)
  public PluginManagementExceptionHandler pluginManagementExceptionHandler() {
    return new PluginManagementExceptionHandler();
  }

  private List<PluginManagementAuthorizer> collectAuthorizers(
      ObjectProvider<PluginManagementAuthorizer> authorizers) {
    return authorizers.orderedStream()
        .filter(authorizer -> authorizer != null && !(authorizer instanceof DelegatingPluginManagementAuthorizer))
        .collect(Collectors.toList());
  }
}
