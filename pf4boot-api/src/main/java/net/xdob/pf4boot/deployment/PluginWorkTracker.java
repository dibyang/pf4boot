package net.xdob.pf4boot.deployment;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 插件工作的原子准入、在途计数和可嵌套排空。
 * 所有状态由同一监视器保护，避免检查排空状态后才增加在途计数的竞态。
 */
public final class PluginWorkTracker implements PluginTrafficDrainer {
  private final Map<String, Integer> draining = new HashMap<>();
  private final Map<String, Integer> running = new HashMap<>();

  /** 尝试进入一次工作；排空中的插件拒绝新工作。 */
  public synchronized boolean tryEnter(String pluginId) {
    if (draining.containsKey(pluginId)) {
      return false;
    }
    running.put(pluginId, getRunningCount(pluginId) + 1);
    return true;
  }

  /** 完成工作并移除空闲计数，唤醒排空等待者。 */
  public synchronized void exit(String pluginId) {
    int remaining = getRunningCount(pluginId) - 1;
    if (remaining <= 0) {
      running.remove(pluginId);
    } else {
      running.put(pluginId, remaining);
    }
    notifyAll();
  }

  /** 查询在途工作数。 */
  public synchronized int getRunningCount(String pluginId) {
    return running.getOrDefault(pluginId, 0);
  }

  /** 查询是否仍有未结束的排空范围。 */
  public synchronized boolean isDraining(String pluginId) {
    return draining.containsKey(pluginId);
  }

  @Override
  public synchronized void beginDrain(Collection<String> pluginIds) {
    if (pluginIds != null) {
      for (String id : pluginIds) {
        draining.put(id, draining.getOrDefault(id, 0) + 1);
      }
    }
  }

  @Override
  public synchronized boolean awaitDrain(Collection<String> pluginIds, long timeoutMillis)
      throws InterruptedException {
    long remaining = TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis));
    long started = System.nanoTime();
    while (pluginIds != null && pluginIds.stream().anyMatch(id -> getRunningCount(id) > 0)) {
      if (remaining <= 0) {
        return false;
      }
      TimeUnit.NANOSECONDS.timedWait(this, remaining);
      remaining = TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis))
          - (System.nanoTime() - started);
    }
    return true;
  }

  @Override
  public synchronized void endDrain(Collection<String> pluginIds) {
    if (pluginIds != null) {
      for (String id : pluginIds) {
        int remaining = draining.getOrDefault(id, 0) - 1;
        if (remaining <= 0) {
          draining.remove(id);
        } else {
          draining.put(id, remaining);
        }
      }
    }
  }
}
