package com.ls.plugin2;

import com.ls.pf4boot.autoconfigure.SpringBootPlugin;
import org.springframework.boot.SpringApplication;

/**
 * Plugin2Starter
 *
 * @author yangzj
 * @version 1.0
 */
@SpringBootPlugin
public class Plugin2Starter {
  public static void main(String[] args) {
    SpringApplication.run(Plugin2Starter.class, args);
  }
}
