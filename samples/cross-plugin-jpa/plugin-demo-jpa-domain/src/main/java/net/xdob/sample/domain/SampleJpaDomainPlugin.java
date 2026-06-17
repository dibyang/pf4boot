package net.xdob.sample.domain;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.jpa.domain.JpaDataSourceDefinition;
import net.xdob.pf4boot.jpa.domain.JpaDomainDefinition;
import net.xdob.pf4boot.jpa.domain.JpaDomainDefinitionProvider;
import net.xdob.pf4boot.jpa.domain.starter.Pf4bootJpaDomainStarter;
import org.pf4j.PluginWrapper;

/**
 * Sample JPA 领域能力插件。
 *
 * <p>该插件只负责提供 demo domain 的 DataSource、EntityManagerFactory 和 TransactionManager；
 * 业务实体来自 model 模块，Repository 和业务服务由消费插件定义。</p>
 */
@PluginStarter(Pf4bootJpaDomainStarter.class)
public class SampleJpaDomainPlugin extends Pf4bootPlugin implements JpaDomainDefinitionProvider {

  public SampleJpaDomainPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public void initiate() {
    setProperty("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
    setProperty("spring.sql.init.enabled", "false");
  }

  @Override
  public JpaDomainDefinition jpaDomainDefinition() {
    return JpaDomainDefinition.builder("demo")
        .entityPackage("net.xdob.sample.model.userbook")
        .entityPackage("net.xdob.sample.model.audit")
        .dataSource(JpaDataSourceDefinition
            .builder("jdbc:h2:file:./work/h2/pf4boot_sample_cross_plugin_jpa;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1")
            .username("sa")
            .driverClassName("org.h2.Driver")
            .build())
        .ddlAuto("update")
        .build();
  }
}
