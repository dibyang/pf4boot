package net.xdob.pf4boot.jpa.binding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Consumer 插件声明的 JPA 绑定定义。
 *
 * <p>插件显式引入 PluginJPAStarter 后，可通过该定义决定使用本地 JPA 还是绑定共享 domain。
 * 共享 domain 的选择属于 consumer 插件自身契约，不应由宿主全局配置代管。</p>
 */
public final class JpaConsumerBinding {

  private final JpaBindingMode mode;
  private final JpaConsumerDomainBinding primaryDomain;
  private final List<JpaConsumerDomainBinding> additionalDomains;

  private JpaConsumerBinding(Builder builder) {
    this.mode = builder.mode == null ? JpaBindingMode.LOCAL : builder.mode;
    this.primaryDomain = builder.primaryDomain;
    this.additionalDomains = Collections.unmodifiableList(
        builder.additionalDomains == null
            ? new ArrayList<>()
            : new ArrayList<>(builder.additionalDomains));
  }

  public static Builder local() {
    return new Builder(JpaBindingMode.LOCAL, null);
  }

  public static Builder shared(String domainId) {
    return new Builder(JpaBindingMode.SHARED, JpaConsumerDomainBinding.builder(domainId).build());
  }

  public JpaBindingMode getMode() {
    return mode;
  }

  public JpaConsumerDomainBinding getPrimaryDomain() {
    return primaryDomain;
  }

  public List<JpaConsumerDomainBinding> getAdditionalDomains() {
    return additionalDomains;
  }

  public boolean isShared() {
    return JpaBindingMode.SHARED == this.mode;
  }

  /**
   * Consumer 绑定构建器。
   */
  public static final class Builder {
    private final JpaBindingMode mode;
    private JpaConsumerDomainBinding primaryDomain;
    private List<JpaConsumerDomainBinding> additionalDomains = new ArrayList<>();

    private Builder(JpaBindingMode mode, JpaConsumerDomainBinding primaryDomain) {
      this.mode = mode;
      this.primaryDomain = primaryDomain;
    }

    public Builder primaryDomain(JpaConsumerDomainBinding primaryDomain) {
      this.primaryDomain = primaryDomain;
      return this;
    }

    public Builder additionalDomain(String domainId) {
      this.additionalDomains.add(JpaConsumerDomainBinding.builder(domainId).build());
      return this;
    }

    public Builder additionalDomain(JpaConsumerDomainBinding binding) {
      if (binding != null) {
        this.additionalDomains.add(binding);
      }
      return this;
    }

    public JpaConsumerBinding build() {
      return new JpaConsumerBinding(this);
    }
  }
}
