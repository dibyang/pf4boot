package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPluginHandler;
import org.pf4j.DefaultPluginFactory;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * Pf4bootPluginFactory
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginFactory extends DefaultPluginFactory {
  @Override
  public Plugin create(PluginWrapper pluginWrapper) {
    Plugin plugin = super.create(pluginWrapper);
    //Preconditions.checkState(plugin instanceof Pf4bootPlugin,"Plugin must be a subclass of Pf4bootPlugin.");
    plugin = new Pf4bootPluginHandler(plugin);
    return plugin;
  }
}
