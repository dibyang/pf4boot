package net.xdob.pf4boot.jpa.domain;

/**
 * Provider 插件用于声明自身共享 JPA domain 的 SPI。
 *
 * <p>插件主类实现该接口时，domain starter 可以在 Bean 创建早期直接读取定义，避免宿主通过
 * 环境配置维护插件实体包、数据源和 DDL 策略。</p>
 */
public interface JpaDomainDefinitionProvider {

  /**
   * 返回当前插件提供的 JPA domain 定义。
   */
  JpaDomainDefinition jpaDomainDefinition();
}
