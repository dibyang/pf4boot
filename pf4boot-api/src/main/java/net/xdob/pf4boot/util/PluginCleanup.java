package net.xdob.pf4boot.util;

import org.pf4j.PluginRuntimeException;
import java.util.ArrayList;
import java.util.List;

/** 逐项执行生命周期清理，保留每个失败原因，确保后续清理仍被尝试。 */
public final class PluginCleanup {
  private PluginRuntimeException failure;
  private final List<Runnable> retries = new ArrayList<>();

  /** 执行一个独立清理步骤。 */
  public void run(String step, Runnable action) {
    try {
      action.run();
    } catch (Throwable cause) {
      if (cause instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      if (failure == null) {
        failure = new PluginRuntimeException("Plugin cleanup failed");
      }
      failure.addSuppressed(new PluginRuntimeException(cause, "Cleanup step failed: %s", step));
      retries.add(() -> run(step, action));
    }
  }

  /** 仅重试失败步骤，避免重复调用已成功的销毁回调。 */
  public void retry() {
    List<Runnable> pending = new ArrayList<>(retries);
    retries.clear();
    failure = null;
    pending.forEach(Runnable::run);
    throwIfFailed();
  }

  /** 全部步骤完成后统一报告失败。 */
  public void throwIfFailed() {
    if (failure != null) {
      throw failure;
    }
  }
}
