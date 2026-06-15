package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadPlanService;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import net.xdob.pf4boot.jpa.reload.JpaDomainReloadService;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * JPA domain 刷新 V1-Plan 自动配置。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(JpaDomainReloadPlanService.class)
@EnableConfigurationProperties(Pf4bootJpaProperties.class)
public class JpaDomainReloadAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JpaPluginBindingRegistry jpaPluginBindingRegistry() {
    return new DefaultJpaPluginBindingRegistry();
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean
  public DefaultJpaDomainReloadPlanService jpaDomainReloadPlanService(
      ObjectProvider<Pf4bootPluginManager> pluginManager,
      JpaPluginBindingRegistry bindingRegistry,
      Pf4bootJpaProperties properties) {
    return new DefaultJpaDomainReloadPlanService(
        pluginManager.getIfAvailable(),
        bindingRegistry,
        properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public JpaDomainReloadRecordRepository jpaDomainReloadRecordRepository(Pf4bootJpaProperties properties) {
    return new InMemoryJpaDomainReloadRecordRepository(properties.getDomainReload().getMaxRecentRecords());
  }

  @Bean
  @ConditionalOnMissingBean
  public JpaDomainReloadDrainCoordinator jpaDomainReloadDrainCoordinator(
      ObjectProvider<PluginTrafficDrainer> trafficDrainers,
      Pf4bootJpaProperties properties) {
    return new JpaDomainReloadDrainCoordinator(trafficDrainers, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public JpaDomainReloadService jpaDomainReloadService(
      ObjectProvider<Pf4bootPluginManager> pluginManager,
      DefaultJpaDomainReloadPlanService planService,
      JpaDomainReloadRecordRepository recordRepository,
      Pf4bootJpaProperties properties) {
    return new DefaultJpaDomainReloadService(
        pluginManager.getIfAvailable(),
        planService,
        recordRepository,
        properties);
  }
}
