package net.xdob.pf4boot.version;

/**
 * 版本范围解析结果。
 *
 * <p>第一阶段支持精确版本和常见 Maven 风格范围，例如 {@code 1.2.3}、
 * {@code [1.0,2.0)}、{@code [1.0,)} 和 {@code (,2.0]}。</p>
 */
public class VersionRange {

  private String expression;
  private VersionBoundary lower;
  private VersionBoundary upper;
  private boolean exact;

  public VersionRange() {
  }

  public VersionRange(String expression, VersionBoundary lower, VersionBoundary upper, boolean exact) {
    this.expression = expression;
    this.lower = lower;
    this.upper = upper;
    this.exact = exact;
  }

  /**
   * 返回原始范围表达式。
   */
  public String getExpression() {
    return expression;
  }

  public void setExpression(String expression) {
    this.expression = expression;
  }

  /**
   * 返回下限边界。
   */
  public VersionBoundary getLower() {
    return lower;
  }

  public void setLower(VersionBoundary lower) {
    this.lower = lower;
  }

  /**
   * 返回上限边界。
   */
  public VersionBoundary getUpper() {
    return upper;
  }

  public void setUpper(VersionBoundary upper) {
    this.upper = upper;
  }

  /**
   * 返回是否为精确版本匹配。
   */
  public boolean isExact() {
    return exact;
  }

  public void setExact(boolean exact) {
    this.exact = exact;
  }
}
