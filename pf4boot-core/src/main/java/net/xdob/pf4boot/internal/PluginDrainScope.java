package net.xdob.pf4boot.internal;

import net.xdob.pf4boot.deployment.PluginTrafficDrainer;
import net.xdob.pf4boot.util.PluginCleanup;
import org.pf4j.PluginRuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 生命周期排空范围，统一超时预算，并在任意失败后恢复已开始的入口。 */
public final class PluginDrainScope implements AutoCloseable {
  private final List<PluginTrafficDrainer> begun = new ArrayList<>();
  private final Collection<String> pluginIds;

  private PluginDrainScope(Collection<String> pluginIds) {
    this.pluginIds = pluginIds;
  }

  /** 禁止新工作并等待所有在途工作退出。 */
  public static PluginDrainScope open(Collection<PluginTrafficDrainer> drainers,
      Collection<String> pluginIds, long timeoutMillis) {
    PluginDrainScope scope = new PluginDrainScope(pluginIds);
    try {
      long started = System.nanoTime();
      for (PluginTrafficDrainer drainer : drainers) {
        scope.begun.add(drainer);
        drainer.beginDrain(pluginIds);
      }
      for (PluginTrafficDrainer drainer : scope.begun) {
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (!drainer.awaitDrain(pluginIds, Math.max(0, timeoutMillis - elapsed))) {
          throw new PluginRuntimeException("Plugin drain timed out: %s", pluginIds);
        }
      }
      return scope;
    } catch (Throwable failure) {
      if (failure instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      try {
        scope.close();
      } catch (Throwable cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw new PluginRuntimeException(failure, "Cannot drain plugins: %s", pluginIds);
    }
  }

  @Override
  public void close() {
    PluginCleanup cleanup = new PluginCleanup();
    for (int i = begun.size() - 1; i >= 0; i--) {
      PluginTrafficDrainer drainer = begun.get(i);
      cleanup.run("end drain", () -> drainer.endDrain(pluginIds));
    }
    cleanup.throwIfFailed();
  }
}
