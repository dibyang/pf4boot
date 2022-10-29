package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@AutoConfigureAfter(FlywayAutoConfiguration.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", matchIfMissing = true)
@ConditionalOnBean(Pf4bootPlugin.class)
public class FlywayClassLoaderConfiguration {

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private Pf4bootPlugin plugin;

  @Autowired
  private ApplicationContext applicationContext;

  @Bean
  @ConditionalOnMissingBean
  public FlywayMigrationStrategy migrationStrategy() {
    return flyway -> {
      FluentConfiguration alterConf = Flyway.configure(plugin.getWrapper().getPluginClassLoader());
      alterConf.configuration(flyway.getConfiguration());
      new Flyway(alterConf).migrate();
    };
  }

}
