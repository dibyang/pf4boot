package net.xdob.pf4boot.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件生命周期并发控制诊断报告。
 *
 * <p>用于描述当前宿主采用的生命周期互斥策略。该对象不暴露锁对象本身，避免外部调用方
 * 通过诊断接口影响运行时并发控制。</p>
 */
public class PluginConcurrencyReport {

  private final String strategy;
  private final boolean lifecycleMutationsSerialized;
  private final List<String> protectedOperations;

  public PluginConcurrencyReport(
      String strategy,
      boolean lifecycleMutationsSerialized,
      List<String> protectedOperations) {
    this.strategy = strategy;
    this.lifecycleMutationsSerialized = lifecycleMutationsSerialized;
    this.protectedOperations = immutableOperations(protectedOperations);
  }

  public String getStrategy() {
    return strategy;
  }

  public boolean isLifecycleMutationsSerialized() {
    return lifecycleMutationsSerialized;
  }

  public List<String> getProtectedOperations() {
    return protectedOperations;
  }

  private List<String> immutableOperations(List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }
}
