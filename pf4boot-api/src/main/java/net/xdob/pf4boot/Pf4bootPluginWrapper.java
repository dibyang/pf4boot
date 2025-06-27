package net.xdob.pf4boot;

import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Pf4bootPluginWrapper extends PluginWrapper {

	private final AtomicInteger startFailed = new AtomicInteger();


	public Pf4bootPluginWrapper(PluginManager pluginManager, PluginDescriptor descriptor, Path pluginPath, ClassLoader pluginClassLoader) {
		super(pluginManager, descriptor, pluginPath, pluginClassLoader);
	}

	public AtomicInteger getStartFailed() {
		return startFailed;
	}

	public boolean isManualInterventionRequired(){
		return this.getFailedException() instanceof ManualInterventionRequired;
	}

	public Set<Pf4bootPluginWrapper> findRequiredManualPlugins(){
		return findRequiredManualPlugins(this);
	}

	/**
	 * 查找需要手动干预的插件列表
	 */
	private Set<Pf4bootPluginWrapper> findRequiredManualPlugins(PluginWrapper  pluginWrapper){
		Set<Pf4bootPluginWrapper> requiredManualPlugins = new LinkedHashSet<>();
		Pf4bootPluginWrapper p = ((Pf4bootPluginWrapper)pluginWrapper);
		if(p.isManualInterventionRequired())
		{
			requiredManualPlugins.add(p);
		}else{
			for (PluginDependency dependency : p.getDescriptor().getDependencies()) {
				if(!dependency.isOptional()){
					requiredManualPlugins.addAll(
							findRequiredManualPlugins(this.getPluginManager().getPlugin(dependency.getPluginId())));
				}
			}
		}
		return requiredManualPlugins;
	}

}
