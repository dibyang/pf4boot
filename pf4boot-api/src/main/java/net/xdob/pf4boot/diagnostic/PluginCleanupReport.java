package net.xdob.pf4boot.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件停止后的资源清理诊断报告。
 *
 * <p>该模型只表达只读诊断结果，不负责直接修改插件状态。框架模块可以把共享 Bean、
 * extension Bean、定时任务、Web mapping 等资源计数汇总到该报告中，供测试、管理接口
 * 和 actuator 后续复用。</p>
 */
public class PluginCleanupReport {

  private final String pluginId;
  private final boolean passed;
  private final int remainingSharingBeans;
  private final int remainingExtensionBeans;
  private final int remainingScheduledTasks;
  private final int remainingRunningScheduledTasks;
  private final boolean classLoaderReachable;
  private final List<String> messages;

  public PluginCleanupReport(
      String pluginId,
      boolean passed,
      int remainingSharingBeans,
      int remainingExtensionBeans,
      int remainingScheduledTasks,
      int remainingRunningScheduledTasks,
      boolean classLoaderReachable,
      List<String> messages) {
    this.pluginId = pluginId;
    this.passed = passed;
    this.remainingSharingBeans = remainingSharingBeans;
    this.remainingExtensionBeans = remainingExtensionBeans;
    this.remainingScheduledTasks = remainingScheduledTasks;
    this.remainingRunningScheduledTasks = remainingRunningScheduledTasks;
    this.classLoaderReachable = classLoaderReachable;
    this.messages = immutableMessages(messages);
  }

  public String getPluginId() {
    return pluginId;
  }

  public boolean isPassed() {
    return passed;
  }

  public int getRemainingSharingBeans() {
    return remainingSharingBeans;
  }

  public int getRemainingExtensionBeans() {
    return remainingExtensionBeans;
  }

  public int getRemainingScheduledTasks() {
    return remainingScheduledTasks;
  }

  public int getRemainingRunningScheduledTasks() {
    return remainingRunningScheduledTasks;
  }

  public boolean isClassLoaderReachable() {
    return classLoaderReachable;
  }

  public List<String> getMessages() {
    return messages;
  }

  private List<String> immutableMessages(List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }
}
