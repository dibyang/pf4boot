package net.xdob.pf4boot.annotation;

import net.xdob.pf4boot.modal.SharingScope;

import java.lang.annotation.*;

/**
 * Export
 *
 * Exporting components or services for plug-ins
 *
 * @author yangzj
 * @version 1.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExportBeans {
  SharingScope scope() default SharingScope.PLATFORM;
  /**
   * 需要导出的共享bean
   */
  Class[] beans() default {};
  /**
   * 需要导出的共享bean名称
   */
  String[] beanNames() default {};

}
