package com.ls.pf4boot.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * EventListenerService
 *
 * @author yangzj
 * @version 1.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EventListener
@Component
public @interface EventListenerComponent {
  @AliasFor(annotation = Component.class)
  String value() default "";
}
