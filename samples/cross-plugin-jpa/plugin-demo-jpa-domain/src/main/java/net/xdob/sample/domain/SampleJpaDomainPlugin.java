package net.xdob.sample.domain;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.jpa.domain.starter.Pf4bootJpaDomainStarter;
import org.pf4j.PluginWrapper;

/**
 * Sample JPA 领域能力插件。
 *
 * <p>该插件只负责提供 demo domain 的 DataSource、EntityManagerFactory 和 TransactionManager；
 * 业务实体来自 model 模块，Repository 和业务服务由消费插件定义。</p>
 */
@PluginStarter(Pf4bootJpaDomainStarter.class)
public class SampleJpaDomainPlugin extends Pf4bootPlugin {

  public SampleJpaDomainPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public void initiate() {
    setProperty("pf4boot.plugin.jpa.domain.id", "demo");
    setProperty("pf4boot.plugin.jpa.domain.entity-packages[0]", "net.xdob.sample.model.userbook");
    setProperty("pf4boot.plugin.jpa.domain.entity-packages[1]", "net.xdob.sample.model.audit");
    setProperty("pf4boot.plugin.jpa.domain.datasource.url",
        "jdbc:h2:file:./work/h2/pf4boot_sample_cross_plugin_jpa;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1");
    setProperty("pf4boot.plugin.jpa.domain.datasource.username", "sa");
    setProperty("pf4boot.plugin.jpa.domain.datasource.driver-class-name", "org.h2.Driver");
    setProperty("pf4boot.plugin.jpa.domain.ddl-auto", "update");
    setProperty("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
    setProperty("spring.sql.init.enabled", "false");
  }
}
