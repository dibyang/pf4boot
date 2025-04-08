package net.xdob.pf4boot.annotation;

import java.lang.annotation.*;

/**
 * 需要动态导入依赖bean
 */
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
