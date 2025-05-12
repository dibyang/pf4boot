package net.xdob.pf4boot.annotation;

import net.xdob.pf4boot.modal.SharingScope;
import org.springframework.cglib.proxy.InvocationHandler;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;
import java.lang.reflect.Method;

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
  @AliasFor("name4Beans")
  Name4Bean[] value() default {};
  Class4Bean[] class4Beans() default {};
  @AliasFor("value")
  Name4Bean[] name4Beans() default {};

  @Retention(RetentionPolicy.RUNTIME)
  @interface Class4Bean{
    SharingScope scope() default SharingScope.PLATFORM;
    @AliasFor("types")
    Class[] value() default {};
    /**
     * 需要导出的共享bean
     */
    @AliasFor("value")
    Class[] types() default {};
  }
  @Retention(RetentionPolicy.RUNTIME)
  @interface Name4Bean{
    SharingScope scope() default SharingScope.PLATFORM;
    @AliasFor("names")
    String[] value() default {};
    /**
     * 需要导出的共享bean名称
     */
    @AliasFor("value")
    String[] names() default {};
  }
}
