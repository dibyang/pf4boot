package com.ls.plugin1;

import com.ls.pf4boot.PluginJPASupport;
import com.ls.pf4boot.autoconfigure.SpringBootPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Map;

/**
 * Plugin1Starter
 *
 * @author yangzj
 * @version 1.0
 */
@SpringBootPlugin
@EnableJpaRepositories
public class Plugin1Starter extends PluginJPASupport {


}
