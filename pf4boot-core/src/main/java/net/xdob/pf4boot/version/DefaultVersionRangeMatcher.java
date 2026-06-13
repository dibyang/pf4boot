package net.xdob.pf4boot.version;

/**
 * 默认版本范围匹配器。
 *
 * <p>实现只覆盖 pf4boot 生产预检第一阶段需要的常用范围语法。版本比较按点号和常见
 * 分隔符切分，数字段按数值比较，非数字限定符按稳定字典序比较。</p>
 */
public class DefaultVersionRangeMatcher implements VersionRangeMatcher {

  @Override
  public VersionRange parse(String expression) {
    if (isBlank(expression)) {
      throw new IllegalArgumentException("Version range expression is empty");
    }
    String value = expression.trim();
    char first = value.charAt(0);
    if (first != '[' && first != '(') {
      return exact(value);
    }
    char last = value.charAt(value.length() - 1);
    if ((last != ']' && last != ')') || value.length() < 3) {
      throw new IllegalArgumentException("Invalid version range: " + expression);
    }
    String body = value.substring(1, value.length() - 1);
    int comma = body.indexOf(',');
    if (comma < 0 || comma != body.lastIndexOf(',')) {
      throw new IllegalArgumentException("Version range must contain one comma: " + expression);
    }
    String lower = body.substring(0, comma).trim();
    String upper = body.substring(comma + 1).trim();
    if (lower.isEmpty() && upper.isEmpty()) {
      throw new IllegalArgumentException("Version range must contain at least one boundary: " + expression);
    }
    return new VersionRange(
        value,
        new VersionBoundary(lower, first == '['),
        new VersionBoundary(upper, last == ']'),
        false);
  }

  @Override
  public VersionMatchResult matches(String version, VersionRange range) {
    if (isBlank(version)) {
      return VersionMatchResult.invalid("Version is empty");
    }
    if (range == null) {
      return VersionMatchResult.invalid("Version range is null");
    }
    if (range.isExact()) {
      boolean matched = compareVersions(version, range.getLower().getVersion()) == 0;
      return matched
          ? VersionMatchResult.matched(range, "Version " + version + " matches " + range.getExpression())
          : VersionMatchResult.unmatched(range, "Version " + version + " does not match " + range.getExpression());
    }
    VersionBoundary lower = range.getLower();
    if (lower != null && !lower.isOpen()) {
      int compare = compareVersions(version, lower.getVersion());
      if (compare < 0 || (compare == 0 && !lower.isInclusive())) {
        return VersionMatchResult.unmatched(
            range, "Version " + version + " is lower than " + range.getExpression());
      }
    }
    VersionBoundary upper = range.getUpper();
    if (upper != null && !upper.isOpen()) {
      int compare = compareVersions(version, upper.getVersion());
      if (compare > 0 || (compare == 0 && !upper.isInclusive())) {
        return VersionMatchResult.unmatched(
            range, "Version " + version + " is higher than " + range.getExpression());
      }
    }
    return VersionMatchResult.matched(range, "Version " + version + " matches " + range.getExpression());
  }

  @Override
  public VersionMatchResult matches(String version, String expression) {
    try {
      return matches(version, parse(expression));
    } catch (IllegalArgumentException e) {
      return VersionMatchResult.invalid(e.getMessage());
    }
  }

  private VersionRange exact(String version) {
    if (isBlank(version)) {
      throw new IllegalArgumentException("Exact version is empty");
    }
    return new VersionRange(version, new VersionBoundary(version, true), new VersionBoundary(version, true), true);
  }

  private int compareVersions(String left, String right) {
    String[] leftParts = tokenize(left);
    String[] rightParts = tokenize(right);
    int max = Math.max(leftParts.length, rightParts.length);
    for (int i = 0; i < max; i++) {
      String l = i < leftParts.length ? leftParts[i] : "0";
      String r = i < rightParts.length ? rightParts[i] : "0";
      int compare = comparePart(l, r);
      if (compare != 0) {
        return compare;
      }
    }
    return 0;
  }

  private String[] tokenize(String version) {
    if (version == null) {
      return new String[0];
    }
    return version.trim().split("[\\.\\-_+]");
  }

  private int comparePart(String left, String right) {
    boolean leftNumber = isNumber(left);
    boolean rightNumber = isNumber(right);
    if (leftNumber && rightNumber) {
      Long l = Long.valueOf(left);
      Long r = Long.valueOf(right);
      return l.compareTo(r);
    }
    if (leftNumber) {
      return 1;
    }
    if (rightNumber) {
      return -1;
    }
    return left.compareTo(right);
  }

  private boolean isNumber(String value) {
    if (isBlank(value)) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
