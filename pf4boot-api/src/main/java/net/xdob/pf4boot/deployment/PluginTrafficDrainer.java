package net.xdob.pf4boot.deployment;

import java.util.Collection;

/**
 * 插件流量摘除扩展点。
 *
 * <p>部署服务在停止插件前调用该扩展点，阻止新请求或新任务进入影响链，并等待在途工作归零。</p>
 */
public interface PluginTrafficDrainer {

  /**
   * 标记插件进入 draining 状态。
   *
   * @param pluginIds 需要摘流的插件 ID
   */
  void beginDrain(Collection<String> pluginIds);

  /**
   * 等待在途工作归零。
   *
   * @param pluginIds 需要等待的插件 ID
   * @param timeoutMillis 超时时间，单位毫秒
   * @return true 表示超时前已归零
   */
  boolean awaitDrain(Collection<String> pluginIds, long timeoutMillis) throws InterruptedException;

  /**
   * 结束 draining 状态。
   *
   * @param pluginIds 插件 ID
   */
  void endDrain(Collection<String> pluginIds);
}
