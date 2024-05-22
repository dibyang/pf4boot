package net.xdob.demo.plugin2;

import net.xdob.pf4boot.Pf4bootPlugin;
import net.xdob.pf4boot.annotation.PluginStarter;
import org.pf4j.PluginWrapper;

/**
 * Plugin2
 *
 * @author yangzj
 * @version 1.0
 */
@PluginStarter(Plugin2Starter.class)
public class Plugin2Plugin extends Pf4bootPlugin {
  public Plugin2Plugin(PluginWrapper wrapper) {
    super(wrapper);
  }

}
