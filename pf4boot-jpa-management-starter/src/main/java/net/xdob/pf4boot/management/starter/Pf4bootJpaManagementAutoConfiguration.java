package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.actuate.Pf4bootJpaReloadEndpoint;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlanService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadService;
import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginOperationStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Servlet;

@Configuration
@ConditionalOnClass({Servlet.class, JpaDomainReloadPlanService.class})
@EnableConfigurationProperties(Pf4bootManagementProperties.class)
@AutoConfigureAfter(name = {
    "net.xdob.pf4boot.management.starter.Pf4bootManagementAutoConfiguration",
    "net.xdob.pf4boot.jpa.starter.reload.JpaDomainReloadAutoConfiguration",
    "net.xdob.pf4boot.actuate.autoconfigure.Pf4bootActuatorAutoConfiguration"
})
public class Pf4bootJpaManagementAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(JpaDomainReloadManagementController.class)
  @ConditionalOnBean({
      JpaDomainReloadPlanService.class,
      PluginManagementAuthorizer.class,
      PluginManagementRequestFactory.class,
      PluginManagementAuditRecorder.class
  })
  @ConditionalOnProperty(prefix = Pf4bootManagementProperties.PREFIX, name = "enabled", havingValue = "true")
  public JpaDomainReloadManagementController jpaDomainReloadManagementController(
      JpaDomainReloadPlanService planService,
      ObjectProvider<JpaDomainReloadService> reloadService,
      Pf4bootManagementProperties properties,
      PluginManagementAuthorizer authorizer,
      PluginManagementRequestFactory requestFactory,
      PluginManagementAuditRecorder auditRecorder,
      ObjectProvider<PluginManagementPathValidator> pathValidator,
      ObjectProvider<PluginManagementIdempotencyService> idempotencyService,
      ObjectProvider<PluginOperationStore> operationStore,
      ObjectProvider<PluginManagementWriteSecurityPolicy> writeSecurityPolicy,
      ObjectProvider<DefaultPluginManagementMetricsRecorder> managementMetricsRecorder) {
    return new JpaDomainReloadManagementController(
        planService,
        reloadService,
        properties,
        authorizer,
        requestFactory,
        auditRecorder,
        pathValidator.getIfAvailable(),
        idempotencyService.getIfAvailable(),
        operationStore.getIfAvailable(),
        writeSecurityPolicy.getIfAvailable(),
        managementMetricsRecorder.getIfAvailable());
  }

  @Bean
  @ConditionalOnClass(Endpoint.class)
  @ConditionalOnMissingBean(Pf4bootJpaReloadEndpoint.class)
  @ConditionalOnBean(JpaDomainReloadPlanService.class)
  public Pf4bootJpaReloadEndpoint pf4bootJpaReloadEndpoint(
      JpaDomainReloadPlanService planService,
      ObjectProvider<JpaDomainReloadService> reloadService,
      ObjectProvider<JpaDomainReloadRecordRepository> recordRepository) {
    return new Pf4bootJpaReloadEndpoint(
        planService,
        reloadService.getIfAvailable(),
        recordRepository.getIfAvailable());
  }
}
