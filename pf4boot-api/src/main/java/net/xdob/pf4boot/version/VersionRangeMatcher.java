package net.xdob.pf4boot.version;

/**
 * 版本范围解析与匹配 SPI。
 *
 * <p>框架默认实现仅覆盖生产预检所需的常用 Maven 风格范围；宿主可在后续通过自定义
 * 实现扩展更复杂的版本语义。</p>
 */
public interface VersionRangeMatcher {

  /**
   * 解析版本范围表达式。
   *
   * @param expression 版本范围表达式
   * @return 解析后的版本范围
   */
  VersionRange parse(String expression);

  /**
   * 判断版本是否满足范围。
   *
   * @param version 待检查版本
   * @param range 已解析的版本范围
   * @return 匹配结果
   */
  VersionMatchResult matches(String version, VersionRange range);

  /**
   * 解析表达式并判断版本是否匹配。
   *
   * @param version 待检查版本
   * @param expression 版本范围表达式
   * @return 匹配结果
   */
  VersionMatchResult matches(String version, String expression);
}
