package com.ls.pf4boot.modal;

import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginState;

import java.util.ArrayList;
import java.util.List;

/**
 * PluginInfo
 *
 * @author yangzj
 * @version 1.0
 */
public class PluginInfo implements PluginDescriptor {

  public String pluginId;

  public String pluginDescription;

  public String pluginClass;

  public String version;

  public String requires;

  public String provider;

  public String license;

  public List<PluginDependency> dependencies;

  public PluginState pluginState;

  public String newVersion;

  public boolean removed;

  public PluginStartingError startingError;

  public static PluginInfo build(PluginDescriptor descriptor,
                                 PluginState pluginState,
                                 String newVersion,
                                 PluginStartingError startingError,
                                 boolean removed) {
    PluginInfo pluginInfo = new PluginInfo();
    pluginInfo.pluginId = descriptor.getPluginId();
    pluginInfo.pluginDescription = descriptor.getPluginDescription();
    pluginInfo.pluginClass = descriptor.getPluginClass();
    pluginInfo.version = descriptor.getVersion();
    pluginInfo.requires = descriptor.getRequires();
    pluginInfo.provider = descriptor.getProvider();
    pluginInfo.license = descriptor.getLicense();
    if (descriptor.getDependencies() != null) {
      pluginInfo.dependencies = new ArrayList<>(descriptor.getDependencies());
    }
    pluginInfo.pluginState = pluginState;
    pluginInfo.startingError = startingError;
    pluginInfo.newVersion = newVersion;
    pluginInfo.removed = removed;
    return pluginInfo;
  }

  @Override
  public String getPluginId() {
    return pluginId;
  }

  public void setPluginId(String pluginId) {
    this.pluginId = pluginId;
  }

  @Override
  public String getPluginDescription() {
    return pluginDescription;
  }

  public void setPluginDescription(String pluginDescription) {
    this.pluginDescription = pluginDescription;
  }

  @Override
  public String getPluginClass() {
    return pluginClass;
  }

  public void setPluginClass(String pluginClass) {
    this.pluginClass = pluginClass;
  }

  @Override
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public String getRequires() {
    return requires;
  }

  public void setRequires(String requires) {
    this.requires = requires;
  }

  @Override
  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  @Override
  public String getLicense() {
    return license;
  }

  public void setLicense(String license) {
    this.license = license;
  }

  @Override
  public List<PluginDependency> getDependencies() {
    return dependencies;
  }

  public void setDependencies(List<PluginDependency> dependencies) {
    this.dependencies = dependencies;
  }

  public PluginState getPluginState() {
    return pluginState;
  }

  public void setPluginState(PluginState pluginState) {
    this.pluginState = pluginState;
  }

  public String getNewVersion() {
    return newVersion;
  }

  public void setNewVersion(String newVersion) {
    this.newVersion = newVersion;
  }

  public boolean isRemoved() {
    return removed;
  }

  public void setRemoved(boolean removed) {
    this.removed = removed;
  }

  public PluginStartingError getStartingError() {
    return startingError;
  }

  public void setStartingError(PluginStartingError startingError) {
    this.startingError = startingError;
  }
}
