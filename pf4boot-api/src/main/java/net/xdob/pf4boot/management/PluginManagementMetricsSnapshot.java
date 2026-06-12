package net.xdob.pf4boot.management;

/**
 * 插件管理接口指标快照。
 *
 * <p>快照只保存单调递增计数，用于 Micrometer gauge 或只读诊断展示。字段不包含请求明文、
 * token、principal 详情或其它敏感信息。</p>
 */
public class PluginManagementMetricsSnapshot {

  private final long requestTotal;
  private final long rejectedTotal;
  private final long idempotencyHitTotal;

  public PluginManagementMetricsSnapshot(
      long requestTotal,
      long rejectedTotal,
      long idempotencyHitTotal) {
    this.requestTotal = requestTotal;
    this.rejectedTotal = rejectedTotal;
    this.idempotencyHitTotal = idempotencyHitTotal;
  }

  public long getRequestTotal() {
    return requestTotal;
  }

  public long getRejectedTotal() {
    return rejectedTotal;
  }

  public long getIdempotencyHitTotal() {
    return idempotencyHitTotal;
  }
}
