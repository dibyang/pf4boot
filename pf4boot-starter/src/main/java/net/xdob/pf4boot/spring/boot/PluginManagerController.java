package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.modal.PluginInfo;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("${spring.pf4boot.controller.base-path:/api/pf4boot/}/plugin/")
public class PluginManagerController {


  private final Pf4bootPluginManager pluginManager;

  public PluginManagerController(Pf4bootPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @GetMapping(value = "/list")
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

    return plugins;
  }

  @GetMapping(value = "/start/{pluginId}")
  public PluginState start(@PathVariable String pluginId) {
    return pluginManager.startPlugin(pluginId);
  }

  @GetMapping(value = "/stop/{pluginId}")
  public PluginState stop(@PathVariable String pluginId) {
    return pluginManager.stopPlugin(pluginId);
  }

  @GetMapping(value = "/reload/{pluginId}")
  public PluginState reload(@PathVariable String pluginId) {
    PluginState pluginState = pluginManager.reloadPlugins(pluginId);
    return pluginState;
  }

  @GetMapping(value = "/reload-all")
  public int reloadAll() {
    pluginManager.reloadPlugins(false);
    return 0;
  }

}
