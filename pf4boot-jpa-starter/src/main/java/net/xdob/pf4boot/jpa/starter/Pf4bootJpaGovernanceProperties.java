package net.xdob.pf4boot.jpa.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 宿主侧 JPA 治理配置。
 *
 * <p>该前缀只承载平台治理能力，例如 JPA reload，不描述插件实体包、数据源或共享 domain 绑定。</p>
 */
@ConfigurationProperties(prefix = "spring.pf4boot.jpa")
public class Pf4bootJpaGovernanceProperties {

  private Pf4bootJpaProperties.DomainReload reload = new Pf4bootJpaProperties.DomainReload();

  public Pf4bootJpaProperties.DomainReload getReload() {
    return reload;
  }

  public void setReload(Pf4bootJpaProperties.DomainReload reload) {
    this.reload = reload == null ? new Pf4bootJpaProperties.DomainReload() : reload;
  }
}
