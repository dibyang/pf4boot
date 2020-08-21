package com.ls.plugin1;

import com.ls.pf4boot.Pf4bootPlugin;
import com.ls.pf4boot.spring.boot.Pf4bootApplication;
import org.pf4j.PluginWrapper;

/**
 * Plugin1
 *
 * @author yangzj
 * @version 1.0
 */
public class Plugin1Plugin extends Pf4bootPlugin {
  public Plugin1Plugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  protected Pf4bootApplication createSpringBootstrap() {
    return new Pf4bootApplication(this,Plugin1Starter.class);
  }
}
