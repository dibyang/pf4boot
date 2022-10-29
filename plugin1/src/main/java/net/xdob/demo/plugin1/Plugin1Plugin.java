package net.xdob.demo.plugin1;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.jpa.PluginJPAStarter;
import net.xdob.pf4boot.annotation.PluginStarter;
import org.pf4j.PluginWrapper;

/**
 * Plugin1
 *
 * @author yangzj
 * @version 1.0
 */
@PluginStarter({Plugin1Starter.class, PluginJPAStarter.class})
public class Plugin1Plugin extends Pf4bootPlugin {
  public Plugin1Plugin(PluginWrapper wrapper) {
    super(wrapper);
  }


}
