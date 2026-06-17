package net.xdob.pf4boot.jpa.domain;

/**
 * 插件侧声明的 JPA domain 数据源定义。
 *
 * <p>该模型描述插件自有 domain 如何创建 DataSource，属于插件运行契约，不需要宿主在
 * application.yml 中替插件维护。</p>
 */
public final class JpaDataSourceDefinition {

  private final String url;
  private final String username;
  private final String password;
  private final String driverClassName;

  private JpaDataSourceDefinition(Builder builder) {
    this.url = trimToNull(builder.url);
    this.username = trimToNull(builder.username);
    this.password = builder.password;
    this.driverClassName = trimToNull(builder.driverClassName);
  }

  public static Builder builder(String url) {
    return new Builder(url);
  }

  public String getUrl() {
    return url;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getDriverClassName() {
    return driverClassName;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() == 0 ? null : trimmed;
  }

  /**
   * DataSource 定义构建器。
   */
  public static final class Builder {
    private String url;
    private String username;
    private String password;
    private String driverClassName;

    private Builder(String url) {
      this.url = url;
    }

    public Builder username(String username) {
      this.username = username;
      return this;
    }

    public Builder password(String password) {
      this.password = password;
      return this;
    }

    public Builder driverClassName(String driverClassName) {
      this.driverClassName = driverClassName;
      return this;
    }

    public JpaDataSourceDefinition build() {
      return new JpaDataSourceDefinition(this);
    }
  }
}
