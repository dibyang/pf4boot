package net.xdob.pf4boot.annotation;

import net.xdob.pf4boot.modal.SharingScope;
import org.springframework.cglib.proxy.InvocationHandler;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * ShareService
 *
 * @author yangzj
 * @version 1.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@Export
public @interface ShareComponent {
  /**
   * The value may indicate a suggestion for a logical component name,
   * to be turned into a Spring bean in case of an autodetected component.
   * @return the suggested component name, if any (or empty String otherwise)
   */
  @AliasFor(annotation = Component.class)
  String value() default "";

  @AliasFor(annotation = Export.class)
  SharingScope scope() default SharingScope.PLATFORM;

}
