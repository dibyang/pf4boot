package net.xdob.pf4boot;


/**
 * 插件启停的扩展支持
 */
public interface Pf4bootPluginSupport {
  int HEIGHT_PRIORITY = 10;
  int DEFAULT_PRIORITY = 20;

  int LOW_PRIORITY = 30;
  /**
   * 组件优先级，越小越优先，默认20
   * @return 组件优先级
   */
  default int getPriority(){
    return DEFAULT_PRIORITY;
  }


  /**
   * 插件初始化前
   */
  default void initiatePlugin(Pf4bootPlugin pf4bootPlugin){

  }

  /**
   * 插件初始化后
   */
  default void initiatedPlugin(Pf4bootPlugin pf4bootPlugin){

  }

  /**
   * 启动插件前
   * @param pf4bootPlugin 插件
   */
  default void startPlugin(Pf4bootPlugin pf4bootPlugin){

  }

  /**
   * 启动插件后
   * @param pf4bootPlugin 插件
   */
  default void startedPlugin(Pf4bootPlugin pf4bootPlugin){

  }

  /**
   * 停止插件前
   * @param pf4bootPlugin 插件
   */
  default void stopPlugin(Pf4bootPlugin pf4bootPlugin){

  }

  /**
   * 停止插件后
   * @param pf4bootPlugin 插件
   */
  default void stoppedPlugin(Pf4bootPlugin pf4bootPlugin){

  }

	/**
	 * 开始释放插件资源
	 * @param pf4bootPlugin 插件
	 */
	default void releasePlugin(Pf4bootPlugin pf4bootPlugin){

	}

  /**
   * 删除插件前
   * @param pf4bootPlugin 插件
   */
  default void deletePlugin(Pf4bootPlugin pf4bootPlugin){

  }

  /**
   * 删除插件后
   * @param pf4bootPlugin 插件
   */
  default void deletedPlugin(Pf4bootPlugin pf4bootPlugin){

  }
}
