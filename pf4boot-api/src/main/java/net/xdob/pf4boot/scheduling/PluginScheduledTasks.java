package net.xdob.pf4boot.scheduling;

import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class PluginScheduledTasks {
	private final String pluginId;
	private final ScheduledTaskRegistrar registrar;
	private final Map<Object, Set<ScheduledTask>> scheduledTasks = new IdentityHashMap<>(4);

	public PluginScheduledTasks(String pluginId, ScheduledTaskRegistrar registrar) {
		this.pluginId = pluginId;
		this.registrar = registrar;
	}

	public String getPluginId() {
		return pluginId;
	}

	public ScheduledTaskRegistrar getRegistrar() {
		return registrar;
	}

	public Map<Object, Set<ScheduledTask>> getScheduledTasks() {
		return scheduledTasks;
	}
}
