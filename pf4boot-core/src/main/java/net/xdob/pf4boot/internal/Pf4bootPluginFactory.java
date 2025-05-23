package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.pf4j.DefaultPluginFactory;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Pf4bootPluginFactory
 *
 * @author yangzj
 * @version 1.0
 */
public class Pf4bootPluginFactory extends DefaultPluginFactory {
  static final Logger LOG = LoggerFactory.getLogger(Pf4bootPluginFactory.class);

  @Override
  public Plugin create(PluginWrapper pluginWrapper) {
    Plugin plugin = super.create(pluginWrapper);
    //Preconditions.checkState(plugin instanceof Pf4bootPlugin,"Plugin must be a subclass of Pf4bootPlugin.");
    if(!(plugin instanceof Pf4bootPlugin)) {
      plugin = new Pf4bootPluginProxy(plugin);
    }
    return plugin;
  }

}
