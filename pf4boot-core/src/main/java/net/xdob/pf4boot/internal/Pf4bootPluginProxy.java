package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.pf4j.Plugin;
import org.springframework.context.ConfigurableApplicationContext;

public class Pf4bootPluginProxy extends Pf4bootPlugin {
	private final Plugin proxy;

	public Pf4bootPluginProxy(Plugin proxy) {
		super(proxy.getWrapper());
		this.proxy = proxy;
	}

	@Override
	public ConfigurableApplicationContext getPluginContext() {
		return super.getPluginContext();
	}

	@Override
	public void start() {
		proxy.start();
	}

	@Override
	public void stop() {
		proxy.stop();
	}

	@Override
	public void delete() {
		proxy.delete();
	}
}
