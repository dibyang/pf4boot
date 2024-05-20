package net.xdob.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Paths;

/**
 * Application
 *
 * @author yangzj
 * @version 1.0
 */
@SpringBootApplication
public class DemoApp {
  private static final Logger LOG = LoggerFactory.getLogger(DemoApp.class);

  public static final String APP_HOME = "app.home";
  public static final String USER_DIR = "user.dir";
  public static final String APP_NAME = "app.name";
  public static final String APP_VERSION = "app.version";

  static ConfigurableApplicationContext context;

  public static String getAppHome(){
    String appHome = System.getProperty(APP_HOME, System.getProperty(USER_DIR));
    System.setProperty(APP_HOME,appHome);
    return appHome;
  }

  public static void main(String[] args) {
    String appHome = getAppHome();
    LOG.info("app home:" + appHome);
    System.setProperty(APP_NAME, System.getProperty("name","demo-app"));
    System.setProperty("logging.config", Paths.get(appHome, "config/logback-spring.xml").toString());
    SpringApplicationBuilder builder = new SpringApplicationBuilder();
    //builder.profiles("no_security");
    builder.sources(DemoApp.class);
    builder.application().setAllowBeanDefinitionOverriding(true);
    LOG.info("spring ready run.");

    try {
      context = builder.build().run(args);

    } catch (Exception e) {
      e.printStackTrace();
      LOG.error("spring start fail.",e);
      if(context!=null){
        context.close ();
      }

    }
  }

  /*
  @Bean
  public ApplicationContextAware multiApplicationContextProviderRegister() {
    return ApplicationContextProvider::registerApplicationContext;
  }//*/

}
