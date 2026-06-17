package net.xdob.pf4boot.jpa.binding;

/**
 * Consumer 插件用于声明 JPA 绑定关系的 SPI。
 */
public interface JpaConsumerBindingProvider {

  /**
   * 返回当前插件的 JPA 绑定定义。
   */
  JpaConsumerBinding jpaConsumerBinding();
}
