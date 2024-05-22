package net.xdob.demo.plugin1;

import net.xdob.pf4boot.annotation.PluginStarter;
import net.xdob.pf4boot.jpa.PluginJPAStarter;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * Plugin1
 *
 * @author yangzj
 * @version 1.0
 */
@PluginStarter({Plugin1Starter.class, PluginJPAStarter.class})
public class Plugin1Plugin extends Plugin {
  public Plugin1Plugin(PluginWrapper wrapper) {
    super(wrapper);
  }


}
