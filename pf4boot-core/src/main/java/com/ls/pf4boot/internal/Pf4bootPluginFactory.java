package com.ls.pf4boot.internal;

import com.ls.pf4boot.Pf4bootPlugin;
import com.ls.pf4boot.Pf4bootPluginService;
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
    if(plugin!=null){
      plugin = new Pf4bootPluginService(plugin);
    }
    return plugin;
  }
}
