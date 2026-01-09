package net.xdob.pf4boot.annotation;

import net.xdob.pf4boot.modal.SharingScope;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoExports {

  // 批量配置：数组类型的嵌套注解
  AutoExport[] value() default {};

  /**
   * 单个自动导出配置，仅作为AutoExports的属性值使用，不单独标注
   */
  @Target({})  // 核心修正：空数组，表示该注解不可被直接标注
  @Retention(RetentionPolicy.RUNTIME)
  @Documented // 优化补充：规范元注解，和外部注解保持一致
  @interface AutoExport {
    // 作用域配置，默认平台级别
    SharingScope scope() default SharingScope.PLATFORM;
    // 分组配置，默认插件启动器的默认分组
    String group() default PluginStarter.DEFAULT;
    Class<?>[] types() default {};
  }
}
