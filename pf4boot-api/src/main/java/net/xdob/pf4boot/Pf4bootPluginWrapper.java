package net.xdob.pf4boot;

import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public class Pf4bootPluginWrapper extends PluginWrapper {

	private final AtomicInteger startFailed = new AtomicInteger();


	public Pf4bootPluginWrapper(PluginManager pluginManager, PluginDescriptor descriptor, Path pluginPath, ClassLoader pluginClassLoader) {
		super(pluginManager, descriptor, pluginPath, pluginClassLoader);
	}

	public AtomicInteger getStartFailed() {
		return startFailed;
	}

}
