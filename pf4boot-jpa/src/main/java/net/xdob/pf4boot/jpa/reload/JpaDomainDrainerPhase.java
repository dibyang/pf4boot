package net.xdob.pf4boot.jpa.reload;

/**
 * JPA domain drain 的执行阶段。
 */
public enum JpaDomainDrainerPhase {
  BEGIN,
  AWAIT,
  END
}
