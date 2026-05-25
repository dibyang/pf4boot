package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.modal.PluginInfo;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("${spring.pf4boot.controller.base-path:/api/pf4boot/}plugin")
public class PluginManagerController {


	public static final String ALL = "all";
	private final Pf4bootPluginManager pluginManager;

  public PluginManagerController(Pf4bootPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @GetMapping(value = "/auto-start/get")
  public Boolean istAutoStartPlugin() {
    return pluginManager.isAutoStartPlugin();
  }

  @GetMapping(value = "/auto-start/set/{autoStartPlugin}")
  public Boolean setAutoStartPlugin(@PathVariable Boolean autoStartPlugin ) {
    if(autoStartPlugin!=null){
      pluginManager.setAutoStartPlugin(autoStartPlugin);
    }
    return pluginManager.isAutoStartPlugin();
  }

  @GetMapping(value = "/list")
  public List<PluginInfo> list() {
    return getPluginInfos();
  }

  private List<PluginInfo> getPluginInfos() {
    List<PluginWrapper> loadedPlugins = pluginManager.getPlugins();

    // loaded plugins
    return loadedPlugins.stream().map(pluginWrapper -> {
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
          pluginManager.getPluginErrors(pluginWrapper.getPluginId()),
          latestDescriptor == null);
    }).collect(Collectors.toList());
  }


  private PluginInfo getPluginInfo(String pluginId) {
    return getPluginInfos().stream().filter(pluginInfo -> pluginInfo.getPluginId().equals(pluginId))
        .findFirst().orElse(null);
  }

  @GetMapping(value = "/enable/{pluginId}")
  public PluginInfo enable(@PathVariable String pluginId) {
    pluginManager.enablePlugin(pluginId);
    return getPluginInfo(pluginId);
  }

  @GetMapping(value = "/disable/{pluginId}")
  public PluginInfo disable(@PathVariable String pluginId) {
    pluginManager.disablePlugin(pluginId);
    return getPluginInfo(pluginId);
  }


  @GetMapping(value = "/start/{pluginId}")
  public PluginInfo start(@PathVariable String pluginId) {
		if(isAll(pluginId)){
			pluginManager.startPlugins();
		}else {
			pluginManager.startPlugin(pluginId);
		}
    return getPluginInfo(pluginId);
  }


  @GetMapping(value = "/stop/{pluginId}")
  public PluginInfo stop(@PathVariable String pluginId) {
		if(isAll(pluginId)){
			pluginManager.stopPlugins();
		}else {
			pluginManager.stopPlugin(pluginId);
		}
    return getPluginInfo(pluginId);
  }

  @GetMapping(value = "/restart/{pluginId}")
  public PluginInfo restart(@PathVariable String pluginId) {
		if(isAll(pluginId)){
			pluginManager.restartPlugins();
		}else {
			pluginManager.restartPlugin(pluginId);
		}
    return getPluginInfo(pluginId);
  }

  @GetMapping(value = "/reload/{pluginId}")
  public PluginInfo reload(@PathVariable String pluginId) {
		if(isAll(pluginId)){
			pluginManager.reloadPlugins(true);
		}else {
			pluginManager.reloadPlugin(pluginId);
		}
    return getPluginInfo(pluginId);
  }

	private static boolean isAll(String pluginId) {
		return ALL.equals(pluginId);
	}


}
