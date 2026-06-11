package net.xdob.sample.host;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 跨插件 JPA 复杂示例宿主应用。
 */
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
public class CrossPluginJpaSampleHost {

  public static void main(String[] args) {
    new SpringApplicationBuilder(CrossPluginJpaSampleHost.class)
        .properties("spring.main.allow-bean-definition-overriding=true")
        .run(args);
  }
}
