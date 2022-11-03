package net.xdob.pf4boot;

import org.pf4j.ManifestPluginDescriptorFinder;
import org.pf4j.PluginDescriptor;

import java.nio.file.Path;

public class ManifestPluginDescriptorFinder2 extends ManifestPluginDescriptorFinder {
  @Override
  public PluginDescriptor find(Path pluginPath) {
    PluginDescriptor pluginDescriptor = super.find(pluginPath);
    return pluginDescriptor.getPluginId()!=null?pluginDescriptor:null;
  }
}
