package net.xdob.demo.plugin2;

import net.xdob.pf4boot.annotation.PluginStarter;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * Plugin2
 *
 * @author yangzj
 * @version 1.0
 */
@PluginStarter(Plugin2Starter.class)
public class Plugin2Plugin extends Plugin {
  public Plugin2Plugin(PluginWrapper wrapper) {
    super(wrapper);
  }

}
