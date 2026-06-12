package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementMetricsProvider;
import net.xdob.pf4boot.management.PluginManagementMetricsSnapshot;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认插件管理接口指标记录器。
 *
 * <p>该实现只维护内存计数，不保存请求明文、token 或 principal 详情。计数供 actuator
 * 只读读取，管理接口不可依赖这些计数做权限或幂等判断。</p>
 */
public class DefaultPluginManagementMetricsRecorder implements PluginManagementMetricsProvider {

  private final AtomicLong requestTotal = new AtomicLong();
  private final AtomicLong rejectedTotal = new AtomicLong();
  private final AtomicLong idempotencyHitTotal = new AtomicLong();

  /**
   * 记录一次管理 HTTP 请求进入 Controller。
   */
  public void recordRequest() {
    requestTotal.incrementAndGet();
  }

  /**
   * 记录一次管理请求被拒绝或失败在预检/鉴权阶段。
   */
  public void recordRejected() {
    rejectedTotal.incrementAndGet();
  }

  /**
   * 记录一次幂等请求 replay 命中。
   */
  public void recordIdempotencyHit() {
    idempotencyHitTotal.incrementAndGet();
  }

  @Override
  public PluginManagementMetricsSnapshot snapshot() {
    return new PluginManagementMetricsSnapshot(
        requestTotal.get(),
        rejectedTotal.get(),
        idempotencyHitTotal.get());
  }
}
