package net.xdob.pf4boot.annotation;

import java.lang.annotation.*;

/**
 * 需要动态导入依赖bean
 * 该特性已被废弃
 * @deprecated
 */
@Deprecated
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DynamicImportBeans {
  /**
   * 需要动态导入依赖bean
   */
  Class[] beans() default {};
  /**
   * 需要动态导入依赖bean名称
   */
  String[] beanNames() default {};
}
