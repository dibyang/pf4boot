package com.ls.pf4boot.annotation;

import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * PFEventListenerBean
 *
 * @author yangzj
 * @version 1.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EventListener
@Bean
public @interface EventListenerBean {
  @AliasFor(annotation = Bean.class)
  String[] value() default {};
}
