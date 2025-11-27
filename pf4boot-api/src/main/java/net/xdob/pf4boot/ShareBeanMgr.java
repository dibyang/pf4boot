package net.xdob.pf4boot;

/**
 * 共享bean管理器
 *
 * @author yangzj
 * @version 1.0
 */
public interface ShareBeanMgr {
	/**
	 * 开始始化插件管理器
	 */
	default void initiatePluginManager(Pf4bootPluginManager pluginManager){

	}

	/**
	 * 完成始化插件管理器
	 */
	default void initiatedPluginManager(Pf4bootPluginManager pluginManager){

	}
	void startedPlugin(Pf4bootPlugin pf4bootPlugin);
	void stopPlugin(Pf4bootPlugin pf4bootPlugin);
}
