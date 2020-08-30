package com.ls.pf4boot.annotation;

import org.springframework.core.annotation.AliasFor;
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
@PFEventListener
@Service
public @interface PFEventListenerService {
  @AliasFor(annotation = Service.class)
  String value() default "";
}
