package com.ls.plugin1;

import com.ls.pf4boot.Pf4bootPlugin;
import com.ls.pf4boot.autoconfigure.PluginStarter;
import org.pf4j.PluginWrapper;

/**
 * Plugin1
 *
 * @author yangzj
 * @version 1.0
 */
@PluginStarter(Plugin1Starter.class)
public class Plugin1Plugin extends Pf4bootPlugin {
  public Plugin1Plugin(PluginWrapper wrapper) {
    super(wrapper);
  }


}
