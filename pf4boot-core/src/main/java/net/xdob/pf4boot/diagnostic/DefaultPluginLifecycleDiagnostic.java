package net.xdob.pf4boot.diagnostic;

import net.xdob.pf4boot.DefaultShareBeanMgr;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 默认插件生命周期诊断实现。
 *
 * <p>当前实现只读取 core 模块能够稳定获取的资源计数。ClassLoader 可达性采用弱引用和
 * 有限 GC 尝试做 best-effort 诊断，不能作为强一致泄漏证明。</p>
 */
public class DefaultPluginLifecycleDiagnostic implements PluginLifecycleDiagnostic {

  private final DefaultShareBeanMgr shareBeanMgr;

  public DefaultPluginLifecycleDiagnostic(DefaultShareBeanMgr shareBeanMgr) {
    this.shareBeanMgr = shareBeanMgr;
  }

  @Override
  public PluginCleanupReport inspectAfterStop(String pluginId, ClassLoader pluginClassLoader) {
    int sharingBeans = shareBeanMgr == null ? 0 : shareBeanMgr.getRegisteredSharingBeanCount(pluginId);
    int extensionBeans = shareBeanMgr == null ? 0 : shareBeanMgr.getRegisteredExtensionBeanCount(pluginId);
    int scheduledTasks = shareBeanMgr == null ? 0 : shareBeanMgr.getScheduledTaskCount(pluginId);
    int runningTasks = shareBeanMgr == null ? 0 : shareBeanMgr.getRunningScheduledTaskCount(pluginId);
    boolean classLoaderReachable = isClassLoaderReachable(pluginClassLoader);
    List<String> messages = new ArrayList<>();
    addMessage(messages, sharingBeans, "sharing beans remain");
    addMessage(messages, extensionBeans, "extension beans remain");
    addMessage(messages, scheduledTasks, "scheduled tasks remain");
    addMessage(messages, runningTasks, "scheduled tasks still running");
    if (classLoaderReachable) {
      messages.add("classloader is still reachable by best-effort check");
    }
    boolean passed = sharingBeans == 0
        && extensionBeans == 0
        && scheduledTasks == 0
        && runningTasks == 0;
    if (passed) {
      messages.add("plugin core resources are cleaned");
    }
    return new PluginCleanupReport(
        pluginId,
        passed,
        sharingBeans,
        extensionBeans,
        scheduledTasks,
        runningTasks,
        classLoaderReachable,
        messages);
  }

  @Override
  public PluginConcurrencyReport inspectLifecycleLocks() {
    return new PluginConcurrencyReport(
        "Pf4bootPluginManagerImpl.stateLock",
        true,
        Arrays.asList(
            "startPlugins",
            "stopPlugins",
            "startPlugin",
            "stopPlugin",
            "restartPlugin",
            "reloadPlugin",
            "reloadPlugins",
            "upgradePlugin"));
  }

  private void addMessage(List<String> messages, int count, String label) {
    if (count > 0) {
      messages.add(count + " " + label);
    }
  }

  private boolean isClassLoaderReachable(ClassLoader classLoader) {
    if (classLoader == null) {
      return false;
    }
    WeakReference<ClassLoader> reference = new WeakReference<>(classLoader);
    classLoader = null;
    for (int i = 0; i < 2 && reference.get() != null; i++) {
      System.gc();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return reference.get() != null;
  }
}
