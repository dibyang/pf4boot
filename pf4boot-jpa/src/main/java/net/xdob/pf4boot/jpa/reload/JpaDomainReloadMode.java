package net.xdob.pf4boot.jpa.reload;

/**
 * JPA domain 运行时刷新模式。
 *
 * <p>默认必须保持禁用；执行模式只用于显式维护窗口下的重启式刷新。</p>
 */
public enum JpaDomainReloadMode {
  DISABLED,
  PLAN_ONLY,
  STOP_CONSUMERS_AND_REBUILD
}
