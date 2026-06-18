package net.xdob.sample.template.host;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 最小 pf4boot 宿主应用模板。
 */
@SpringBootApplication
public class TemplateHostApplication {

  public static void main(String[] args) {
    new SpringApplicationBuilder(TemplateHostApplication.class)
        .properties("spring.main.allow-bean-definition-overriding=true")
        .run(args);
  }
}

