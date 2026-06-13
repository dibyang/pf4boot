package net.xdob.pf4boot.version;

/**
 * 版本范围匹配结果。
 *
 * <p>调用方应根据 {@link #isValid()} 区分表达式是否合法，再根据
 * {@link #isMatched()} 判断版本是否满足范围。</p>
 */
public class VersionMatchResult {

  private final boolean valid;
  private final boolean matched;
  private final String message;
  private final VersionRange range;

  public VersionMatchResult(boolean valid, boolean matched, String message, VersionRange range) {
    this.valid = valid;
    this.matched = matched;
    this.message = message;
    this.range = range;
  }

  /**
   * 创建匹配成功结果。
   */
  public static VersionMatchResult matched(VersionRange range, String message) {
    return new VersionMatchResult(true, true, message, range);
  }

  /**
   * 创建范围合法但版本不匹配结果。
   */
  public static VersionMatchResult unmatched(VersionRange range, String message) {
    return new VersionMatchResult(true, false, message, range);
  }

  /**
   * 创建非法范围表达式结果。
   */
  public static VersionMatchResult invalid(String message) {
    return new VersionMatchResult(false, false, message, null);
  }

  public boolean isValid() {
    return valid;
  }

  public boolean isMatched() {
    return matched;
  }

  public String getMessage() {
    return message;
  }

  public VersionRange getRange() {
    return range;
  }
}
