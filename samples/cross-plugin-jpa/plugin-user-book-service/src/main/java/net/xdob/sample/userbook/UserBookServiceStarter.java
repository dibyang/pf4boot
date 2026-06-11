package net.xdob.sample.userbook;

import net.xdob.pf4boot.annotation.SpringBootPlugin;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 用户图书业务插件 Spring 入口。
 *
 * <p>Repository 显式绑定到 demo 共享 JPA domain。</p>
 */
@SpringBootPlugin
@EnableJpaRepositories(
    basePackages = "net.xdob.sample.userbook.repository",
    entityManagerFactoryRef = "domain.demo.entityManagerFactory",
    transactionManagerRef = "domain.demo.transactionManager"
)
public class UserBookServiceStarter {
}
