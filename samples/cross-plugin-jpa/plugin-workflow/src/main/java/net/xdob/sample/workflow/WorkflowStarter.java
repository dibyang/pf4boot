package net.xdob.sample.workflow;

import net.xdob.pf4boot.annotation.SpringBootPlugin;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 工作流插件 Spring 入口。
 *
 * <p>审计 Repository 单独分包，并显式绑定到 demo 共享 JPA domain。</p>
 */
@SpringBootPlugin
@EnableJpaRepositories(
    basePackages = "net.xdob.sample.workflow.repository",
    entityManagerFactoryRef = "domain.demo.entityManagerFactory",
    transactionManagerRef = "domain.demo.transactionManager"
)
public class WorkflowStarter {
}
