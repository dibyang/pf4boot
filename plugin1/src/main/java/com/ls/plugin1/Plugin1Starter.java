package com.ls.plugin1;

import com.ls.pf4boot.autoconfigure.SpringBootPlugin;
import org.springframework.boot.SpringApplication;

/**
 * Plugin1Starter
 *
 * @author yangzj
 * @version 1.0
 */
@SpringBootPlugin
public class Plugin1Starter {
  public static void main(String[] args) {
    SpringApplication.run(Plugin1Starter.class, args);
  }
}
