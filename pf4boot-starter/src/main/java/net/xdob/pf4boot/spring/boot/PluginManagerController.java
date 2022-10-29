package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.modal.PluginInfo;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;


@RestController
public class PluginManagerController {

  @Autowired
  private Pf4bootPluginManager pluginManager;

  @GetMapping(value = "${spring.pf4boot.controller.base-path:/api/pf4boot/}/list")
  public List<PluginInfo> list() {
    List<PluginWrapper> loadedPlugins = pluginManager.getPlugins();

    // loaded plugins
    List<PluginInfo> plugins = loadedPlugins.stream().map(pluginWrapper -> {
      PluginDescriptor descriptor = pluginWrapper.getDescriptor();
      PluginDescriptor latestDescriptor = null;
      try {
        latestDescriptor = pluginManager.getPluginDescriptorFinder()
            .find(pluginWrapper.getPluginPath());
      } catch (PluginRuntimeException ignored) {
      }
      String newVersion = null;
      if (latestDescriptor != null && !descriptor.getVersion().equals(latestDescriptor.getVersion())) {
        newVersion = latestDescriptor.getVersion();
      }

      return PluginInfo.build(descriptor,
          pluginWrapper.getPluginState(), newVersion,
          pluginManager.getPluginStartingError(pluginWrapper.getPluginId()),
          latestDescriptor == null);
    }).collect(Collectors.toList());

    // yet not loaded plugins
    List<Path> pluginPaths = pluginManager.getPluginRepository().getPluginPaths();
    plugins.addAll(pluginPaths.stream().filter(path ->
        loadedPlugins.stream().noneMatch(plugin -> plugin.getPluginPath().equals(path))
    ).map(path -> {
      PluginDescriptor descriptor = pluginManager
          .getPluginDescriptorFinder().find(path);
      return PluginInfo.build(descriptor, null, null, null, false);
    }).filter(pluginInfo -> loadedPlugins.stream().noneMatch(plugin->plugin.getPluginId().equals(pluginInfo.pluginId))).collect(Collectors.toList()));

    return plugins;
  }

  @GetMapping(value = "${spring.pf4boot.controller.base-path:/api/pf4boot/}/start/{pluginId}")
  public PluginState start(@PathVariable String pluginId) {
    return pluginManager.startPlugin(pluginId);
  }

  @GetMapping(value = "${spring.pf4boot.controller.base-path:/api/pf4boot/}/stop/{pluginId}")
  public PluginState stop(@PathVariable String pluginId) {
    return pluginManager.stopPlugin(pluginId);
  }

  @GetMapping(value = "${spring.pf4boot.controller.base-path:/api/pf4boot/}/reload/{pluginId}")
  public PluginState reload(@PathVariable String pluginId) {
    PluginState pluginState = pluginManager.reloadPlugins(pluginId);
    return pluginState;
  }

  @GetMapping(value = "${spring.pf4boot.controller.base-path:/api/pf4boot/}/reload-all")
  public int reloadAll() {
    pluginManager.reloadPlugins(false);
    return 0;
  }

}
