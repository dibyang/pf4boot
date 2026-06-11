package net.xdob.pf4boot.jpa.starter;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.deployment.PluginHealthVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

/**
 * JPA 热替换部署验证自动配置。
 *
 * <p>宿主应用引入 JPA starter 后自动提供 JPA 模块级健康检查和 stop 后清理验证能力。
 * Pf4bootPluginManager 通过 ObjectProvider 延迟注入，避免被自动配置顺序影响。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({
    DataSource.class,
    EntityManagerFactory.class,
    PlatformTransactionManager.class,
    PluginHealthVerifier.class
})
public class JpaDeploymentAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JpaPluginDeploymentVerifier jpaPluginDeploymentVerifier(
      ObjectProvider<Pf4bootPluginManager> pluginManager) {
    return new JpaPluginDeploymentVerifier(pluginManager.getIfAvailable());
  }
}
