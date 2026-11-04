package net.xdob.pf4boot.util;

import org.pf4j.PluginRuntimeException;
import java.util.ArrayList;
import java.util.List;

/** 串行执行生命周期清理并收集失败；动作可嵌套执行，但不能在动作内重试计划。 */
public final class PluginCleanup {
  private PluginRuntimeException failure;
  private final List<Runnable> retries = new ArrayList<>();
  private int activeActions;

  /** 执行一个独立清理步骤。 */
  public void run(String step, Runnable action) {
    activeActions++;
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
    } finally {
      activeActions--;
    }
  }

  /** 仅在动作全部退出后重试失败步骤，避免递归消费尚在执行的清理计划。 */
  public void retry() {
    if (activeActions != 0) {
      throw new PluginRuntimeException("Cannot retry plugin cleanup while a cleanup action is running");
    }
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
