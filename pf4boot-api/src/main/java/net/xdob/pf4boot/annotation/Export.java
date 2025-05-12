package net.xdob.pf4boot.annotation;


import net.xdob.pf4boot.modal.SharingScope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cglib.proxy.InvocationHandler;

import java.lang.annotation.*;
import java.lang.reflect.Method;

/**
 *
 * 导出 components or services 到应用上下文
 *
 * @author yangzj
 * @version 1.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@Documented
public @interface Export {
  SharingScope scope() default SharingScope.PLATFORM;
}
