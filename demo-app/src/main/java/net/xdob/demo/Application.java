package net.xdob.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Application
 *
 * @author yangzj
 * @version 1.0
 */
@SpringBootApplication
public class Application {
  private static final Logger logger = LoggerFactory.getLogger(Application.class);


  public static void main(String[] args) {
    logger.trace("app run....");
    SpringApplicationBuilder builder = new SpringApplicationBuilder();
    //builder.profiles("no_security");
    builder.sources(Application.class);
    builder.build().run();
  }

  /*
  @Bean
  public ApplicationContextAware multiApplicationContextProviderRegister() {
    return ApplicationContextProvider::registerApplicationContext;
  }//*/

}
