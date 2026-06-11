package net.xdob.pf4boot.jpa.starter;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 共享 JPA 模式的 BeanDefinition 预注册配置。
 *
 * <p>必须早于 Spring Data JPA repository 自动配置执行，否则 repository 后处理器
 * 会先扫描父上下文中的共享 EMF 名称，并因当前插件上下文缺少同名 BeanDefinition
 * 而启动失败。</p>
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(JpaRepositoriesAutoConfiguration.class)
@Import(SharedJpaBeanDefinitionRegistrar.class)
public class SharedJpaAutoConfiguration {
}
