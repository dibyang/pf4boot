package net.xdob.pf4boot.annotation;

import net.xdob.pf4boot.modal.SharingScope;
import org.springframework.cglib.proxy.InvocationHandler;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Service;

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
@Service
@Export
public @interface ShareService {

  @AliasFor(annotation = Service.class)
  String value() default "";

  @AliasFor(annotation = Export.class)
  SharingScope scope() default SharingScope.PLATFORM;

}
