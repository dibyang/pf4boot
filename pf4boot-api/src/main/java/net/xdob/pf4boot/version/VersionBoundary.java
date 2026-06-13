package net.xdob.pf4boot.version;

/**
 * 版本范围的单侧边界。
 *
 * <p>边界用于描述 Maven 风格范围表达式中的下限或上限，例如 {@code [1,2)} 中的
 * {@code 1} 和 {@code 2}。空版本表示该侧为开放边界。</p>
 */
public class VersionBoundary {

  private String version;
  private boolean inclusive;

  public VersionBoundary() {
  }

  public VersionBoundary(String version, boolean inclusive) {
    this.version = version;
    this.inclusive = inclusive;
  }

  /**
   * 返回边界版本；为空表示开放边界。
   */
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * 返回边界是否包含自身。
   */
  public boolean isInclusive() {
    return inclusive;
  }

  public void setInclusive(boolean inclusive) {
    this.inclusive = inclusive;
  }

  /**
   * 判断该侧是否为开放边界。
   */
  public boolean isOpen() {
    return version == null || version.trim().isEmpty();
  }
}
